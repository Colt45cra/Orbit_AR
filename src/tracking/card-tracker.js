import * as THREE from 'three'
import { cardPoints, homography, refineCardQuad } from './card-geometry.js'

export function poseFromCardQuad(corners, aspect, projection, width, height, worldScale) {
  const h=homography(cardPoints(aspect),corners)
  if(!h) return null
  const fx=projection.elements[0]*width/2, fy=projection.elements[5]*height/2
  const cx=width/2, cy=height/2
  const column=(a,b,c)=>new THREE.Vector3((a-cx*c)/fx,(b-cy*c)/fy,c)
  const r1=column(h[0],h[3],h[6]),r2=column(h[1],h[4],h[7]),t=column(h[2],h[5],1)
  let scale=2/(r1.length()+r2.length())
  if(t.z*scale<0) scale=-scale
  r1.multiplyScalar(scale).normalize()
  r2.multiplyScalar(scale).addScaledVector(r1,-r2.dot(r1)).normalize()
  const r3=new THREE.Vector3().crossVectors(r1,r2).normalize()
  t.multiplyScalar(scale)
  // Computer-vision camera (right, down, forward) -> Three (right, up, back).
  for(const v of [r1,r2,r3,t]){v.y=-v.y;v.z=-v.z;v.multiplyScalar(worldScale)}
  if(![...r1.toArray(),...r2.toArray(),...t.toArray()].every(Number.isFinite)) return null
  return new THREE.Matrix4().makeBasis(r1,r2,r3).setPosition(t)
}

export function createCardTracker({ mindar, anchor, content, aspect, onStatus }) {
  const source=document.createElement('canvas')
  const ctx=source.getContext('2d',{willReadFrequently:true})
  const projection=new THREE.Matrix4().fromArray(mindar.controller.getProjectionMatrix())
  const captureScale=Math.min(1,640/mindar.video.videoWidth,960/mindar.video.videoHeight)
  const width=Math.round(mindar.video.videoWidth*captureScale)
  const height=Math.round(width*mindar.video.videoHeight/mindar.video.videoWidth)
  source.width=width;source.height=height
  const overlay=document.createElement('canvas')
  overlay.width=width;overlay.height=height
  Object.assign(overlay.style,{position:'absolute',pointerEvents:'none',zIndex:'1'})
  mindar.container.appendChild(overlay)
  const draw=overlay.getContext('2d')
  const raw=new THREE.Matrix4()
  const position=new THREE.Vector3(),rotation=new THREE.Quaternion(),scale=new THREE.Vector3()
  let rawTime=0,lastVideoTime=-1,lastFrameTime=0,lastGoodTime=0,lastStatus=''
  let lastQuad=null
  const status=value=>{if(value!==lastStatus){lastStatus=value;onStatus?.(value)}}
  const local=cardPoints(aspect)
  function seed() {
    return local.map(p=>{
      const v=new THREE.Vector3(p.x,p.y,0).applyMatrix4(raw).applyMatrix4(projection)
      return {x:(v.x+1)*width/2,y:(1-v.y)*height/2}
    })
  }
  return {
    observe() {
      if(!anchor.visible) return
      raw.copy(anchor.group.matrix)
      rawTime=performance.now()
    },
    lost() {lastStatus='';rawTime=0;lastGoodTime=0;lastQuad=null;content.visible=false;draw.clearRect(0,0,width,height)},
    update() {
      const now=performance.now(), video=mindar.video
      if(!rawTime || now-rawTime>180){content.visible=false;draw.clearRect(0,0,width,height);status('Searching for card…');return}
      if(video.currentTime===lastVideoTime || now-lastFrameTime<40) {
        if(now-lastGoodTime>120)content.visible=false
        return
      }
      lastVideoTime=video.currentTime;lastFrameTime=now
      ctx.drawImage(video,0,0,width,height)
      const pixels=ctx.getImageData(0,0,width,height).data
      const gray=new Uint8Array(width*height)
      for(let i=0;i<gray.length;i++)gray[i]=(pixels[i*4]*77+pixels[i*4+1]*150+pixels[i*4+2]*29)>>8
      const initial=seed()
      // Recognition supplies identity and a fresh search region; measured edges
      // supply the four-corner pose. Never keep a stale screen-space lock.
      let result=refineCardQuad(gray,width,height,initial)
      if(!result && lastQuad && now-lastGoodTime<120)result=refineCardQuad(gray,width,height,lastQuad)
      draw.clearRect(0,0,width,height)
      if(!result){content.visible=false;status('Reacquiring card corners…');return}
      raw.decompose(position,rotation,scale)
      const pose=poseFromCardQuad(result.corners,aspect,projection,width,height,scale.x)
      if(!pose){content.visible=false;return}
      content.matrix.copy(pose)
      content.matrixWorldNeedsUpdate=true
      content.visible=true
      lastGoodTime=now;lastQuad=result.corners
      status(`Card tracked · ${result.supported.filter(Boolean).length}/4 corners`)
      for(const key of ['width','height','top','left'])overlay.style[key]=video.style[key]
      draw.strokeStyle='rgba(168,255,75,.6)';draw.lineWidth=1
      draw.beginPath();result.corners.forEach((p,i)=>i?draw.lineTo(p.x,p.y):draw.moveTo(p.x,p.y));draw.closePath();draw.stroke()
      result.corners.forEach((p,i)=>{draw.fillStyle=result.supported[i]?'#a8ff4b':'#ffce58';draw.beginPath();draw.arc(p.x,p.y,3,0,Math.PI*2);draw.fill()})
    },
    dispose(){overlay.remove();source.width=source.height=0},
  }
}
