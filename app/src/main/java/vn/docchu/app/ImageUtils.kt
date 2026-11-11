package vn.docchu.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlin.math.max
import kotlin.math.roundToInt

object ImageUtils {

    private const val MAX_SIDE = 2200
    private const val MIN_SIDE = 1200

    /** Đọc ảnh từ Uri, thu nhỏ khi giải mã để tiết kiệm bộ nhớ và xoay đúng chiều theo EXIF. */
    fun load(context: Context, uri: Uri): Bitmap {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IllegalArgumentException("Không đọc được ảnh.")
        }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: throw IllegalArgumentException("Không đọc được ảnh.")

        val orientation = try {
            resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
            else -> return decoded
        }
        val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true)
        if (rotated !== decoded) decoded.recycle()
        return rotated
    }

    /**
     * Chuẩn bị ảnh cho nhận dạng: đưa về cỡ 1200–2200 px theo cạnh dài,
     * và nếu [enhance] thì chuyển xám + giãn tương phản (cắt 1% sáng/tối nhất).
     */
    fun prepare(src: Bitmap, enhance: Boolean): Bitmap {
        val long = max(src.width, src.height)
        val scale = when {
            long > MAX_SIDE -> MAX_SIDE.toFloat() / long
            long < MIN_SIDE -> MIN_SIDE.toFloat() / long
            else -> 1f
        }
        val w = max(1, (src.width * scale).roundToInt())
        val h = max(1, (src.height * scale).roundToInt())
        val scaled = if (scale == 1f && src.config == Bitmap.Config.ARGB_8888) {
            src.copy(Bitmap.Config.ARGB_8888, true)
        } else {
            Bitmap.createScaledBitmap(src, w, h, true).let {
                if (it.isMutable && it.config == Bitmap.Config.ARGB_8888 && it !== src) it
                else it.copy(Bitmap.Config.ARGB_8888, true).also { c -> if (it !== src) it.recycle() }
            }
        }
        if (!enhance) return scaled

        val n = w * h
        val px = IntArray(n)
        scaled.getPixels(px, 0, w, 0, 0, w, h)
        val gray = IntArray(n)
        val hist = IntArray(256)
        for (i in 0 until n) {
            val c = px[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val v = (r * 299 + g * 587 + b * 114) / 1000
            gray[i] = v
            hist[v]++
        }
        val cut = n / 100
        var lo = 0
        var acc = 0
        for (v in 0..255) { acc += hist[v]; if (acc > cut) { lo = v; break } }
        var hi = 255
        acc = 0
        for (v in 255 downTo 0) { acc += hist[v]; if (acc > cut) { hi = v; break } }
        val range = max(1, hi - lo)
        for (i in 0 until n) {
            val v = ((gray[i] - lo) * 255 / range).coerceIn(0, 255)
            px[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        scaled.setPixels(px, 0, w, 0, 0, w, h)
        return scaled
    }
}
