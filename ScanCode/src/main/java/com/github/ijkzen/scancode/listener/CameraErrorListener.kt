package com.github.ijkzen.scancode.listener

interface CameraErrorListener {

    fun noCameraPermission()

    fun noCamera()

    fun noBackCamera()

    fun noFlashAvailable()

    fun captureFail()

    fun cameraAccessFail()

}