package com.cobe

/**
 * Convenience facade matching the JS createGlobe(canvas, options) idiom.
 */
object Cobe {
    fun configure(view: CobeView, init: CobeOptions.() -> Unit): CobeView {
        view.options.init()
        view.renderer.markMarkersDirty()
        view.renderer.markArcsDirty()
        view.requestRender()
        return view
    }
}
