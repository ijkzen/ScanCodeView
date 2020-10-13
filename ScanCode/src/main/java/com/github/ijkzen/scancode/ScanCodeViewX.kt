package com.github.ijkzen.scancode

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.display.DisplayManager
import android.util.AttributeSet
import android.util.Log
import android.util.Size
import android.view.LayoutInflater
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.core.view.get
import androidx.lifecycle.LifecycleOwner
import com.github.ijkzen.scancode.listener.ScanResultListener
import com.github.ijkzen.scancode.util.isCameraAllowed
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs

open class ScanCodeViewX : FrameLayout, ScanManager {

    companion object {
        private const val TAG = "ScanCodeViewX"
        private const val RATIO_4_3_VALUE = 4.0 / 3.0
        private const val RATIO_16_9_VALUE = 16.0 / 9.0
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
    private lateinit var mApplicationContext: Context

    private var showFocusCircle = false

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

    @SuppressLint("UnsafeExperimentalUsageError")
    private val scanWorker = ImageAnalysis.Analyzer { proxy ->
        if (!mContinue || scanResultListener == null || proxy.image == null) {
            proxy.close()
            return@Analyzer
        }
        val image = InputImage.fromMediaImage(proxy.image!!, proxy.imageInfo.rotationDegrees)
        val scanner = BarcodeScanning.getClient()
        scanner.process(image)
            .addOnSuccessListener { codes ->
                if (codes.isNotEmpty()) {
                    post {
                        scanResultListener?.onScanResult(
                            codes.map { it.rawValue ?: "" }
                        )
                    }
                }
            }.addOnFailureListener { e ->
                e.printStackTrace()
            }.addOnCompleteListener {
                proxy.close()
            }
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
                preview = Preview.Builder()
                    .setTargetAspectRatio(aspectRatio)
                    .setTargetRotation(rotation)
                    .build()

                imageAnalyzer = ImageAnalysis.Builder()
                    .setTargetResolution(Size(1280, 720))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setTargetRotation(rotation)
                    .build()

                imageAnalyzer!!.setAnalyzer(cameraExecutor!!, scanWorker)

                cameraProvider.unbindAll()

                try {
                    camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner!!,
                        cameraSelector,
                        preview,
                        imageAnalyzer
                    )

                    preview?.setSurfaceProvider(previewView.surfaceProvider)

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
                true
            }
            MotionEvent.ACTION_UP -> {
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
}