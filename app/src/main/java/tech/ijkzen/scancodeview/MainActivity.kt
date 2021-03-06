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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.ijkzen.scancode.ScanCodeViewX
import com.github.ijkzen.scancode.listener.ScanResultListener

class MainActivity : AppCompatActivity() {

    private var isOpenFlash = false

    private lateinit var codeView: ScanCodeViewX

    private val mAdapter = ResultAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        codeView = findViewById<ScanCodeViewX>(R.id.scan_code)
        codeView.setLifecycle(this, this)
        codeView.initCamera()
        codeView.setShowFocusCircle(true)
        val result = findViewById<RecyclerView>(R.id.result)
        result.layoutManager = LinearLayoutManager(this)
        result.adapter = mAdapter

        codeView.setResultListener(object : ScanResultListener {
            override fun onScanResult(resultList: List<String>) {
                mAdapter.setList(resultList)
                codeView.setContinue(true)
            }
        })

        val flashText = findViewById<AppCompatTextView>(R.id.flash_text)
        findViewById<AppCompatImageView>(R.id.flash_img)
            .setOnClickListener {
                if (isOpenFlash) {
                    isOpenFlash = false
                    codeView.closeFlash()
                    flashText.text = "Turn On"
                } else {
                    isOpenFlash = true
                    codeView.openFlash()
                    flashText.text = "Turn Off"
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