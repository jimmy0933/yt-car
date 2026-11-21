package com.jimmy.ytcar.car

import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.jimmy.ytcar.PlaybackController

/** 車機上只有全螢幕影片畫布，加上一顆播放/暫停鍵（NavigationTemplate 規定至少要有一個 action）。 */
class PlayerScreen(carContext: CarContext) : Screen(carContext) {
    private val renderer = SurfaceRenderer(carContext)

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                carContext.getCarService(AppManager::class.java).setSurfaceCallback(renderer)
            }

            override fun onDestroy(owner: LifecycleOwner) {
                carContext.getCarService(AppManager::class.java).setSurfaceCallback(null)
                renderer.release()
            }
        })
    }

    override fun onGetTemplate(): Template {
        val toggle = Action.Builder()
            .setTitle("⏯")
            .setOnClickListener { PlaybackController.togglePlay() }
            .build()
        return NavigationTemplate.Builder()
            .setActionStrip(ActionStrip.Builder().addAction(toggle).build())
            .build()
    }
}
