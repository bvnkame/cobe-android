package com.cobe

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class CobeRenderer internal constructor(
    private val context: Context,
    val options: CobeOptions,
) : GLSurfaceView.Renderer {

    private var width = 1
    private var height = 1
    private val dpr: Float = context.resources.displayMetrics.density

    private var globeProgram = 0
    private var markerProgram = 0
    private var arcProgram = 0

    private val quadBuf = IntArray(1)
    private val arcSegBuf = IntArray(1)
    private val markerInstBuf = IntArray(1)
    private val arcInstBuf = IntArray(1)
    private val tex = IntArray(1)

    private var pendingMarkerData: FloatArray? = null
    private var pendingArcData: FloatArray? = null
    private var markerCount = 0
    private var arcCount = 0

    var onProject: ((Marker, CobeProjection) -> Unit)? = null
    var onProjectArc: ((Arc, CobeProjection) -> Unit)? = null

    private val arcSegmentCount = 66      // (32+1)*2

    @Volatile private var markersDirty = true
    @Volatile private var arcsDirty = true

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        globeProgram = ShaderProgram.create(Shaders.GLOBE_VERT, Shaders.GLOBE_FRAG)
        markerProgram = ShaderProgram.create(Shaders.MARKER_VERT, Shaders.MARKER_FRAG)
        arcProgram = ShaderProgram.create(Shaders.ARC_VERT, Shaders.ARC_FRAG)

        GLES30.glGenBuffers(1, quadBuf, 0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, quadBuf[0])
        val quad = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, -1f, 1f, 1f, -1f, 1f, 1f)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, quad.size * 4, fb(quad), GLES30.GL_STATIC_DRAW)

        GLES30.glGenBuffers(1, arcSegBuf, 0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, arcSegBuf[0])
        val seg = FloatArray((33) * 4)
        for (i in 0..32) {
            val t = i / 32f
            seg[i * 4 + 0] = t; seg[i * 4 + 1] = -1f
            seg[i * 4 + 2] = t; seg[i * 4 + 3] = 1f
        }
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, seg.size * 4, fb(seg), GLES30.GL_STATIC_DRAW)

        GLES30.glGenBuffers(1, markerInstBuf, 0)
        GLES30.glGenBuffers(1, arcInstBuf, 0)

        // Texture (try load from assets, else 1×1 black)
        GLES30.glGenTextures(1, tex, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex[0])
        try {
            val ins = context.assets.open("texture.png")
            val bmp = BitmapFactory.decodeStream(ins)
            ins.close()
            GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0)
            GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
            bmp.recycle()
        } catch (_: Throwable) {
            val px = ByteBuffer.allocateDirect(3).put(byteArrayOf(0, 0, 0)).apply { position(0) }
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGB, 1, 1, 0, GLES30.GL_RGB, GLES30.GL_UNSIGNED_BYTE, px)
        }
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_REPEAT)

        markersDirty = true
        arcsDirty = true
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        width = w; height = h
        GLES30.glViewport(0, 0, w, h)
    }

    override fun onDrawFrame(gl: GL10?) {
        if (markersDirty) {
            uploadMarkerBuffer()
            markersDirty = false
        }
        if (arcsDirty) {
            uploadArcBuffer()
            arcsDirty = false
        }

        val c = options.clearColor
        GLES30.glClearColor(c[0], c[1], c[2], c[3])
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)

        drawGlobe()
        if (arcProgram != 0 && arcCount > 0) drawArcs()
        if (markerProgram != 0 && markerCount > 0) drawMarkers()

        if (onProject != null || onProjectArc != null) {
            options.markers.forEach { m ->
                onProject?.invoke(m, project(m.location))
            }
            options.arcs.forEach { a ->
                onProjectArc?.invoke(a, projectArcMid(a))
            }
        }
    }

    fun markMarkersDirty() { markersDirty = true }
    fun markArcsDirty() { arcsDirty = true }

    private fun drawGlobe() {
        if (globeProgram == 0) return
        GLES30.glUseProgram(globeProgram)
        val aPos = GLES30.glGetAttribLocation(globeProgram, "aPosition")
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, quadBuf[0])
        GLES30.glEnableVertexAttribArray(aPos)
        GLES30.glVertexAttribPointer(aPos, 2, GLES30.GL_FLOAT, false, 0, 0)
        GLES30.glVertexAttribDivisor(aPos, 0)

        uni2f("uResolution", width.toFloat(), height.toFloat(), globeProgram)
        uni2f("uRotation", options.phi, options.theta, globeProgram)
        uni1f("uDots", options.mapSamples, globeProgram)
        uni1f("uScale", options.scale, globeProgram)
        uni2f("uOffset", options.offset[0] * dpr, options.offset[1] * dpr, globeProgram)
        uni3fv("uBaseColor", options.baseColor, globeProgram)
        uni3fv("uGlowColor", options.glowColor, globeProgram)
        uni4f("uRenderParams", options.mapBrightness, options.diffuse, options.dark, options.opacity, globeProgram)
        uni1f("uMapBaseBrightness", options.mapBaseBrightness, globeProgram)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex[0])
        uni1i("uTexture", 0, globeProgram)

        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 6)
    }

    private fun drawArcs() {
        GLES30.glUseProgram(arcProgram)
        val aPos = GLES30.glGetAttribLocation(arcProgram, "aPosition")
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, arcSegBuf[0])
        GLES30.glEnableVertexAttribArray(aPos)
        GLES30.glVertexAttribPointer(aPos, 2, GLES30.GL_FLOAT, false, 0, 0)
        GLES30.glVertexAttribDivisor(aPos, 0)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, arcInstBuf[0])
        val stride = 12 * 4
        setInstanced(arcProgram, "aArcFrom", 3, stride, 0)
        setInstanced(arcProgram, "aArcTo", 3, stride, 12)
        setInstanced(arcProgram, "aArcHeight", 1, stride, 24)
        setInstanced(arcProgram, "aArcWidth", 1, stride, 28)
        setInstanced(arcProgram, "aArcColor", 3, stride, 32)
        setInstanced(arcProgram, "aHasColor", 1, stride, 44)

        uni1f("uPhi", options.phi, arcProgram)
        uni1f("uTheta", options.theta, arcProgram)
        uni2f("uResolution", width.toFloat(), height.toFloat(), arcProgram)
        uni1f("uScale", options.scale, arcProgram)
        uni2f("uOffset", options.offset[0] * dpr, options.offset[1] * dpr, arcProgram)
        uni3fv("uArcColor", options.arcColor, arcProgram)
        uni1f("uMarkerElevation", options.markerElevation, arcProgram)

        GLES30.glDrawArraysInstanced(GLES30.GL_TRIANGLE_STRIP, 0, arcSegmentCount, arcCount)
    }

    private fun drawMarkers() {
        GLES30.glUseProgram(markerProgram)
        val aPos = GLES30.glGetAttribLocation(markerProgram, "aPosition")
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, quadBuf[0])
        GLES30.glEnableVertexAttribArray(aPos)
        GLES30.glVertexAttribPointer(aPos, 2, GLES30.GL_FLOAT, false, 0, 0)
        GLES30.glVertexAttribDivisor(aPos, 0)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, markerInstBuf[0])
        val stride = 8 * 4
        setInstanced(markerProgram, "aMarkerPos", 3, stride, 0)
        setInstanced(markerProgram, "aMarkerSize", 1, stride, 12)
        setInstanced(markerProgram, "aMarkerColor", 3, stride, 16)
        setInstanced(markerProgram, "aHasColor", 1, stride, 28)

        uni1f("uPhi", options.phi, markerProgram)
        uni1f("uTheta", options.theta, markerProgram)
        uni2f("uResolution", width.toFloat(), height.toFloat(), markerProgram)
        uni1f("uScale", options.scale, markerProgram)
        uni2f("uOffset", options.offset[0] * dpr, options.offset[1] * dpr, markerProgram)
        uni3fv("uMarkerColor", options.markerColor, markerProgram)
        uni1f("uMarkerElevation", options.markerElevation, markerProgram)
        uni1f("uPerspective", options.perspective, markerProgram)

        GLES30.glDrawArraysInstanced(GLES30.GL_TRIANGLES, 0, 6, markerCount)
    }

    private fun setInstanced(prog: Int, name: String, size: Int, stride: Int, offset: Int) {
        val loc = GLES30.glGetAttribLocation(prog, name)
        if (loc < 0) return
        GLES30.glEnableVertexAttribArray(loc)
        GLES30.glVertexAttribPointer(loc, size, GLES30.GL_FLOAT, false, stride, offset)
        GLES30.glVertexAttribDivisor(loc, 1)
    }

    private fun uploadMarkerBuffer() {
        markerCount = options.markers.size
        if (markerCount == 0) return
        val data = FloatArray(markerCount * 8)
        options.markers.forEachIndexed { i, m ->
            val p = latLonTo3D(m.location)
            data[i * 8 + 0] = p[0]
            data[i * 8 + 1] = p[1]
            data[i * 8 + 2] = p[2]
            data[i * 8 + 3] = m.size
            val c = m.color
            if (c != null) {
                data[i * 8 + 4] = c[0]; data[i * 8 + 5] = c[1]; data[i * 8 + 6] = c[2]
                data[i * 8 + 7] = 1f
            } else {
                data[i * 8 + 7] = 0f
            }
        }
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, markerInstBuf[0])
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, data.size * 4, fb(data), GLES30.GL_DYNAMIC_DRAW)
    }

    private fun uploadArcBuffer() {
        arcCount = options.arcs.size
        if (arcCount == 0) return
        val data = FloatArray(arcCount * 12)
        options.arcs.forEachIndexed { i, a ->
            val from = latLonTo3D(a.from)
            val to = latLonTo3D(a.to)
            val base = i * 12
            data[base + 0] = from[0]; data[base + 1] = from[1]; data[base + 2] = from[2]
            data[base + 3] = to[0];   data[base + 4] = to[1];   data[base + 5] = to[2]
            data[base + 6] = options.arcHeight + options.markerElevation
            data[base + 7] = options.arcWidth * 0.005f
            val c = a.color
            if (c != null) {
                data[base + 8] = c[0]; data[base + 9] = c[1]; data[base + 10] = c[2]
                data[base + 11] = 1f
            } else {
                data[base + 11] = 0f
            }
        }
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, arcInstBuf[0])
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, data.size * 4, fb(data), GLES30.GL_DYNAMIC_DRAW)
    }

    private fun uni1f(name: String, v: Float, prog: Int) {
        GLES30.glUniform1f(GLES30.glGetUniformLocation(prog, name), v)
    }
    private fun uni2f(name: String, a: Float, b: Float, prog: Int) {
        GLES30.glUniform2f(GLES30.glGetUniformLocation(prog, name), a, b)
    }
    private fun uni4f(name: String, a: Float, b: Float, c: Float, d: Float, prog: Int) {
        GLES30.glUniform4f(GLES30.glGetUniformLocation(prog, name), a, b, c, d)
    }
    private fun uni3fv(name: String, v: FloatArray, prog: Int) {
        GLES30.glUniform3fv(GLES30.glGetUniformLocation(prog, name), 1, v, 0)
    }
    private fun uni1i(name: String, v: Int, prog: Int) {
        GLES30.glUniform1i(GLES30.glGetUniformLocation(prog, name), v)
    }

    private fun fb(arr: FloatArray): FloatBuffer {
        val bb = ByteBuffer.allocateDirect(arr.size * 4).order(ByteOrder.nativeOrder())
        val fb = bb.asFloatBuffer()
        fb.put(arr); fb.position(0)
        return fb
    }

    // --- projection helpers (mirror shader rotation for anchors) ---
    private fun latLonTo3D(loc: DoubleArray): FloatArray {
        val lat = (loc[0] * Math.PI / 180.0)
        val lon = (loc[1] * Math.PI / 180.0 - Math.PI)
        val cosLat = cos(lat)
        return floatArrayOf((-cosLat * cos(lon)).toFloat(), sin(lat).toFloat(), (cosLat * sin(lon)).toFloat())
    }

    private fun applyRotation(p: FloatArray): CobeProjection {
        val cx = cos(options.theta.toDouble()); val cy = cos(options.phi.toDouble())
        val sx = sin(options.theta.toDouble()); val sy = sin(options.phi.toDouble())
        val aspect = width.toDouble() / height.toDouble()
        val rx = cy * p[0] + sy * p[2]
        val ry = sy * sx * p[0] + cx * p[1] - cy * sx * p[2]
        val rz = -sy * cx * p[0] + sx * p[1] + cy * cx * p[2]
        val x = ((rx / aspect) * options.scale + options.offset[0] * options.scale * dpr / width + 1.0) / 2.0
        val y = (-ry * options.scale + options.offset[1] * options.scale * dpr / height + 1.0) / 2.0
        val radial = rx * rx + ry * ry
        val inSil = radial < 0.64
        val alpha: Float = if (rz < 0 && inSil) {
            0f
        } else {
            val t = ((rz + 0.15) / 0.30).coerceIn(0.0, 1.0).toFloat()
            t * t * (3 - 2 * t)
        }
        return CobeProjection(x.toFloat(), y.toFloat(), alpha, rz.toFloat())
    }

    private fun project(loc: DoubleArray): CobeProjection {
        val pos = latLonTo3D(loc)
        val r = 0.8f + options.markerElevation
        return applyRotation(floatArrayOf(pos[0] * r, pos[1] * r, pos[2] * r))
    }

    private fun projectArcMid(a: Arc): CobeProjection {
        val from = latLonTo3D(a.from); val to = latLonTo3D(a.to)
        val mx = from[0] + to[0]; val my = from[1] + to[1]; val mz = from[2] + to[2]
        val len = sqrt((mx * mx + my * my + mz * mz).toDouble()).toFloat()
        if (len < 0.001f) return CobeProjection(0f, 0f, 0f, -1f)
        val r = 0.8f + options.markerElevation
        val s = 0.25f * r + (0.5f * (0.8f + options.arcHeight + options.markerElevation)) / len
        return applyRotation(floatArrayOf(mx * s, my * s, mz * s))
    }
}
