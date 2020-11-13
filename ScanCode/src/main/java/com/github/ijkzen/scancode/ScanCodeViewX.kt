package com.github.ijkzen.scancode

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.params.StreamConfigurationMap
import android.hardware.display.DisplayManager
import android.util.AttributeSet
import android.util.Log
import android.util.Size
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.camera.camera2.internal.Camera2CameraInfoImpl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.core.view.get
import androidx.lifecycle.LifecycleOwner
import com.github.ijkzen.scancode.listener.ScanResultListener
import com.github.ijkzen.scancode.util.*
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.multi.GenericMultipleBarcodeReader
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs

open class ScanCodeViewX : FrameLayout, ScanManager {

    companion object {
        private const val TAG = "ScanCodeViewX"
        private const val RATIO_4_3_VALUE = 4.0 / 3.0
        private const val RATIO_16_9_VALUE = 16.0 / 9.0
        private val DEFAULT_SIZE = Size(1280, 720)
    }

    @Volatile
    private var mContinue = true

    private lateinit var previewView: PreviewView
    private lateinit var focusView: FocusView

    private var displayId: Int = -1
    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var lifecycleOwner: LifecycleOwner? = null
    private var scanResultListener: ScanResultListener? = null
    private val codeReader by lazy {
        val formatReader = MultiFormatReader()
        val formats = arrayListOf<BarcodeFormat>(BarcodeFormat.CODE_128, BarcodeFormat.QR_CODE)
        val hints = HashMap<DecodeHintType, Any>()
        hints[DecodeHintType.POSSIBLE_FORMATS] = formats
        hints[DecodeHintType.TRY_HARDER] = true
        hints[DecodeHintType.CHARACTER_SET] = "utf-8"
        formatReader.setHints(hints)
        GenericMultipleBarcodeReader(formatReader)
    }
    private lateinit var mApplicationContext: Context

    private var showFocusCircle = false

    private var mTouchDownTime: Long = 0L

    private val displayManager by lazy {
        context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    }

