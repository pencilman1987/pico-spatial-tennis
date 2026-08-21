package com.example.spatialtennis.spatial.scene

/**
 * All athlete visuals are authored toward local +Z. In scene space, an athlete on the
 * positive-Z half must rotate 180 degrees to look at the net; an athlete on the negative-Z
 * half keeps the authored orientation. This also remains correct if court sides are swapped.
 */
internal fun yawToFaceNet(sceneZ: Float): Float = if (sceneZ >= 0f) 180f else 0f
