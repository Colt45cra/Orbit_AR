package com.orbitar.nativeapp.room

import kotlin.math.*

internal fun depthAgrees(measured: Float, expected: Float): Boolean =
    measured.isFinite() && expected.isFinite() && measured in 0.2f..5f && expected in 0.2f..5f &&
        abs(measured-expected)<=maxOf(0.045f,expected*0.02f)

// Only the connected depth-supported patch under the aim belongs to this selection.
internal fun connectedPatch(matches: BooleanArray, width: Int, seed: Int): BooleanArray {
    val out=BooleanArray(matches.size)
    if(width<=0 || seed !in matches.indices || !matches[seed]) return out
    val queue=IntArray(matches.size); var head=0;var tail=0
    queue[tail++]=seed;out[seed]=true
    while(head<tail) {
        val i=queue[head++];val x=i%width
        val neighbors=intArrayOf(if(x>0) i-1 else -1,if(x<width-1) i+1 else -1,i-width,i+width)
        for(n in neighbors) if(n in matches.indices && matches[n] && !out[n]) {out[n]=true;queue[tail++]=n}
    }
    return out
}
internal data class DepthEdge(val a: Point2,val b: Point2,val supported: Boolean)
internal data class DepthPatch(val cells: List<Point2> = emptyList(),val edges: List<DepthEdge> = emptyList(),val columns: Int=40,val rows: Int=60)
internal fun patchGeometry(mask: BooleanArray, measured: BooleanArray, width: Int): DepthPatch {
    val height=mask.size/width;val cells=ArrayList<Point2>();val edges=ArrayList<DepthEdge>()
    for(i in mask.indices) if(mask[i]) {
        val x=i%width;val y=i/width;val a=Point2(x.toFloat()/width,y.toFloat()/height)
        val b=Point2((x+1f)/width,(y+1f)/height);cells.add(a)
        fun edge(n: Int,p:Point2,q:Point2) {if(n !in mask.indices || !mask[n]) edges.add(DepthEdge(p,q,n in measured.indices && measured[n]))}
        edge(if(x>0) i-1 else -1,a,Point2(a.x,b.z))
        edge(if(x<width-1) i+1 else -1,Point2(b.x,a.z),b)
        edge(i-width,a,Point2(b.x,a.z));edge(i+width,Point2(a.x,b.z),b)
    }
    return DepthPatch(cells,edges,width,height)
}
