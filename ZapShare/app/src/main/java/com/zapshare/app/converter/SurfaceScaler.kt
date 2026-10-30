package com.zapshare.app.converter

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLExt
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.SystemClock
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Draws each decoded frame onto the encoder surface at the target size.
 * Connecting the decoder straight to a smaller encoder surface leaves green lines on this phone.
 */
internal class SurfaceScaler(
    encoderSurface: Surface,
    srcWidth: Int,
    srcHeight: Int,
    private val outWidth: Int,
    private val outHeight: Int
) {
    val inputSurface: Surface

    private val gate = Object()
    private var pending = false
    private val matrix = FloatArray(16)
    private val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
    private val context: android.opengl.EGLContext
    private val eglSurface: android.opengl.EGLSurface
    private val texture: SurfaceTexture
    private val program: Int
    private val texId: Int
    private val coords: FloatBuffer
    private val uvs: FloatBuffer
    private var lastNs = 0L
    private var released = false

    init {
        if (display == EGL14.EGL_NO_DISPLAY) error("No display")
        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) error("GL init failed")
        val config = chooseConfig(display)
        val attrs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, attrs, 0)
        if (context == null || context == EGL14.EGL_NO_CONTEXT) error("No GL context")
        val surfaceAttrs = intArrayOf(EGL14.EGL_NONE)
        eglSurface = EGL14.eglCreateWindowSurface(display, config, encoderSurface, surfaceAttrs, 0)
        if (eglSurface == null || eglSurface == EGL14.EGL_NO_SURFACE) error("No encoder surface")
        if (!EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context)) error("GL context failed")
        texId = newExternalTexture()
        texture = SurfaceTexture(texId)
        texture.setDefaultBufferSize(srcWidth.coerceAtLeast(2), srcHeight.coerceAtLeast(2))
        texture.setOnFrameAvailableListener {
            synchronized(gate) {
                pending = true
                gate.notifyAll()
            }
        }
        inputSurface = Surface(texture)
        program = linkProgram(VERTEX, FRAGMENT)
        coords = floatBuffer(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
        uvs = floatBuffer(floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f))
        GLES20.glUseProgram(program)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "sTex"), 0)
    }

    /** Waits for the frame the decoder just rendered, then scales it onto the encoder. */
    fun draw(ptsUs: Long): Boolean {
        synchronized(gate) {
            val deadline = SystemClock.elapsedRealtime() + 800
            while (!pending && SystemClock.elapsedRealtime() < deadline) gate.wait(40)
            if (!pending) return false
            pending = false
        }
        texture.updateTexImage()
        texture.getTransformMatrix(matrix)
        GLES20.glViewport(0, 0, outWidth, outHeight)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId)
        val pos = GLES20.glGetAttribLocation(program, "aPosition")
        val tex = GLES20.glGetAttribLocation(program, "aTexCoord")
        coords.position(0)
        uvs.position(0)
        GLES20.glEnableVertexAttribArray(pos)
        GLES20.glVertexAttribPointer(pos, 2, GLES20.GL_FLOAT, false, 0, coords)
        GLES20.glEnableVertexAttribArray(tex)
        GLES20.glVertexAttribPointer(tex, 2, GLES20.GL_FLOAT, false, 0, uvs)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "uTexMatrix"), 1, false, matrix, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        var ns = ptsUs.coerceAtLeast(0) * 1000L
        if (ns <= lastNs) ns = lastNs + 1_000L
        lastNs = ns
        EGLExt.eglPresentationTimeANDROID(display, eglSurface, ns)
        return EGL14.eglSwapBuffers(display, eglSurface)
    }

    fun release() {
        if (released) return
        released = true
        runCatching { inputSurface.release() }
        runCatching { texture.release() }
        runCatching {
            GLES20.glDeleteProgram(program)
            GLES20.glDeleteTextures(1, intArrayOf(texId), 0)
        }
        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        EGL14.eglDestroySurface(display, eglSurface)
        EGL14.eglDestroyContext(display, context)
        EGL14.eglTerminate(display)
    }

    private fun newExternalTexture(): Int {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, ids[0])
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        return ids[0]
    }

    private fun chooseConfig(display: android.opengl.EGLDisplay): EGLConfig {
        val attrs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            0x3142, 1,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        if (!EGL14.eglChooseConfig(display, attrs, 0, configs, 0, 1, count, 0) || count[0] == 0 || configs[0] == null) {
            error("No GL config")
        }
        return configs[0]!!
    }

    private fun linkProgram(vertex: String, fragment: String): Int {
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, compile(GLES20.GL_VERTEX_SHADER, vertex))
        GLES20.glAttachShader(program, compile(GLES20.GL_FRAGMENT_SHADER, fragment))
        GLES20.glLinkProgram(program)
        val ok = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) error(GLES20.glGetProgramInfoLog(program))
        return program
    }

    private fun compile(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val ok = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) error(GLES20.glGetShaderInfoLog(shader))
        return shader
    }

    private fun floatBuffer(values: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(values.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(values)
            position(0)
        }

    private companion object {
        val VERTEX = """
            attribute vec4 aPosition;
            attribute vec4 aTexCoord;
            uniform mat4 uTexMatrix;
            varying vec2 vTex;
            void main() {
                gl_Position = aPosition;
                vTex = (uTexMatrix * aTexCoord).xy;
            }
        """.trimIndent()
        val FRAGMENT = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTex;
            uniform samplerExternalOES sTex;
            void main() {
                gl_FragColor = texture2D(sTex, vTex);
            }
        """.trimIndent()
    }
}
