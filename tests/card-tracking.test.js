import { test } from 'node:test'
import assert from 'node:assert/strict'
import * as THREE from 'three'
import { cardPoints, homography, project, refineCardQuad } from '../src/tracking/card-geometry.js'
import { poseFromCardQuad } from '../src/tracking/card-tracker.js'

const quad=[{x:100,y:80},{x:380,y:100},{x:360,y:300},{x:80,y:280}]
function image(occlusion) {
  const gray=new Uint8Array(480*360).fill(30)
  const h=homography(quad,cardPoints(1))
  for(let y=0;y<360;y++)for(let x=0;x<480;x++){
    const p=project(h,{x,y})
    if(Math.abs(p.x)<.5 && Math.abs(p.y)<.5)gray[y*480+x]=220
    if(occlusion?.(x,y))gray[y*480+x]=30
  }
  return gray
}
test('projective mapping recovers all four corners under perspective',()=>{
  const local=cardPoints(.6),h=homography(local,quad)
  local.forEach((p,i)=>assert.ok(Math.hypot(project(h,p).x-quad[i].x,project(h,p).y-quad[i].y)<1e-6))
})
test('four measured edges refine a slightly inaccurate recognition seed',()=>{
  const seed=quad.map(p=>({x:p.x+3,y:p.y-2}))
  const result=refineCardQuad(image(),480,360,seed)
  assert.ok(result)
  assert.equal(result.supported.filter(Boolean).length,4)
  result.corners.forEach((p,i)=>assert.ok(Math.hypot(p.x-quad[i].x,p.y-quad[i].y)<3))
})
test('one covered corner can be inferred from the remaining edge support',()=>{
  const result=refineCardQuad(image((x,y)=>x>328 && y>260),480,360,quad)
  assert.ok(result)
  assert.equal(result.supported.filter(Boolean).length,3)
})
test('two covered corners are rejected instead of displaying a stale overlay',()=>{
  const result=refineCardQuad(image((x,y)=>(x>328 && y>260)||(x<130 && y>240)),480,360,quad)
  assert.equal(result,null)
})
test('a textureless frame cannot generate a card lock',()=>{
  assert.equal(refineCardQuad(new Uint8Array(480*360).fill(128),480,360,quad),null)
})
test('pose from measured corners preserves perspective and native world scale',()=>{
  const camera=new THREE.PerspectiveCamera(45,640/480,10,100000)
  const projection=camera.projectionMatrix
  const original=new THREE.Matrix4().compose(new THREE.Vector3(35,-20,-1100),new THREE.Quaternion().setFromEuler(new THREE.Euler(.25,-.4,.15)),new THREE.Vector3(400,400,400))
  const local=cardPoints(.62)
  const corners=local.map(p=>{const v=new THREE.Vector3(p.x,p.y,0).applyMatrix4(original).applyMatrix4(projection);return{x:(v.x+1)*320,y:(1-v.y)*240}})
  const recovered=poseFromCardQuad(corners,.62,projection,640,480,400)
  assert.ok(recovered)
  local.forEach((p,i)=>{const v=new THREE.Vector3(p.x,p.y,0).applyMatrix4(recovered).applyMatrix4(projection);assert.ok(Math.hypot((v.x+1)*320-corners[i].x,(1-v.y)*240-corners[i].y)<.01)})
})
