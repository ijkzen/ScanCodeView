![bage](https://jitpack.io/v/ijkzen/ScanCodeView.svg) ![week_download](https://jitpack.io/v/ijkzen/ScanCodeView/week.svg) ![month_download](https://jitpack.io/v/ijkzen/ScanCodeView/month.svg) ![build status](https://github.com/ijkzen/ScanCodeView/workflows/ScanCodeView/badge.svg)
### Preview

<img src='preview/preview.gif' height=800px/>



[Demo Download](./preview/scanCode-demo.apk)

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
    implementation 'com.github.ijkzen:ScanCodeView:<latest_version>'
}
```

### Practice

#### Get Scan Result
```kotlin

setResultListener(listener)

```

#### About Lifecycle

`OnResume`:  initCamera()
`OnPause`: releaseCamera()
`OnDestroy`: destroyCamera()

### More

If you have any questions, please ask me [here](https://github.com/ijkzen/ScanCodeView/issues)
