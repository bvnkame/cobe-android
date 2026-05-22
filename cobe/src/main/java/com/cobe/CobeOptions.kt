package com.cobe

data class Marker(
    val location: DoubleArray,          // [lat, lon] degrees
    val size: Float,
    val color: FloatArray? = null,      // r,g,b 0..1
    val id: String? = null,
)

data class Arc(
    val from: DoubleArray,              // [lat, lon] degrees
    val to: DoubleArray,
    val color: FloatArray? = null,
    val id: String? = null,
)

data class CobeProjection(
    val x: Float,         // normalized 0..1 across viewport
    val y: Float,
    val alpha: Float,     // 0..1 visibility with horizon fade
    val depth: Float,     // rz: 1.0 = front center, < 0 = behind
)

class CobeOptions(
    var phi: Float = 0f,
    var theta: Float = 0f,
    var perspective: Float = 0f,
    var mapSamples: Float = 10_000f,
    var mapBrightness: Float = 1f,
    var mapBaseBrightness: Float = 0f,
    var baseColor: FloatArray = floatArrayOf(1f, 1f, 1f),
    var markerColor: FloatArray = floatArrayOf(1f, 0.5f, 0f),
    var glowColor: FloatArray = floatArrayOf(1f, 1f, 1f),
    var arcColor: FloatArray = floatArrayOf(0.3f, 0.6f, 1f),
    var arcWidth: Float = 1f,
    var arcHeight: Float = 0.2f,
    var diffuse: Float = 1f,
    var clearColor: FloatArray = floatArrayOf(1f, 1f, 1f, 1f),
    var dark: Float = 0f,
    var opacity: Float = 1f,
    var offset: FloatArray = floatArrayOf(0f, 0f),
    var scale: Float = 1f,
    var markerElevation: Float = 0.05f,
    var markers: List<Marker> = emptyList(),
    var arcs: List<Arc> = emptyList(),
)

class CobeState(
    var phi: Float? = null,
    var theta: Float? = null,
    var perspective: Float? = null,
    var mapSamples: Float? = null,
    var mapBrightness: Float? = null,
    var mapBaseBrightness: Float? = null,
    var baseColor: FloatArray? = null,
    var markerColor: FloatArray? = null,
    var glowColor: FloatArray? = null,
    var arcColor: FloatArray? = null,
    var arcWidth: Float? = null,
    var arcHeight: Float? = null,
    var diffuse: Float? = null,
    var clearColor: FloatArray? = null,
    var dark: Float? = null,
    var opacity: Float? = null,
    var offset: FloatArray? = null,
    var scale: Float? = null,
    var markerElevation: Float? = null,
    var markers: List<Marker>? = null,
    var arcs: List<Arc>? = null,
)
