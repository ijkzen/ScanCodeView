package com.github.ijkzen.scancode

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.AttributeSet
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import com.github.ijkzen.scancode.listener.CameraErrorListener
import com.github.ijkzen.scancode.listener.ScanResultListener
import com.github.ijkzen.scancode.util.isCameraAllowed
import com.github.ijkzen.scancode.util.rotate90ForNv21
import com.github.ijkzen.scancode.util.yuv888ToNv21
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.multi.GenericMultipleBarcodeReader
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

open class ScanCodeView : TextureView, ScanManager {

    companion object {
        const val TAG = "ScanCodeView"
        const val CAMERA_THREAD_TAG = "CameraThread"
        const val IMAGE_READER_THREAD_TAG = "ImageReaderThread"
    }

    private val mCameraThread = HandlerThread(CAMERA_THREAD_TAG).apply { start() }
    private val mCameraHandler = Handler(mCameraThread.looper)
    private val mImageThread = HandlerThread(IMAGE_READER_THREAD_TAG).apply { start() }
    private val mImageHandler = Handler(mImageThread.looper)


    private var mErrorListener: CameraErrorListener? = null
    private var mResultListener: ScanResultListener? = null

    private var mIsFlashAvailable = false
    private val mCameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var mCameraDevice: CameraDevice? = null
    private var mCharacteristics: CameraCharacteristics? = null
    private var mPreviewSize: Size? = null
    private var mCameraSession: CameraCaptureSession? = null
    private var mRequestBuilder: CaptureRequest.Builder? = null

    private var mImageReader: ImageReader? = null

    private val mCodeReader = GenericMultipleBarcodeReader(MultiFormatReader())

    @Volatile
    private var mContinue = true

    private var mRemeasured = false

