package tech.ijkzen.scancodeview

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import com.github.ijkzen.scancode.ScanCodeViewX
import com.github.ijkzen.scancode.listener.ScanResultListener

class MainActivity : AppCompatActivity() {

    private var isOpenFlash = false

    private lateinit var codeView: ScanCodeViewX

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        codeView = findViewById<ScanCodeViewX>(R.id.scan_code)
        codeView.setLifecycle(this, this)
        codeView.setupCamera()
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
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN
            .or(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
            .or(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
            .or(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
        window.statusBarColor = Color.TRANSPARENT
    }
}