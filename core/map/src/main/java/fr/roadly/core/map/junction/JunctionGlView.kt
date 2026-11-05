package fr.roadly.core.map.junction

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Choreographer
import android.view.TextureView

/**
 * Vue OpenGL ES 3.0 de la vue de carrefour. TextureView (et non GLSurfaceView) : elle se compose avec Compose (coins arrondis,
 * fondu, glissement) et au-dessus de la carte MapLibre sans problème d'ordre de surfaces. Un thread de rendu dédié, cadencé par
 * le Choreographer, plafonné à [maxFps] (60, 30 en économie d'énergie) ; aucune image n'est rendue tant que la vue n'a pas de scène.
 */
class JunctionGlView(context: Context) : TextureView(context), TextureView.SurfaceTextureListener {
    @Volatile private var pendingScene: Pair<JunctionMeshes, JunctionPalette>? = null
    @Volatile private var pendingDynamics: JunctionDynamics? = null
    @Volatile var maxFps: Int = 60
    /** Appelé (thread GL) si OpenGL ES 3 est indisponible : l'écran retire alors la vue. */
    @Volatile var onUnsupported: (() -> Unit)? = null

    private var thread: RenderThread? = null

    init {
        surfaceTextureListener = this
        isOpaque = false
    }

    fun setScene(meshes: JunctionMeshes, palette: JunctionPalette) {
        pendingScene = meshes to palette
        thread?.wake()
    }

    fun setDynamics(d: JunctionDynamics) {
        pendingDynamics = d
        thread?.wake()
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        thread = RenderThread(surface, width, height).also { it.start() }
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        thread?.resize(width, height)
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        // La SurfaceTexture est libérée par le thread de rendu, après la destruction du contexte EGL qui l'utilise.
        val t = thread
        thread = null
        if (t == null) return true
        t.shutdown()
        return false
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

    private inner class RenderThread(private val texture: SurfaceTexture, @Volatile private var w: Int, @Volatile private var h: Int) :
        HandlerThread("RoadlyJunctionGL"), Choreographer.FrameCallback {
        private lateinit var handler: Handler
        private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
        private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
        private var surface: EGLSurface = EGL14.EGL_NO_SURFACE
        private val renderer = JunctionRenderer()
        private var ready = false
        private var hasScene = false
        private var sizeDirty = true
        private var lastFrameNs = 0L
        @Volatile private var running = true

        override fun onLooperPrepared() {
            handler = Handler(looper)
            if (!initEgl()) {
                Log.w(TAG, "OpenGL ES 3 indisponible : vue 3D de carrefour désactivée")
                onUnsupported?.invoke()
                return
            }
            try {
                renderer.init()
            } catch (e: Exception) {
                Log.w(TAG, "shaders", e)
                onUnsupported?.invoke()
                return
            }
            ready = true
            Choreographer.getInstance().postFrameCallback(this)
        }

        fun wake() { /* Rendu continu tant que la vue existe : rien à faire. */ }

        fun resize(nw: Int, nh: Int) { w = nw; h = nh; sizeDirty = true }

        override fun doFrame(frameTimeNanos: Long) {
            if (!running || !ready) return
            Choreographer.getInstance().postFrameCallback(this)
            // À l'arrêt, caméra posée : 20 i/s suffisent aux chevrons animés (batterie).
            val still = !renderer.animating && (pendingDynamics?.speedKmh ?: 0.0) < STILL_KMH
            val fps = if (still) minOf(maxFps, STILL_FPS) else maxFps
            val minInterval = 1_000_000_000L / fps.coerceIn(15, 120)
            if (lastFrameNs != 0L && frameTimeNanos - lastFrameNs < minInterval - 2_000_000L) return
            lastFrameNs = frameTimeNanos
            pendingScene?.let { (m, p) ->
                pendingScene = null
                renderer.setScene(m, p)
                hasScene = true
            }
            pendingDynamics?.let { renderer.setDynamics(it) }
            if (sizeDirty) { renderer.resize(w, h); sizeDirty = false }
            if (!hasScene) return
            try {
                renderer.draw()
                EGL14.eglSwapBuffers(display, surface)
            } catch (e: Exception) {
                Log.w(TAG, "rendu", e)
            }
        }

        fun shutdown() {
            running = false
            if (::handler.isInitialized) {
                handler.post {
                    Choreographer.getInstance().removeFrameCallback(this)
                    if (ready) runCatching { renderer.release() }
                    releaseEgl()
                    texture.release()
                    quitSafely()
                }
            } else {
                quitSafely()
            }
        }

        private fun initEgl(): Boolean {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (display == EGL14.EGL_NO_DISPLAY) return false
            val version = IntArray(2)
            if (!EGL14.eglInitialize(display, version, 0, version, 1)) return false
            val config = chooseConfig(samples = 4) ?: chooseConfig(samples = 0) ?: return false
            eglContext = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE), 0)
            if (eglContext == EGL14.EGL_NO_CONTEXT) return false
            surface = EGL14.eglCreateWindowSurface(display, config, texture, intArrayOf(EGL14.EGL_NONE), 0)
            if (surface == EGL14.EGL_NO_SURFACE) return false
            return EGL14.eglMakeCurrent(display, surface, surface, eglContext)
        }

        private fun chooseConfig(samples: Int): EGLConfig? {
            val attrs = mutableListOf(
                EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_DEPTH_SIZE, 24, EGL14.EGL_RENDERABLE_TYPE, EGLExt.EGL_OPENGL_ES3_BIT_KHR,
            )
            if (samples > 0) attrs += listOf(EGL14.EGL_SAMPLE_BUFFERS, 1, EGL14.EGL_SAMPLES, samples)
            attrs += EGL14.EGL_NONE
            val configs = arrayOfNulls<EGLConfig>(1)
            val n = IntArray(1)
            if (!EGL14.eglChooseConfig(display, attrs.toIntArray(), 0, configs, 0, 1, n, 0) || n[0] == 0) return null
            return configs[0]
        }

        private fun releaseEgl() {
            if (display == EGL14.EGL_NO_DISPLAY) return
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
            if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, eglContext)
            EGL14.eglTerminate(display)
            display = EGL14.EGL_NO_DISPLAY
        }
    }

    private companion object {
        const val TAG = "RoadlyJunction"
        const val STILL_KMH = 5.0
        const val STILL_FPS = 20
    }
}
