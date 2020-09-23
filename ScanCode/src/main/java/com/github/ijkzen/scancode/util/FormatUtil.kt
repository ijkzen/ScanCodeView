package com.github.ijkzen.scancode.util

import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import androidx.camera.core.ImageProxy
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer

// convert yuv_420_888 to nv21
fun yuv888ToNv21(image: Image): ByteArray {
    val crop: Rect = image.cropRect
    val format: Int = image.format
    val width: Int = crop.width()
    val height: Int = crop.height()
    val planes: Array<Image.Plane> = image.planes
    val data = ByteArray(width * height * ImageFormat.getBitsPerPixel(format) / 8)
    val rowData = ByteArray(planes[0].rowStride)
    var channelOffset = 0
    var outputStride = 1
    for (i in planes.indices) {
        when (i) {
            0 -> {
                channelOffset = 0
                outputStride = 1
            }
            1 -> {
                channelOffset = width * height + 1
                outputStride = 2
            }
            2 -> {
                channelOffset = width * height
                outputStride = 2
            }
        }
        val buffer: ByteBuffer = planes[i].buffer
        val rowStride: Int = planes[i].rowStride
        val pixelStride: Int = planes[i].pixelStride

        val shift = if (i == 0) 0 else 1
        val w = width shr shift
        val h = height shr shift
        buffer.position(rowStride * (crop.top shr shift) + pixelStride * (crop.left shr shift))
        for (row in 0 until h) {
            var length: Int
            if (pixelStride == 1 && outputStride == 1) {
                length = w
                buffer.get(data, channelOffset, length)
                channelOffset += length
            } else {
                length = (w - 1) * pixelStride + 1
                buffer.get(rowData, 0, length)
                for (col in 0 until w) {
                    data[channelOffset] = rowData[col * pixelStride]
                    channelOffset += outputStride
                }
            }
            if (row < h - 1) {
                buffer.position(buffer.position() + rowStride - length)
            }
        }
    }
    return data
}

fun yuv888ToNv21(image: ImageProxy): ByteArray {
    val crop: Rect = image.cropRect
    val format: Int = image.format
    val width: Int = crop.width()
    val height: Int = crop.height()
    val planes: Array<ImageProxy.PlaneProxy> = image.planes
    val data = ByteArray(width * height * ImageFormat.getBitsPerPixel(format) / 8)
    val rowData = ByteArray(planes[0].rowStride)
    var channelOffset = 0
    var outputStride = 1
    for (i in planes.indices) {
        when (i) {
            0 -> {
                channelOffset = 0
                outputStride = 1
            }
            1 -> {
                channelOffset = width * height + 1
                outputStride = 2
            }
            2 -> {
                channelOffset = width * height
                outputStride = 2
            }
        }
        val buffer: ByteBuffer = planes[i].buffer
        val rowStride: Int = planes[i].rowStride
        val pixelStride: Int = planes[i].pixelStride

        val shift = if (i == 0) 0 else 1
        val w = width shr shift
        val h = height shr shift
        buffer.position(rowStride * (crop.top shr shift) + pixelStride * (crop.left shr shift))
        for (row in 0 until h) {
            var length: Int
            if (pixelStride == 1 && outputStride == 1) {
                length = w
                buffer.get(data, channelOffset, length)
                channelOffset += length
            } else {
                length = (w - 1) * pixelStride + 1
                buffer.get(rowData, 0, length)
                for (col in 0 until w) {
                    data[channelOffset] = rowData[col * pixelStride]
                    channelOffset += outputStride
                }
            }
            if (row < h - 1) {
                buffer.position(buffer.position() + rowStride - length)
            }
        }
    }
    return data
}


// rotate 90 degree for nv21 image
fun rotate90ForNv21(nv21_data: ByteArray, width: Int, height: Int): ByteArray? {
    val ySize = width * height
    val bufferSize = ySize * 3 / 2
    val rotatedNv21 = ByteArray(bufferSize)
    var i = 0
    val startPos = (height - 1) * width
    for (x in 0 until width) {
        var offset = startPos
        for (y in height - 1 downTo 0) {
            rotatedNv21[i] = nv21_data[offset + x]
            i++
            offset -= width
        }
    }

    i = bufferSize - 1
    var x = width - 1
    while (x > 0) {
        var offset = ySize
        for (y in 0 until height / 2) {
            rotatedNv21[i] = nv21_data[offset + x]
            i--
            rotatedNv21[i] = nv21_data[offset + (x - 1)]
            i--
            offset += width
        }
        x -= 2
    }
    return rotatedNv21
}

fun NV21toJPEG(nv21: ByteArray?, width: Int, height: Int): ByteArray? {
    val out = ByteArrayOutputStream()
    val yuv = YuvImage(nv21, ImageFormat.NV21, width, height, null)
    yuv.compressToJpeg(Rect(0, 0, width, height), 100, out)
    return out.toByteArray()
}

fun saveJpeg2File(jpeg: ByteArray?, context: Context) {
    try {
        val bos = BufferedOutputStream(
            FileOutputStream(
                context.getExternalCacheDir()!!.getAbsolutePath()
                    .toString() + "/" + System.currentTimeMillis() + ".jpg"
            )
        )
        bos.write(jpeg)
        bos.flush()
        bos.close()
        //            Log.e(TAG, "" + data.length + " bytes have been written to " + filesDir + fileName + ".jpg");
    } catch (e: IOException) {
        e.printStackTrace()
    }
}