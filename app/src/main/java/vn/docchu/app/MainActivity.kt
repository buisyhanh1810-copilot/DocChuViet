package vn.docchu.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import androidx.core.os.BundleCompat
import java.io.File
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private lateinit var preview: ImageView
    private lateinit var btnRecognize: Button
    private lateinit var progress: ProgressBar
    private lateinit var status: TextView
    private lateinit var result: EditText
    private lateinit var modeDigits: RadioButton
    private lateinit var chkEnhance: CheckBox
    private lateinit var btnPause: Button
    private lateinit var ttsNote: TextView
    private lateinit var btnInstallVoice: Button
    private lateinit var rateLabel: TextView

    private val worker: ExecutorService = Executors.newSingleThreadExecutor()
    private lateinit var ocr: OcrEngine

    private var image: Bitmap? = null
    private var photoUri: Uri? = null
    private var busy = false
    private var defaultStatusColor = 0

    // Đọc to
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var speechRate = 1.0f
    private var chunks: List<String> = emptyList()
    private var currentChunk = 0
    private var paused = false
    private var speakSession = 0

    private val takePicture =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
            val uri = photoUri
            if (ok && uri != null) loadImage(uri) else setStatus("Đã hủy chụp ảnh.")
        }

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) loadImage(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        preview = findViewById(R.id.preview)
        btnRecognize = findViewById(R.id.btnRecognize)
        progress = findViewById(R.id.progress)
        status = findViewById(R.id.status)
        result = findViewById(R.id.result)
        modeDigits = findViewById(R.id.modeDigits)
        chkEnhance = findViewById(R.id.chkEnhance)
        btnPause = findViewById(R.id.btnPause)
        ttsNote = findViewById(R.id.ttsNote)
        btnInstallVoice = findViewById(R.id.btnInstallVoice)
        rateLabel = findViewById(R.id.rateLabel)

        ocr = OcrEngine(applicationContext)
        defaultStatusColor = status.currentTextColor
        photoUri = savedInstanceState?.let { BundleCompat.getParcelable(it, KEY_PHOTO, Uri::class.java) }

        findViewById<Button>(R.id.btnCamera).setOnClickListener { openCamera() }
        findViewById<Button>(R.id.btnGallery).setOnClickListener { pickImage.launch("image/*") }
        btnRecognize.setOnClickListener { recognize() }
        findViewById<Button>(R.id.btnSpeak).setOnClickListener { speakFromStart() }
        btnPause.setOnClickListener { togglePause() }
        findViewById<Button>(R.id.btnStop).setOnClickListener { stopSpeaking() }
        findViewById<Button>(R.id.btnCopy).setOnClickListener { copyText() }
        findViewById<Button>(R.id.btnShare).setOnClickListener { shareText() }
        btnInstallVoice.setOnClickListener { installVoice() }

        findViewById<SeekBar>(R.id.seekRate).setOnSeekBarChangeListener(object :
            SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                speechRate = (p + 5) / 10f
                rateLabel.text = String.format(Locale.US, "%.1f×", speechRate)
                tts?.setSpeechRate(speechRate)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        ttsNote.text = "Đang chuẩn bị giọng đọc…"
        tts = TextToSpeech(this, this)

        // Chuẩn bị bộ nhận dạng sẵn ở nền để lần bấm đầu tiên nhanh hơn
        worker.execute { runCatching { ocr.ensureReady() } }

        handleShareIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        photoUri?.let { outState.putParcelable(KEY_PHOTO, it) }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        worker.execute { ocr.close() }
        worker.shutdown()
        super.onDestroy()
    }

    // ---------------- Ảnh ----------------

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
            IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                ?.let { loadImage(it) }
        }
    }

    private fun openCamera() {
        try {
            val dir = File(cacheDir, "photos").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, "photo_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            photoUri = uri
            takePicture.launch(uri)
        } catch (e: Exception) {
            setStatus("Không mở được camera: ${e.message}", true)
        }
    }

    private fun loadImage(uri: Uri) {
        setBusy(true, "Đang mở ảnh…")
        worker.execute {
            try {
                val bmp = ImageUtils.load(applicationContext, uri)
                runOnUiThread {
                    image = bmp
                    preview.setImageBitmap(bmp)
                    preview.visibility = View.VISIBLE
                    setBusy(false, "Ảnh đã sẵn sàng. Bấm Nhận dạng.")
                }
            } catch (e: Throwable) {
                runOnUiThread { setBusy(false, "Không mở được ảnh: ${e.message}", true) }
            }
        }
    }

    // ---------------- Nhận dạng ----------------

    private fun recognize() {
        val src = image ?: return
        if (busy) return
        val mode = if (modeDigits.isChecked) OcrEngine.Mode.DIGITS else OcrEngine.Mode.TEXT
        val enhance = chkEnhance.isChecked
        setBusy(true, "Đang nhận dạng…")
        worker.execute {
            val t0 = System.currentTimeMillis()
            try {
                val prepared = ImageUtils.prepare(src, enhance)
                val r = try {
                    ocr.recognize(prepared, mode)
                } finally {
                    prepared.recycle()
                }
                val sec = (System.currentTimeMillis() - t0) / 1000.0
                runOnUiThread {
                    result.setText(r.text)
                    if (r.text.isEmpty()) {
                        setBusy(false, "Không tìm thấy chữ. Thử chụp gần hơn, thẳng hơn hoặc sáng hơn.", true)
                    } else {
                        setBusy(
                            false,
                            String.format(
                                Locale.US,
                                "Xong sau %.1f giây · độ tin cậy khoảng %d%%.",
                                sec, r.confidence
                            )
                        )
                    }
                }
            } catch (e: OutOfMemoryError) {
                runOnUiThread { setBusy(false, "Ảnh quá lớn, máy không đủ bộ nhớ.", true) }
            } catch (e: Throwable) {
                runOnUiThread { setBusy(false, "Lỗi khi nhận dạng: ${e.message}", true) }
            }
        }
    }

    private fun setBusy(b: Boolean, msg: String, error: Boolean = false) {
        busy = b
        progress.visibility = if (b) View.VISIBLE else View.GONE
        btnRecognize.isEnabled = !b && image != null
        setStatus(msg, error)
    }

    private fun setStatus(msg: String, error: Boolean = false) {
        status.text = msg
        status.setTextColor(if (error) 0xFFB3471F.toInt() else defaultStatusColor)
    }

    // ---------------- Đọc to ----------------

    override fun onInit(code: Int) {
        val engine = tts ?: return
        if (code != TextToSpeech.SUCCESS) {
            ttsNote.text = "Máy này chưa có bộ đọc văn bản. Hãy cài \"Dịch vụ Lời nói của Google\" từ CH Play."
            btnInstallVoice.visibility = View.VISIBLE
            return
        }
        val r = engine.setLanguage(VI_LOCALE)
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            ttsNote.text = "Chưa có giọng tiếng Việt trên máy, nên sẽ đọc sai dấu. Bấm nút dưới để cài, " +
                "hoặc vào Cài đặt › Hỗ trợ tiếp cận › Chuyển văn bản thành giọng nói."
            btnInstallVoice.visibility = View.VISIBLE
        } else {
            ttsNote.text = "Giọng đọc tiếng Việt đã sẵn sàng."
            btnInstallVoice.visibility = View.GONE
        }
        ttsReady = true
        engine.setSpeechRate(speechRate)
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {
                val (session, idx) = parseId(id) ?: return
                runOnUiThread { if (session == speakSession) currentChunk = idx }
            }

            override fun onDone(id: String?) {
                val (session, idx) = parseId(id) ?: return
                runOnUiThread {
                    if (session == speakSession && idx == chunks.lastIndex && !paused) {
                        currentChunk = 0
                        chunks = emptyList()
                    }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {
            }
        })
    }

    override fun onResume() {
        super.onResume()
        // Người dùng có thể vừa cài giọng tiếng Việt xong
        val engine = tts
        if (ttsReady && engine != null && btnInstallVoice.visibility == View.VISIBLE) {
            val r = engine.setLanguage(VI_LOCALE)
            if (r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED) {
                ttsNote.text = "Giọng đọc tiếng Việt đã sẵn sàng."
                btnInstallVoice.visibility = View.GONE
            }
        }
    }

    private fun parseId(id: String?): Pair<Int, Int>? {
        val parts = id?.split('_') ?: return null
        if (parts.size != 3) return null
        val s = parts[1].toIntOrNull() ?: return null
        val i = parts[2].toIntOrNull() ?: return null
        return s to i
    }

    private fun speakFromStart() {
        val text = result.text.toString().trim()
        if (text.isEmpty()) {
            setStatus("Chưa có văn bản để đọc.", true); return
        }
        if (!ttsReady) {
            setStatus("Giọng đọc chưa sẵn sàng.", true); return
        }
        chunks = splitText(text)
        speakFrom(0)
    }

    private fun speakFrom(start: Int) {
        val engine = tts ?: return
        speakSession++
        paused = false
        btnPause.text = "⏸️ Dừng tạm"
        engine.stop()
        engine.setSpeechRate(speechRate)
        currentChunk = start
        for (i in start until chunks.size) {
            val mode = if (i == start) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            engine.speak(chunks[i], mode, null, "u_${speakSession}_$i")
        }
    }

    private fun togglePause() {
        val engine = tts ?: return
        if (paused) {
            if (chunks.isNotEmpty()) speakFrom(currentChunk)
        } else if (engine.isSpeaking) {
            paused = true
            speakSession++ // bỏ qua các sự kiện còn lại của lượt đọc cũ
            engine.stop()
            btnPause.text = "▶️ Đọc tiếp"
        }
    }

    private fun stopSpeaking() {
        speakSession++
        paused = false
        chunks = emptyList()
        currentChunk = 0
        btnPause.text = "⏸️ Dừng tạm"
        tts?.stop()
    }

    /** Chia văn bản thành các câu ngắn để có thể dừng tạm rồi đọc tiếp đúng chỗ. */
    private fun splitText(text: String): List<String> {
        val flat = text.replace(Regex("\\s+"), " ")
        val parts = Regex("[^.!?;:…]+[.!?;:…]*\\s*").findAll(flat).map { it.value }.toList()
            .ifEmpty { listOf(flat) }
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        for (p in parts) {
            if (cur.length + p.length > 250 && cur.isNotEmpty()) {
                out.add(cur.toString().trim()); cur.setLength(0)
            }
            if (p.length > 1000) {
                p.chunked(1000).forEach { out.add(it) }
            } else {
                cur.append(p)
            }
        }
        if (cur.isNotBlank()) out.add(cur.toString().trim())
        return out
    }

    private fun installVoice() {
        try {
            startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))
        } catch (e: Exception) {
            try {
                startActivity(Intent("com.android.settings.TTS_SETTINGS"))
            } catch (e2: Exception) {
                Toast.makeText(this, "Hãy mở Cài đặt › Chuyển văn bản thành giọng nói.", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ---------------- Sao chép, chia sẻ ----------------

    private fun copyText() {
        val text = result.text.toString()
        if (text.isBlank()) return
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Văn bản", text))
        Toast.makeText(this, "Đã sao chép.", Toast.LENGTH_SHORT).show()
    }

    private fun shareText() {
        val text = result.text.toString()
        if (text.isBlank()) return
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(send, "Chia sẻ văn bản"))
    }

    companion object {
        private const val KEY_PHOTO = "photo_uri"
        private val VI_LOCALE: Locale = Locale.forLanguageTag("vi-VN")
    }
}