    private var cameraExecutor: ExecutorService? = null

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) = run {
            if (displayId == this@ScanCodeViewX.displayId && display != null) {
                Log.d(TAG, "Rotation changed: ${this@ScanCodeViewX.display.rotation}")
                imageAnalyzer?.targetRotation = this@ScanCodeViewX.display.rotation
            }
        }
    }

    private val scanWorker = ImageAnalysis.Analyzer { proxy ->
        if (!mContinue || scanResultListener == null) {
            proxy.close()
            return@Analyzer
        }

        Log.e("test", "image width: ${proxy.width} image height: ${proxy.height}")
        val nv21 = yuv888ToNv21(proxy)
        val data = rotate90ForNv21(nv21, proxy.width, proxy.height)

        val source = PlanarYUVLuminanceSource(
            data,
            proxy.height,
            proxy.width,
            0,
            0,
            proxy.height,
            proxy.width,
            false
        )

        val bitmap = BinaryBitmap(HybridBinarizer(source))
        try {
            val resultList = codeReader.decodeMultiple(bitmap)
            if (resultList != null && resultList.isNotEmpty()) {
                mContinue = false
                post { scanResultListener?.onScanResult(resultList.map { it.text }) }
            } else {
                mContinue = true
            }
        } catch (e: Exception) {
        }
        proxy.close()
    }

    constructor(context: Context) : super(context) {
        initView()
    }

    constructor(context: Context, attributeSet: AttributeSet) : super(context, attributeSet) {
        initView()
    }

    constructor(context: Context, attributeSet: AttributeSet, defStyleAttr: Int)
            : super(context, attributeSet, defStyleAttr) {
        initView()
    }

    constructor(
        context: Context,
        attributeSet: AttributeSet,
        defStyleAttr: Int,
        defStyleRes: Int
    ) : super(context, attributeSet, defStyleAttr, defStyleRes) {
        initView()
    }

    private fun initView() {
        LayoutInflater.from(context).inflate(R.layout.layout_scan_code_view, this, true)
        previewView = findViewById(R.id.preview)
        focusView = findViewById(R.id.focus)
    }

    @SuppressLint("UnsafeExperimentalUsageError")
    private fun setupCamera() {
        if (lifecycleOwner == null) {
            throw RuntimeException(" Missing lifecycleOwner ")
        }
        displayManager.unregisterDisplayListener(displayListener)
        if (cameraExecutor == null) {
            cameraExecutor = Executors.newSingleThreadExecutor()
        }
        displayManager.registerDisplayListener(displayListener, null)
        previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        post {
            displayId = display.displayId
            val providerFuture = ProcessCameraProvider.getInstance(mApplicationContext)
            providerFuture.addListener({
                cameraProvider = providerFuture.get()
                if (!hasBackCamera()) {
                    throw RuntimeException("Missing Back Camera")
                }

                val aspectRatio = aspectRatio(width, height)
                val rotation = display.rotation
                val cameraProvider =
                    cameraProvider ?: throw IllegalStateException("Camera initialization failed")
                val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

                try {
                    cameraProvider.unbindAll()

                    preview = Preview.Builder()
                        .setTargetAspectRatio(aspectRatio)
                        .setTargetRotation(rotation)
                        .build()

                    camera =
                        cameraProvider.bindToLifecycle(lifecycleOwner!!, cameraSelector, preview)

                    preview?.setSurfaceProvider(previewView.surfaceProvider)

                    imageAnalyzer = ImageAnalysis.Builder()
                        .setTargetResolution(getBestAnalyzeSize())
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setTargetRotation(rotation)
                        .build()

                    imageAnalyzer!!.setAnalyzer(cameraExecutor!!, scanWorker)

                    camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner!!,
                        cameraSelector,
                        imageAnalyzer
                    )

                } catch (e: Exception) {
                    e.printStackTrace()
                }

            }, ContextCompat.getMainExecutor(context))
        }
    }

    private fun hasBackCamera(): Boolean {
        return cameraProvider?.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ?: false
    }

    fun setLifecycle(owner: LifecycleOwner, context: Context) {
        lifecycleOwner = owner
        mApplicationContext = context
    }

    private fun aspectRatio(width: Int, height: Int): Int {
        val previewRatio = Math.max(width, height).toDouble() / Math.min(width, height)
        if (abs(previewRatio - RATIO_4_3_VALUE) <= abs(previewRatio - RATIO_16_9_VALUE)) {
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }

    override fun initCamera() {
        if (isCameraAllowed(context)) {
            setupCamera()
        }
    }

    override fun releaseCamera() {
        displayManager.unregisterDisplayListener(displayListener)
        cameraExecutor?.shutdown()
        nv21 = null
        rotatedNv21 = null
    }

    override fun openFlash() {
        camera?.cameraControl?.enableTorch(true)
    }

    override fun closeFlash() {
        camera?.cameraControl?.enableTorch(false)
    }

    override fun setContinue(continueScan: Boolean) {
        mContinue = continueScan
    }

    override fun setResultListener(listener: ScanResultListener) {
        scanResultListener = listener
    }

    override fun setShowFocusCircle(show: Boolean) {
        showFocusCircle = show
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        return when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                mTouchDownTime = System.currentTimeMillis()
                true
            }
            MotionEvent.ACTION_UP -> {
                if (System.currentTimeMillis() - mTouchDownTime > ViewConfiguration.getTapTimeout()) {
                    return true
                }

                val factory =
                    SurfaceOrientedMeteringPointFactory(width.toFloat(), height.toFloat())
                val autoFocusPoint =
                    factory.createPoint(event.x, event.y)

                if (showFocusCircle) {
                    val constraintSet = ConstraintSet()
                    constraintSet.clone(getChildConstraintLayout())
                    constraintSet.connect(
                        R.id.focus,
                        ConstraintSet.TOP,
                        ConstraintSet.PARENT_ID,
                        ConstraintSet.TOP,
                        event.y.toInt() - focusView.height / 2
                    )

                    constraintSet.connect(
                        R.id.focus,
                        ConstraintSet.START,
                        ConstraintSet.PARENT_ID,
                        ConstraintSet.START,
                        event.x.toInt() - focusView.width / 2
                    )

                    constraintSet.applyTo(getChildConstraintLayout())
                    focusView.startAnimation()
                }

                try {
                    camera?.cameraControl?.startFocusAndMetering(
                        FocusMeteringAction.Builder(autoFocusPoint, FocusMeteringAction.FLAG_AF)
                            .apply { disableAutoCancel() }
                            .build())
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                return true
            }
            else -> {
                false
            }
        }
    }

    private fun getChildConstraintLayout() = get(0) as ConstraintLayout

    private fun getBestAnalyzeSize(): Size {
        if (camera != null) {
            val cameraManager =
                mApplicationContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager

            val info = getCameraInfo()

            if (info != null) {
                val clz = Camera2CameraInfoImpl::class.java
                val cameraIdField = clz.getDeclaredField("mCameraId")
                cameraIdField.isAccessible = true
                val cameraId = cameraIdField.get(info)

                val character =
                    cameraManager.getCameraCharacteristics(cameraId as String)

                val map: StreamConfigurationMap =
                    character[CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP] as StreamConfigurationMap
                val sizeList = map.getOutputSizes(SurfaceTexture::class.java)

                for (size in sizeList) {

                    Log.e("test", size.toString())
                    if ((size.width == 1920 || size.width == 1080) &&
                        (size.height == 1920 || size.height == 1080)) {

                        return Size(1920, 1080)
                    }
                }

                return sizeList.last()

            } else {
                return DEFAULT_SIZE
            }
        } else {
            return DEFAULT_SIZE
        }
    }

    private fun getCameraInfo(): Camera2CameraInfoImpl? {
        return if (camera?.cameraInfo is Camera2CameraInfoImpl) {
            camera?.cameraInfo as Camera2CameraInfoImpl
        } else {
            null
        }
    }
}