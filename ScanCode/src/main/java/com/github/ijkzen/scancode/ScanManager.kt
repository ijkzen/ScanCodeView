package com.github.ijkzen.scancode

import com.github.ijkzen.scancode.listener.ScanResultListener

interface ScanManager {

    fun initCamera()

    fun releaseCamera()

    fun openFlash()

    fun closeFlash()

    fun setContinue(continueScan: Boolean)

    fun setResultListener(listener: ScanResultListener)

    fun setShowFocusCircle(show: Boolean)
}