    private val mImageListener = ImageReader.OnImageAvailableListener {
        val image = it.acquireLatestImage()
        if (!mContinue || mResultListener == null || image == null) {
            mImageHandler.removeCallbacksAndMessages(null)
            image?.close()
            return@OnImageAvailableListener
        }

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
        } finally {
            image.close()
        }
    }

    constructor(context: Context) : super(context) {
        initTextureView()
    }

    constructor(context: Context, attributeSet: AttributeSet) : super(context, attributeSet) {
        initTextureView()
    }

    private fun initTextureView() {
        if (surfaceTextureListener == null) {
            val listener = object : SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(
                    surface: SurfaceTexture,
                    width: Int,
                    height: Int
                ) {
                    post { initCamera() }
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
        } else {
            if (surfaceTexture != null) {
                post { initCamera() }
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun initCamera() = runBlocking {
        if (isInitDone()) {
            releaseCamera()
        }

        if (surfaceTexture == null) {
            return@runBlocking
        }

        if (!isCameraAllowed(context)) {
            mErrorListener?.noCameraPermission()
            return@runBlocking
        }

        try {
            val cameraPair = getBackCamera()

            if (cameraPair.first == "" && cameraPair.second == null) {
                mErrorListener?.noBackCamera()
                return@runBlocking
            }

            mCharacteristics = cameraPair.second
            checkFlash()
            initPreviewSize()
            if (!mRemeasured) {
                mRemeasured = true
                resetLayoutParam()
                post { initTextureView() }
                return@runBlocking
            }
            mCameraDevice = openCamera(cameraPair.first, mCameraHandler)
            mCameraSession = createCaptureSession(mCameraDevice!!, getTargetList(), mCameraHandler)
            initCaptureRequest()
            mCameraSession!!.setRepeatingRequest(mRequestBuilder!!.build(), null, mCameraHandler)

        } catch (e: Exception) {
            mErrorListener?.cameraAccessFail()
            e.printStackTrace()
        }
    }

    private fun getBackCamera(): Pair<String, CameraCharacteristics?> {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraList = cameraManager.cameraIdList
        if (cameraList.isEmpty()) {
            mErrorListener?.noCamera()
            return Pair("", null)
        }

        var targetCameraId = ""
        var targetCharacteristic: CameraCharacteristics? = null
        for (cameraId in cameraList) {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            if (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) {
                targetCameraId = cameraId
                targetCharacteristic = characteristics
                break
            }
        }

        return Pair(targetCameraId, targetCharacteristic)
    }

    private fun checkFlash() {
        mIsFlashAvailable =
            mCharacteristics?.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) as Boolean
        if (!mIsFlashAvailable) {
            mErrorListener?.noFlashAvailable()
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun openCamera(
        cameraId: String,
        handler: Handler
    ): CameraDevice = suspendCancellableCoroutine { cont ->
        mCameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                cont.resume(camera)
            }

            override fun onDisconnected(camera: CameraDevice) {
                Log.w(TAG, "Camera $cameraId has been disconnected")
            }

            override fun onError(camera: CameraDevice, error: Int) {
                val msg = when (error) {
                    ERROR_CAMERA_DEVICE -> "Fatal (device)"
                    ERROR_CAMERA_DISABLED -> "Device policy"
                    ERROR_CAMERA_IN_USE -> "Camera in use"
                    ERROR_CAMERA_SERVICE -> "Fatal (service)"
                    ERROR_MAX_CAMERAS_IN_USE -> "Maximum cameras in use"
                    else -> "Unknown"
                }
                val exc = RuntimeException("Camera $cameraId error: ($error) $msg")
                Log.e(TAG, exc.message, exc)
                if (cont.isActive) cont.resumeWithException(exc)
            }

        }, handler)
    }

    private fun initPreviewSize() {
        mPreviewSize = getBestSize()
        initImageReader()
    }

    open fun getBestSize(): Size {
        if (mCharacteristics != null) {
            val map =
                mCharacteristics!!.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
            val sizeList = map.getOutputSizes(SurfaceTexture::class.java)
            if (sizeList.isEmpty()) {
                throw CameraAccessException(
                    CameraAccessException.CAMERA_ERROR,
                    "there is no support size for SurfaceTexture"
                )
            } else {
                val viewSize = getViewSize()
                val viewRadio = viewSize.width / (viewSize.height + 0F)
                var radioDelta = -1000F
                var targetSize = Size(0, 0)

                for (size in sizeList) {
                    val currentRadio = size.width / (size.height + 0F)
                    val currentRadioDelta = currentRadio - viewRadio
                    if (radioDelta == -1000F) {
                        targetSize = size
                    } else {
                        if (currentRadioDelta <= radioDelta
                            && size.width * size.height <= targetSize.width * targetSize.height
                            && size.width >= viewSize.width * 0.8
                        ) {
                            targetSize = size
                        }
                    }
                    radioDelta = currentRadioDelta
                }

                Log.e(
                    TAG,
                    "preview width: ${targetSize.width} preview height: ${targetSize.height}"
                )
                return targetSize
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
            mPreviewSize!!.width, mPreviewSize!!.height,
            ImageFormat.YUV_420_888, 3
        )
        mImageReader!!.setOnImageAvailableListener(mImageListener, mImageHandler)
    }

    private fun resetLayoutParam() {
        val params = layoutParams ?: ViewGroup.LayoutParams(0, 0)
        val maxSize = getMaxPreviewRect()
        if (measuredWidth < measuredHeight) {
            params.height = measuredWidth * maxSize.width() / maxSize.height()
        } else {
            params.height = measuredWidth * maxSize.height() / maxSize.width()
        }

        layoutParams = params
    }

    private suspend fun createCaptureSession(
        device: CameraDevice,
        targetList: List<Surface>,
        handler: Handler
    ): CameraCaptureSession = suspendCoroutine {
        val callback = object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                it.resume(session)
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {
                val exc = RuntimeException("Camera ${device.id} session configuration failed")
                Log.e(TAG, exc.message, exc)
                it.resumeWithException(exc)
            }
        }
        device.createCaptureSession(targetList, callback, handler)
    }

    private fun initCaptureRequest() {
        mRequestBuilder = mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        val textureSurface = Surface(surfaceTexture)
        mRequestBuilder!!.addTarget(textureSurface)
        mRequestBuilder!!.addTarget(mImageReader!!.surface)
        mRequestBuilder!!.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
        mRequestBuilder!!.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
        mRequestBuilder!!.set(
            CaptureRequest.CONTROL_AF_MODE,
            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
        )

//        val activeRect =
//            mCharacteristics!!.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)!!
//        val centerPoint = Point(
//            (activeRect.left + activeRect.right) / 2,
//            (activeRect.top + activeRect.bottom) / 2
//        )
//        val viewSize = getViewSize()
//        val halfCropWidth = viewSize.width / 2
//        val halfCropHeight = viewSize.height / 2
//        val cropRect = Rect(
//            centerPoint.x - halfCropWidth,
//            centerPoint.y - halfCropHeight,
//            centerPoint.x + halfCropWidth,
//            centerPoint.y + halfCropHeight
//        )
//
//        mRequestBuilder!!.set(CaptureRequest.SCALER_CROP_REGION, cropRect)
    }

    private fun getTargetList() = arrayListOf(Surface(surfaceTexture), mImageReader!!.surface)

    override fun releaseCamera() {
        if (isInitDone()) {
            mCameraSession!!.stopRepeating()
            mCameraSession = null
            mCameraDevice!!.close()
            mCameraDevice = null
        }
    }

    override fun destroyCamera() {
        mCameraThread.quitSafely()
        mImageThread.quitSafely()
    }

    override fun isFlashAvailable(): Boolean {
        return mIsFlashAvailable
    }

    override fun openFlash() {
        if (isInitDone() && isFlashAvailable()) {
            mRequestBuilder!!.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
            mCameraSession!!.setRepeatingRequest(mRequestBuilder!!.build(), null, mCameraHandler)
        }
    }

    override fun closeFlash() {
        if (isInitDone() && isFlashAvailable()) {
            mRequestBuilder!!.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
            mCameraSession!!.setRepeatingRequest(mRequestBuilder!!.build(), null, mCameraHandler)
        }
    }

    override fun isInitDone(): Boolean {
        return mCameraDevice != null && mCharacteristics != null && mPreviewSize != null && mCameraSession != null
    }

    override fun setContinue(continueScan: Boolean) {
        mContinue = continueScan
    }

    override fun setResultListener(listener: ScanResultListener) {
        mResultListener = listener
    }

    override fun setCameraErrorListener(listener: CameraErrorListener) {
        mErrorListener = listener
    }

    private fun getViewSize(): Size {
        val finalWidth = Math.max(width, height)
        val finalHeight = Math.min(width, height)

        return Size(finalWidth, finalHeight)
    }

    private fun getMaxPreviewRect():Rect{
        return mCharacteristics!!.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)!!
    }
}