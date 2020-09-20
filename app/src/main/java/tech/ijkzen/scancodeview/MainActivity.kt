package tech.ijkzen.scancodeview

import android.Manifest
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import com.github.ijkzen.scancode.ScanCodeView
import com.github.ijkzen.scancode.listener.ScanResultListener

class MainActivity : AppCompatActivity() {

    private var isOpenFlash = false

    private lateinit var codeView: ScanCodeView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        codeView = findViewById<ScanCodeView>(R.id.scan_code)
        val result = findViewById<AppCompatTextView>(R.id.result)
        codeView.setResultListener(object : ScanResultListener {
            override fun onScanResult(resultList: List<String>) {
                result.text = resultList[0]
                codeView.setContinue(true)
            }
        })

        findViewById<AppCompatImageView>(R.id.flash_img)
            .setOnClickListener {
                if (isOpenFlash) {
                    isOpenFlash = false
                    codeView.closeFlash()
                } else {
                    isOpenFlash = true
                    codeView.openFlash()
                }
            }

        checkPermission()
    }

    private fun checkPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                requestPermissions(arrayOf(Manifest.permission.CAMERA), 101)
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            codeView.initCamera()
        }
    }

    override fun onResume() {
        super.onResume()
        codeView.initCamera()
    }

    override fun onPause() {
        super.onPause()
        codeView.releaseCamera()
    }

    override fun onDestroy() {
        super.onDestroy()
        codeView.destroyCamera()
    }
}