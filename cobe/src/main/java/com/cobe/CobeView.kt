package com.cobe

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet

class CobeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : GLSurfaceView(context, attrs) {

    val options = CobeOptions()
    val renderer = CobeRenderer(context, options)

    init {
        setEGLContextClientVersion(3)
        holder.setFormat(android.graphics.PixelFormat.TRANSLUCENT)
        setEGLConfigChooser(8, 8, 8, 8, 0, 0)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    fun update(state: CobeState) {
        state.phi?.let { options.phi = it }
        state.theta?.let { options.theta = it }
        state.perspective?.let { options.perspective = it }
        state.mapSamples?.let { options.mapSamples = it }
        state.mapBrightness?.let { options.mapBrightness = it }
        state.mapBaseBrightness?.let { options.mapBaseBrightness = it }
        state.baseColor?.let { options.baseColor = it }
        state.markerColor?.let { options.markerColor = it }
        state.glowColor?.let { options.glowColor = it }
        state.arcColor?.let { options.arcColor = it }
        state.arcWidth?.let { options.arcWidth = it; renderer.markArcsDirty() }
        state.arcHeight?.let { options.arcHeight = it; renderer.markArcsDirty() }
        state.diffuse?.let { options.diffuse = it }
        state.clearColor?.let { options.clearColor = it }
        state.dark?.let { options.dark = it }
        state.opacity?.let { options.opacity = it }
        state.offset?.let { options.offset = it }
        state.scale?.let { options.scale = it }
        state.markerElevation?.let { options.markerElevation = it; renderer.markArcsDirty() }
        state.markers?.let { options.markers = it; renderer.markMarkersDirty() }
        state.arcs?.let { options.arcs = it; renderer.markArcsDirty() }
        requestRender()
    }

    fun setOnProject(cb: (Marker, CobeProjection) -> Unit) {
        renderer.onProject = cb
    }
    fun setOnProjectArc(cb: (Arc, CobeProjection) -> Unit) {
        renderer.onProjectArc = cb
    }
}
