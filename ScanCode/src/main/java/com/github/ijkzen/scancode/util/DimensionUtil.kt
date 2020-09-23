package com.github.ijkzen.scancode.util

import android.content.Context
import android.util.DisplayMetrics

fun convertDp2Px(dp: Int, context: Context):Int{
    return dp * context.resources
        .displayMetrics.densityDpi / DisplayMetrics.DENSITY_DEFAULT
}