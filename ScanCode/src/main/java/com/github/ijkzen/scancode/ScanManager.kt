package com.github.ijkzen.scancode

import com.github.ijkzen.scancode.listener.CameraErrorListener
import com.github.ijkzen.scancode.listener.ScanResultListener

interface ScanManager {

    fun initCamera()

    fun releaseCamera()

    fun destroyCamera()

    fun isFlashAvailable():Boolean

    fun openFlash()

    fun closeFlash()

    fun isInitDone(): Boolean

    fun setContinue(continueScan: Boolean)

    fun setResultListener(listener: ScanResultListener)

    fun setCameraErrorListener(listener: CameraErrorListener)
}