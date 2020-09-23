package tech.ijkzen.scancodeview

import android.app.Application
import androidx.camera.camera2.Camera2Config
import androidx.camera.core.CameraXConfig

class MyApplication: Application(),   CameraXConfig.Provider {
    companion object {
        private lateinit var instance: MyApplication
        fun instance() = instance
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun getCameraXConfig(): CameraXConfig {
        return Camera2Config.defaultConfig()
    }
}