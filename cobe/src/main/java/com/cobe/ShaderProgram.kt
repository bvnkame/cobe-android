package com.cobe

import android.opengl.GLES30
import android.util.Log

internal object ShaderProgram {

    fun create(vertSrc: String, fragSrc: String): Int {
        val v = compile(GLES30.GL_VERTEX_SHADER, vertSrc)
        val f = compile(GLES30.GL_FRAGMENT_SHADER, fragSrc)
        if (v == 0 || f == 0) return 0

        val prog = GLES30.glCreateProgram()
        GLES30.glAttachShader(prog, v)
        GLES30.glAttachShader(prog, f)
        GLES30.glLinkProgram(prog)

        val status = IntArray(1)
        GLES30.glGetProgramiv(prog, GLES30.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            Log.e("Cobe", "Link error: ${GLES30.glGetProgramInfoLog(prog)}")
            GLES30.glDeleteProgram(prog)
            return 0
        }
        GLES30.glDeleteShader(v)
        GLES30.glDeleteShader(f)
        return prog
    }

    private fun compile(type: Int, src: String): Int {
        val s = GLES30.glCreateShader(type)
        GLES30.glShaderSource(s, src)
        GLES30.glCompileShader(s)
        val status = IntArray(1)
        GLES30.glGetShaderiv(s, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            Log.e("Cobe", "Shader compile error: ${GLES30.glGetShaderInfoLog(s)}\nSource:\n$src")
            GLES30.glDeleteShader(s)
            return 0
        }
        return s
    }
}
