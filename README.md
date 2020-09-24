![bage](https://jitpack.io/v/ijkzen/ScanCodeView.svg) ![week_download](https://jitpack.io/v/ijkzen/ScanCodeView/week.svg) ![month_download](https://jitpack.io/v/ijkzen/ScanCodeView/month.svg) ![build status](https://github.com/ijkzen/ScanCodeView/workflows/ScanCodeView/badge.svg)

**ScanCodeView** is a library to scan code for android app

### Preview

<img src='preview/preview.gif' height=800px/>



[Demo Download](./preview/scanCode-demo.apk)


### Feature


- Scan barCode and QR code 
- Scan multi-code at the same time
- Support to auto-focus by tap
- Support to show focus view after tap 

### Usage

1. Add it in your **root**  `build.gradle` at the end of repositories

```groovy
allprojects {
	repositories {
		...
		maven { url 'https://jitpack.io' }
	}
}
```

2. Add the dependency

![bage](https://jitpack.io/v/ijkzen/ScanCodeView.svg)

```groovy
dependencies {
    ...
    def camerax_version = '1.0.0-beta09'
    implementation "androidx.camera:camera-core:$camerax_version"

    // CameraX Camera2 extensions
    implementation "androidx.camera:camera-camera2:$camerax_version"

    // CameraX Lifecycle library
    implementation "androidx.camera:camera-lifecycle:$camerax_version"

    // CameraX View class
    implementation 'androidx.camera:camera-view:1.0.0-alpha16'
    
    implementation 'com.github.ijkzen:ScanCodeView:<latest_version>'
}
```

### Practice

#### First

To use this library, you must change Applicatioin class like below.

```kotlin

class MyApplication: Application(),   CameraXConfig.Provider {
    companion object {
        private lateinit var instance: MyApplication
        fun instance() = instance
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun getCameraXConfig(): CameraXConfig {
        return Camera2Config.defaultConfig()
    }
}

```

#### About Lifecycle

```kotlin

// bind camera resource to lifecycle, must be called before initCamera()
// context must be Application or Activity
   fun setLifecycle(owner: LifecycleOwner, context: Context)

// initialize camera to scan code
    fun initCamera()
    
// release cache data to avoid memory leak
    fun releaseCamera()

// open flash if torch is available
    fun openFlash()
// close flash if torch is available
    fun closeFlash()

// continue to scan code or not
    fun setContinue(continueScan: Boolean)

// callback for scan result
    fun setResultListener(listener: ScanResultListener)

// show focus view for tap or not 
    fun setShowFocusCircle(show: Boolean)

```

### More

If you have any questions, please ask me [here](https://github.com/ijkzen/ScanCodeView/issues)
