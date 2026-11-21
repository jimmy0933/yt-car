package com.jimmy.ytcar.car

import android.app.Presentation
import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.util.Log
import android.view.WindowManager
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import com.jimmy.ytcar.PlaybackController
import com.jimmy.ytcar.R

/**
 * Android Auto 給導航類 App 的 Surface → 建立 VirtualDisplay → 在上面顯示 Presentation（WebView 播放器）。
 */
class SurfaceRenderer(private val context: Context) : SurfaceCallback {
    private var virtualDisplay: VirtualDisplay? = null
    private var presentation: Presentation? = null
    private var player: CarPlayer? = null

    override fun onSurfaceAvailable(container: SurfaceContainer) {
        val surface = container.surface ?: return
        if (container.width <= 0 || container.height <= 0) return
        release()
        try {
            val dm = context.getSystemService(DisplayManager::class.java)
            val vd = dm.createVirtualDisplay(
                "YtCar",
                container.width,
                container.height,
                container.dpi,
                surface,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY,
            )
            virtualDisplay = vd
            val p = Presentation(context, vd.display, R.style.Theme_YtCar_Car)
            p.window?.addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
            val cp = CarPlayer(p.context)
            p.setContentView(cp.root)
            p.show()
            presentation = p
            player = cp
            PlaybackController.attach(cp)
        } catch (e: Exception) {
            Log.e("YtCar", "無法建立車機畫面", e)
            release()
        }
    }

    override fun onSurfaceDestroyed(container: SurfaceContainer) = release()

    fun release() {
        player?.let {
            PlaybackController.detach(it)
            it.destroy()
        }
        player = null
        runCatching { presentation?.dismiss() }
        presentation = null
        virtualDisplay?.release()
        virtualDisplay = null
    }
}
