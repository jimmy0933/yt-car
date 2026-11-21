package com.jimmy.ytcar.car

import android.content.Intent
import android.util.Log
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.hardware.CarHardwareManager
import androidx.car.app.hardware.common.CarValue
import androidx.car.app.hardware.common.OnCarDataAvailableListener
import androidx.car.app.hardware.info.Speed
import androidx.car.app.validation.HostValidator
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.jimmy.ytcar.PlaybackController

class YtCarAppService : CarAppService() {
    // 自用側載：允許所有 host（Android Auto 需開啟開發者模式的「未知來源」）
    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    override fun onCreateSession(): Session = YtSession()
}

class YtSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen {
        startSpeedListener()
        return PlayerScreen(carContext)
    }

    private fun startSpeedListener() {
        try {
            val info = carContext.getCarService(CarHardwareManager::class.java).carInfo
            val listener = OnCarDataAvailableListener<Speed> { speed ->
                PlaybackController.onSpeed(
                    speed.displaySpeedMetersPerSecond.valueOrNull()
                        ?: speed.rawSpeedMetersPerSecond.valueOrNull()
                )
            }
            info.addSpeedListener(ContextCompat.getMainExecutor(carContext), listener)
            lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    runCatching { info.removeSpeedListener(listener) }
                }
            })
        } catch (e: Exception) {
            Log.w("YtCar", "車速無法取得", e)
            PlaybackController.onSpeed(null)
        }
    }

    private fun CarValue<Float>.valueOrNull(): Float? =
        if (status == CarValue.STATUS_SUCCESS) value else null
}
