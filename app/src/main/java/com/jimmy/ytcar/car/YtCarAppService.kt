package com.jimmy.ytcar.car

import android.content.Intent
import com.jimmy.ytcar.PlaybackController
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

class YtCarAppService : CarAppService() {
    // 自用側載：允許所有 host（Android Auto 需開啟開發者模式的「未知來源」）
    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    override fun onCreateSession(): Session = YtSession()
}

class YtSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen {
        PlaybackController.log("車機連線，建立畫面")
        lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onDestroy(owner: androidx.lifecycle.LifecycleOwner) {
                PlaybackController.log("車機連線結束")
                PlaybackController.setCarSession(false)
            }
        })
        return PlayerScreen(carContext)
    }
}
