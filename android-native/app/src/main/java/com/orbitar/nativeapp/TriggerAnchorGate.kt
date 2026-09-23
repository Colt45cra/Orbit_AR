package com.orbitar.nativeapp

import kotlin.math.*

/** Require a steady, fully observed trigger before creating or explicitly realigning an anchor. */
internal class TriggerAnchorGate {
    var needsAnchor = true
        private set
    private var origin: FloatArray? = null
    private var since = 0L
    fun requestLock() {needsAnchor=true;loseObservation()}
    fun locked() {needsAnchor=false;loseObservation()}
    fun loseObservation() {origin=null;since=0L}
    // xyz followed by quaternion xyzw. Copies values; never retains a native ARCore pose/frame.
    fun update(pose: FloatArray?, timestamp: Long): Boolean {
        if(!needsAnchor) return false
        if(pose==null || pose.size!=7 || pose.any {!it.isFinite()}) {loseObservation();return false}
        val old=origin
        val moved=old==null || sqrt((0..2).sumOf {(pose[it]-old[it]).toDouble().pow(2)})>.025
        val turned=old!=null && abs((3..6).sumOf {pose[it].toDouble()*old[it]})<cos(Math.toRadians(4.0)/2)
        if(moved || turned || timestamp<since) {origin=pose.copyOf();since=timestamp;return false}
        return timestamp-since>=500_000_000L
    }
}
