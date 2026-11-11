package vn.docchu.app

import android.content.Context
import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File

/** Nhận dạng chữ tiếng Việt offline bằng Tesseract (mô hình LSTM "vie" đi kèm ứng dụng). */
class OcrEngine(private val context: Context) {

    enum class Mode { TEXT, DIGITS }

    data class Result(val text: String, val confidence: Int)

    private var api: TessBaseAPI? = null

    /** Sao chép dữ liệu ngôn ngữ từ assets ra bộ nhớ trong (chỉ lần đầu) và khởi động Tesseract. */
    @Synchronized
    fun ensureReady() {
        if (api != null) return
        val root = File(context.filesDir, "ocr")
        val tessdata = File(root, "tessdata").apply { mkdirs() }
        val target = File(tessdata, "$LANG.traineddata")
        @Suppress("DEPRECATION")
        val installedAt = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
        if (!target.exists() || target.length() == 0L || target.lastModified() < installedAt) {
            val tmp = File(tessdata, "$LANG.traineddata.tmp")
            context.assets.open("tessdata/$LANG.traineddata").use { input ->
                tmp.outputStream().use { input.copyTo(it) }
            }
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
        }
        val t = TessBaseAPI()
        if (!t.init(root.absolutePath, LANG, TessBaseAPI.OEM_LSTM_ONLY)) {
            t.recycle()
            throw IllegalStateException("Không khởi động được bộ nhận dạng.")
        }
        api = t
    }

    @Synchronized
    fun recognize(bitmap: Bitmap, mode: Mode): Result {
        ensureReady()
        val t = api!!
        if (mode == Mode.DIGITS) {
            t.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, "0123456789.,:;-/+%() ")
            t.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK)
        } else {
            t.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, "")
            t.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
        }
        t.setImage(bitmap)
        val raw = t.getUTF8Text() ?: ""
        val conf = t.meanConfidence()
        t.clear()
        val text = raw
            .replace(Regex("[ \\t]+\\n"), "\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
        return Result(text, conf)
    }

    @Synchronized
    fun close() {
        api?.recycle()
        api = null
    }

    companion object {
        const val LANG = "vie"
    }
}
