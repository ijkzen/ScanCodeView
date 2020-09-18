package com.github.ijkzen.scancode

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.AttributeSet
import android.util.Size
import android.view.Surface
import android.view.TextureView
import com.github.ijkzen.scancode.util.isCameraAllowed
import com.github.ijkzen.scancode.util.rotate90ForNv21
import com.github.ijkzen.scancode.util.yuv888ToNv21
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.multi.GenericMultipleBarcodeReader
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.lang.Exception

open class ScanCodeView : TextureView, ScanManager {

    private val mCameraThread = HandlerThread("")
    private var mCameraHandler: Handler? = null

    private var mErrorListener: CameraErrorListener? = null
    private var mResultListener: ScanResultListener? = null

    private var mIsFlashAvailable = false
    private var mCameraDevice: CameraDevice? = null
    private var mCharacteristics: CameraCharacteristics? = null
    private var mSize: Size? = null
    private var mCameraSession: CameraCaptureSession? = null
    private var mRequestBuilder: CaptureRequest.Builder? = null

    private var mImageReader: ImageReader? = null

    private val mCodeReader = GenericMultipleBarcodeReader(MultiFormatReader())

    @Volatile
    private var mContinue = true

    private val mDeviceStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            mCameraDevice = camera
            initPreview()
        }

        override fun onDisconnected(camera: CameraDevice) {}

        override fun onError(camera: CameraDevice, error: Int) {
            mErrorListener?.cameraAccessFail()
        }

    }

    private val mImageListener = ImageReader.OnImageAvailableListener {
        if (!mContinue || mResultListener == null) {
            return@OnImageAvailableListener
        }

        val image = it.acquireLatestImage()
        val nv21 = yuv888ToNv21(image)
        val finalData = rotate90ForNv21(nv21, image.width, image.height)
        val source = PlanarYUVLuminanceSource(
            finalData,
            image.height,
            image.width,
            0,
            0,
            image.height,
            image.width,
            false
        )
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        try {
            val resultList = mCodeReader.decodeMultiple(bitmap)
            if (resultList != null && resultList.isNotEmpty()) {
                mContinue = false
                val list = resultList.map { result -> result.text }
                post {
                    mResultListener?.onScanResult(list)
                }
            } else {
                mContinue = true
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private val mSessionCallback = object:CameraCaptureSession.StateCallback(){
        override fun onConfigured(session: CameraCaptureSession) {
            mCameraSession = session
            try {
                mRequestBuilder?.let {
                    mCameraSession!!.setRepeatingRequest(it.build(), null, mCameraHandler)
                }
            } catch (e: Exception) {
                mErrorListener?.captureFail()
                e.printStackTrace()
            }
        }

        override fun onConfigureFailed(session: CameraCaptureSession) {
            mErrorListener?.captureFail()
        }

    }

    constructor(context: Context) : super(context) {
        startCameraThread()
        initTextureView()
    }

    constructor(context: Context, attributeSet: AttributeSet) : super(context, attributeSet) {
        startCameraThread()
        initTextureView()
    }

    private fun startCameraThread() {
        mCameraThread.start()
        mCameraHandler = Handler(mCameraThread.looper)
    }

    private fun initTextureView() {
        if (surfaceTextureListener == null) {
            val listener = object : SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(
                    surface: SurfaceTexture,
                    width: Int,
                    height: Int
                ) {
                    initCamera()
                }

                override fun onSurfaceTextureSizeChanged(
                    surface: SurfaceTexture,
                    width: Int,
                    height: Int
                ) {
                }

                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                    releaseCamera()
                    return true
                }

                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}

            }

            surfaceTextureListener = listener
        }
    }


    @SuppressLint("MissingPermission")
    override fun initCamera() {
        if (isInitDone()) {
            releaseCamera()
        }

        if (!isCameraAllowed(context)) {
            mErrorListener?.noCameraPermission()
            return
        }

        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraList = cameraManager.cameraIdList
            if (cameraList.isEmpty()) {
                mErrorListener?.noCamera()
                return
            }

            var targetCameraId = ""
            for (cameraId in cameraList) {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                if (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) {
                    targetCameraId = cameraId
                    mCharacteristics = characteristics
                    break
                }
            }

            if ("" == targetCameraId && mCharacteristics == null) {
                mErrorListener?.noBackCamera()
                return
            }

            mIsFlashAvailable =
                mCharacteristics?.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) as Boolean
            if (!mIsFlashAvailable) {
                mErrorListener?.noFlashAvailable()
            }

            cameraManager.openCamera(targetCameraId, mDeviceStateCallback, mCameraHandler)

        } catch (e: Exception) {
            mErrorListener?.cameraAccessFail()
            e.printStackTrace()
        }
    }

    private fun initPreview() {
        mSize = getBestSize()
        initImageReader()
        initCameraSession()
    }

    open fun getBestSize(): Size {
        if (mCameraDevice != null && mCharacteristics != null) {
            val map =
                mCharacteristics!!.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
            val sizeList = map.getOutputSizes(SurfaceTexture::class.java)
            if (sizeList.isEmpty()) {
                throw CameraAccessException(
                    CameraAccessException.CAMERA_ERROR,
                    "there is no support size for SurfaceTexture"
                )
            } else {
                val maxWidth = Math.max(width, height)

                for (size in sizeList) {
                    if (size.width >= maxWidth * 0.8 && size.width <= maxWidth * 1.2) {
                        return size
                    }
                }

                val p1080 = Size(1920, 1080)
                val p768 = Size(1366, 768)
                val p720 = Size(1280, 720)
                val p480 = Size(704, 480)
                return when {
                    sizeList.contains(p1080) -> {
                        p1080
                    }
                    sizeList.contains(p768) -> {
                        p768
                    }
                    sizeList.contains(p720) -> {
                        p720
                    }
                    else -> {
                        p480
                    }
                }
            }

        } else {
            throw CameraAccessException(
                CameraAccessException.CAMERA_ERROR,
                "current camera has not been initialized"
            )
        }
    }

    private fun initImageReader() {
        mImageReader = ImageReader.newInstance(
            mSize!!.width, mSize!!.height,
            ImageFormat.YUV_420_888, 3
        )
        mImageReader!!.setOnImageAvailableListener(mImageListener, mCameraHandler)
    }

    private fun initCameraSession() {
        if (!isInitDone()) {
            return
        }

        try {

        } catch (e: Exception) {
            mErrorListener?.captureFail()
        }
    }

    private fun initCaptureRequest() {
        mRequestBuilder = mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        val textureSurface = Surface(surfaceTexture)
        mRequestBuilder!!.addTarget(textureSurface)
        mRequestBuilder!!.addTarget(mImageReader!!.surface)
        mRequestBuilder!!.set(
            CaptureRequest.CONTROL_AF_MODE,
            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
        )
        GlobalScope.launch {  }
        runBlocking {  }
    }

    override fun releaseCamera() {
        TODO("Not yet implemented")
    }

    override fun isFlashAvailable(): Boolean {
        return mIsFlashAvailable
    }

    override fun openFlash() {
        TODO("Not yet implemented")
    }

    override fun closeFlash() {
        TODO("Not yet implemented")
    }

    override fun isInitDone(): Boolean {
        TODO("Not yet implemented")
    }

    override fun setContinue(continueScan: Boolean) {
        mContinue = continueScan
    }

    override fun setResultListener(listener: ScanResultListener) {
        TODO("Not yet implemented")
    }

    override fun setCameraErrorListener(listener: CameraErrorListener) {
        mErrorListener = listener
    }


}