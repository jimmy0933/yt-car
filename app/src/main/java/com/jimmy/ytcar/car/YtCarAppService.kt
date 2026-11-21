package com.jimmy.ytcar.car

import android.content.Intent
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
        return PlayerScreen(carContext)
    }
}
