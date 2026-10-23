// Card-space points map to camera pixels through a planar homography.
export function homography(from, to) {
  const a = []
  for (let i = 0; i < 4; i++) {
    const { x, y } = from[i], u = to[i].x, v = to[i].y
    a.push([x, y, 1, 0, 0, 0, -u*x, -u*y, u])
    a.push([0, 0, 0, x, y, 1, -v*x, -v*y, v])
  }
  for (let c = 0; c < 8; c++) {
    let pivot = c
    for (let r = c+1; r < 8; r++) if (Math.abs(a[r][c]) > Math.abs(a[pivot][c])) pivot = r
    if (Math.abs(a[pivot][c]) < 1e-9) return null
    ;[a[c], a[pivot]] = [a[pivot], a[c]]
    const div = a[c][c]
    for (let k = c; k < 9; k++) a[c][k] /= div
    for (let r = 0; r < 8; r++) if (r !== c) {
      const factor = a[r][c]
      for (let k = c; k < 9; k++) a[r][k] -= factor*a[c][k]
    }
  }
  return [...a.map(row => row[8]), 1]
}

export function project(h, point) {
  const w = h[6]*point.x + h[7]*point.y + 1
  return { x: (h[0]*point.x+h[1]*point.y+h[2])/w, y: (h[3]*point.x+h[4]*point.y+h[5])/w }
}

export function cardPoints(aspect) {
  return [{x:-.5,y:aspect/2},{x:.5,y:aspect/2},{x:.5,y:-aspect/2},{x:-.5,y:-aspect/2}]
}

function intersection(a, b) {
  const det = a.nx*b.ny - a.ny*b.nx
  if (Math.abs(det) < .15) return null
  return { x:(a.c*b.ny-a.ny*b.c)/det, y:(a.nx*b.c-a.c*b.nx)/det }
}

// Robust parallel-edge search around a recognized target. Scores long edge
// support, not isolated text strokes. No rectangle is accepted without a seed.
export function refineCardQuad(gray, width, height, seed) {
  const pixel = (x, y) => gray[Math.round(y)*width+Math.round(x)]
  const edges = []
  for (let edge = 0; edge < 4; edge++) {
    const a = seed[edge], b = seed[(edge+1)%4]
    const dx = b.x-a.x, dy = b.y-a.y, len = Math.hypot(dx,dy)
    if (len < 35) return null
    const nx = -dy/len, ny = dx/len
    const radius = Math.max(5, Math.min(18, len*.045))
    const samples = []
    for (let j = 0; j < 25; j++) {
      const t = .04 + .92*j/24
      const x = a.x+dx*t, y = a.y+dy*t
      let best = null
      for (let offset = -radius; offset <= radius; offset++) {
        const px=x+nx*offset, py=y+ny*offset
        if (px < 4 || py < 4 || px >= width-4 || py >= height-4) continue
        // Average tangentially to suppress texture and sensor noise.
        let inside=0, outside=0
        for (let k=-1; k<=1; k++) {
          inside += pixel(px+nx*2+dx/len*k,py+ny*2+dy/len*k)
          outside += pixel(px-nx*2+dx/len*k,py-ny*2+dy/len*k)
        }
        const contrast = Math.abs(inside-outside)/3
        const score = contrast - Math.abs(offset)*1.4
        if (!best || score > best.score) best = {x:px,y:py,t,contrast,score,offset}
      }
      if (best?.contrast >= 18) samples.push(best)
    }
    // RANSAC line: steep perspective is supported, random text responses rejected.
    let inliers=[]
    for (let j=0; j<samples.length; j++) for (let k=j+1; k<samples.length; k++) {
      const p=samples[j], q=samples[k]
      if (Math.abs(p.t-q.t)<.35) continue
      const vx=q.x-p.x, vy=q.y-p.y, distance=Math.hypot(vx,vy)
      const normalX=-vy/distance, normalY=vx/distance
      if (Math.abs(normalX*nx+normalY*ny)<.96) continue
      const c=normalX*p.x+normalY*p.y
      const match=samples.filter(s=>Math.abs(normalX*s.x+normalY*s.y-c)<2)
      if (match.length>inliers.length) inliers=match
    }
    if (inliers.length<12) { edges.push(null); continue }
    const mx=inliers.reduce((v,p)=>v+p.x,0)/inliers.length
    const my=inliers.reduce((v,p)=>v+p.y,0)/inliers.length
    let xx=0,yy=0,xy=0
    for(const p of inliers){ xx+=(p.x-mx)**2; yy+=(p.y-my)**2; xy+=(p.x-mx)*(p.y-my) }
    const angle=.5*Math.atan2(2*xy,xx-yy)
    const normalX=-Math.sin(angle), normalY=Math.cos(angle)
    edges.push({nx:normalX,ny:normalY,c:normalX*mx+normalY*my,
      start:inliers.filter(p=>p.t<.26).length>=3,
      end:inliers.filter(p=>p.t>.74).length>=3,
      support:inliers.length/25})
  }
  // Three supported corners require all four edges, while one corner itself
  // can be covered. Its position is inferred from the adjacent fitted lines.
  if (edges.some(edge=>!edge)) return null
  const corners = edges.map((edge,i)=>intersection(edges[(i+3)%4],edge))
  if (corners.some(p=>!p || !Number.isFinite(p.x+p.y))) return null
  const supported=edges.map((edge,i)=>edge.start && edges[(i+3)%4].end)
  if (supported.filter(Boolean).length<3) return null
  for(let i=0;i<4;i++) {
    if(Math.hypot(corners[i].x-seed[i].x,corners[i].y-seed[i].y)>32) return null
  }
  // Preserve winding and reject collapsed/implausible rectangles.
  const cross=points=>points.map((p,i)=>{
    const q=points[(i+1)%4],r=points[(i+2)%4]
    return (q.x-p.x)*(r.y-q.y)-(q.y-p.y)*(r.x-q.x)
  })
  const seedCross=cross(seed), refinedCross=cross(corners)
  if(refinedCross.some((v,i)=>v*seedCross[i]<=0 || Math.abs(v)<400)) return null
  return {corners,supported,confidence:edges.reduce((v,e)=>v+e.support,0)/4}
}
