package com.github.ijkzen.scancode

interface ScanManager {

    fun initCamera()

    fun releaseCamera()

    fun isFlashAvailable():Boolean

    fun openFlash()

    fun closeFlash()

    fun isInitDone(): Boolean

    fun setContinue(continueScan: Boolean)

    fun setResultListener(listener: ScanResultListener)

    fun setCameraErrorListener(listener: CameraErrorListener)
}