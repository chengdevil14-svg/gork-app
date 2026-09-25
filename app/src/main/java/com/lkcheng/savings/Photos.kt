package com.lkcheng.savings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import java.io.File
import kotlin.math.max

object Photos {
    private const val MAX_SIDE = 1600

    private fun dir(context: Context): File = File(context.filesDir, "photos").apply { mkdirs() }

    fun file(context: Context, name: String): File = File(dir(context), name)

    /** Copies a picture (camera or gallery) into app storage, resized and rotated upright. Returns file name. */
    fun import(context: Context, uri: Uri): String? = try {
        val cr = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val raw = cr.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
        if (raw == null) {
            null
        } else {
            val orientation = try {
                cr.openInputStream(uri)?.use {
                    ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                } ?: ExifInterface.ORIENTATION_NORMAL
            } catch (e: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }
            val degrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            val scale = minOf(1f, MAX_SIDE.toFloat() / max(raw.width, raw.height))
            val m = Matrix()
            if (scale < 1f) m.postScale(scale, scale)
            if (degrees != 0f) m.postRotate(degrees)
            val bmp = if (scale < 1f || degrees != 0f) {
                Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
            } else {
                raw
            }
            val name = "p_${System.currentTimeMillis()}.jpg"
            file(context, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 80, it) }
            name
        }
    } catch (e: Exception) {
        null
    }

    fun load(context: Context, name: String, sample: Int = 1): Bitmap? = try {
        val f = file(context, name)
        if (f.exists()) BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample }) else null
    } catch (e: Throwable) {
        null
    }

    /** Smaller JPEG for embedding inside a Google Sheet cell (limit ~1M pixels / 2MB). */
    fun base64(context: Context, name: String): String? = try {
        val src = BitmapFactory.decodeFile(file(context, name).path)
        if (src == null) {
            null
        } else {
            val scale = minOf(1f, 1000f / max(src.width, src.height))
            val bmp = if (scale < 1f) {
                Bitmap.createScaledBitmap(src, (src.width * scale).toInt(), (src.height * scale).toInt(), true)
            } else {
                src
            }
            val out = java.io.ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 75, out)
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        }
    } catch (e: Throwable) {
        null
    }

    fun delete(context: Context, name: String) {
        if (name.isNotEmpty()) file(context, name).delete()
    }
}
