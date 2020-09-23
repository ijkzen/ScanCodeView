package com.github.ijkzen.scancode

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.display.DisplayManager
import android.util.AttributeSet
import android.util.Log
import android.util.Size
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.github.ijkzen.scancode.listener.ScanResultListener
import com.github.ijkzen.scancode.util.*
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.multi.GenericMultipleBarcodeReader
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs

open class ScanCodeViewX : PreviewView, ScanManager {

    companion object {
        const val TAG = "ScanCodeViewX"
        private const val RATIO_4_3_VALUE = 4.0 / 3.0
        private const val RATIO_16_9_VALUE = 16.0 / 9.0
    }

    @Volatile
    private var mContinue = true

    private var frameCount: Long = 0
    private var displayId: Int = -1
    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var lifecycleOwner: LifecycleOwner? = null
    private var scanResultListener: ScanResultListener? = null
    private val mCodeReader = GenericMultipleBarcodeReader(MultiFormatReader())
    private lateinit var mApplicationContext: Context

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
        if (!mContinue || scanResultListener == null || frameCount++ % 5 != 0L) {
            proxy.close()
            return@Analyzer
        }
        val nv21 = yuv888ToNv21(proxy)
        val data = rotate90ForNv21(nv21, proxy.width, proxy.height)
//                    val jpg = NV21toJPEG(data, proxy.height, proxy.width)
//                    saveJpeg2File(jpg, context)
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
            val resultList = mCodeReader.decodeMultiple(bitmap)
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

    constructor(context: Context) : super(context)

    constructor(context: Context, attributeSet: AttributeSet) : super(context, attributeSet)

    constructor(context: Context, attributeSet: AttributeSet, defStyleAttr: Int) : super(
        context,
        attributeSet,
        defStyleAttr
    )

    constructor(
        context: Context,
        attributeSet: AttributeSet,
        defStyleAttr: Int,
        defStyleRes: Int
    ) : super(context, attributeSet, defStyleAttr, defStyleRes)

    @SuppressLint("UnsafeExperimentalUsageError")
    fun setupCamera() {
        if (lifecycleOwner == null) {
            throw RuntimeException(" Missing lifecycleOwner ")
        }
        displayManager.unregisterDisplayListener(displayListener)
        if (cameraExecutor == null) {
            cameraExecutor = Executors.newSingleThreadExecutor()
        }
        displayManager.registerDisplayListener(displayListener, null)
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

                    preview?.setSurfaceProvider(surfaceProvider)
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
}