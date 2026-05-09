package com.mienmien.android

import android.os.Bundle
import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.graphics.Color
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    companion object {
        private const val PREFS_NAME = "mienmien_android_ui"
        private const val KEY_TIMELINE_FILTER = "timeline_filter"
    }

    private val api = ConsumerApi()
    private var sessionId: String? = null
    private var audioRecord: AudioRecord? = null
    @Volatile private var recording = false
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val log = TextView(this).apply {
            textSize = 14f
            setPadding(16, 16, 16, 16)
        }
        val timelineTitle = TextView(this).apply {
            text = "回合时间线"
            textSize = 16f
            setPadding(16, 16, 16, 8)
        }
        val timelineStats = TextView(this).apply {
            textSize = 12f
            setPadding(16, 0, 16, 8)
            setTextColor(Color.DKGRAY)
        }
        val timelineAdapter = TurnTimelineAdapter()
        val timelineList = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = timelineAdapter
        }
        val question = EditText(this).apply { hint = "文本问题（走 /events/text）" }
        val filterTabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 0, 16, 8)
        }
        val btnFilterAll = Button(this).apply { text = "全部" }
        val btnFilterInterviewer = Button(this).apply { text = "面试官" }
        val btnFilterCandidate = Button(this).apply { text = "候选人" }
        val btnClearTimeline = Button(this).apply { text = "清空时间线" }
        val btnExportTimeline = Button(this).apply { text = "导出" }
        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 0, 16, 8)
        }
        val logScroll = ScrollView(this).apply {
            addView(log)
        }
        timelineAdapter.onItemLongPressCopy = { line ->
            runOnUiThread {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("mienmien_timeline_item", line))
                appendLog("timeline_item_copied")
            }
        }

        fun appendLog(line: String) {
            runOnUiThread {
                log.append(line)
                log.append("\n")
                if (log.lineCount > 500) {
                    val text = log.text?.toString().orEmpty()
                    val lines = text.lines()
                    log.text = lines.takeLast(350).joinToString("\n")
                    log.append("\n")
                }
            }
        }

        fun refreshStats() {
            val s = timelineAdapter.stats()
            timelineStats.text = "总计 ${s.total} | 面试官 ${s.interviewer} | 候选人 ${s.candidate} | 当前可见 ${s.visible}"
        }

        fun appendTimelineCard(turnType: String, title: String, content: String) {
            runOnUiThread {
                timelineAdapter.prepend(
                    TurnTimelineItem(
                        turnType = turnType,
                        title = title,
                        content = content
                    )
                )
                timelineList.scrollToPosition(0)
                refreshStats()
            }
        }

        fun setFilterTabSelected(selected: TimelineFilter) {
            fun style(btn: Button, active: Boolean) {
                btn.setBackgroundColor(if (active) Color.parseColor("#90CAF9") else Color.parseColor("#ECEFF1"))
                btn.setTextColor(if (active) Color.BLACK else Color.DKGRAY)
            }
            style(btnFilterAll, selected == TimelineFilter.ALL)
            style(btnFilterInterviewer, selected == TimelineFilter.INTERVIEWER)
            style(btnFilterCandidate, selected == TimelineFilter.CANDIDATE)
        }

        filterTabs.addView(btnFilterAll, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        filterTabs.addView(btnFilterInterviewer, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        filterTabs.addView(btnFilterCandidate, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        actionRow.addView(btnClearTimeline, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        actionRow.addView(btnExportTimeline, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        fun applyFilter(filter: TimelineFilter, emitLog: Boolean = true) {
            timelineAdapter.setFilter(filter)
            setFilterTabSelected(filter)
            prefs.edit().putString(KEY_TIMELINE_FILTER, filter.name).apply()
            if (emitLog) {
                appendLog("timeline_filter: ${filter.name.lowercase()}")
            }
            refreshStats()
        }

        btnFilterAll.setOnClickListener {
            applyFilter(TimelineFilter.ALL)
        }
        btnFilterInterviewer.setOnClickListener {
            applyFilter(TimelineFilter.INTERVIEWER)
        }
        btnFilterCandidate.setOnClickListener {
            applyFilter(TimelineFilter.CANDIDATE)
        }
        btnClearTimeline.setOnClickListener {
            timelineAdapter.clearAll()
            appendLog("timeline_cleared")
            refreshStats()
        }
        btnExportTimeline.setOnClickListener {
            val text = buildExportPayload(
                body = timelineAdapter.exportText(),
                filter = timelineAdapter.currentFilter()
            )
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("mienmien_timeline", text))
            val file = exportTimelineToFile(text)
            appendLog("timeline_exported_filtered_to_clipboard")
            if (file != null) {
                appendLog("timeline_file: ${file.absolutePath}")
            } else {
                appendLog("timeline_file_export_failed")
            }
        }
        val savedFilter = parseTimelineFilter(
            prefs.getString(KEY_TIMELINE_FILTER, TimelineFilter.ALL.name)
        )
        applyFilter(savedFilter, emitLog = false)

        val btnSession = Button(this).apply { text = "创建会话" }
        btnSession.setOnClickListener {
            Thread {
                try {
                    val json = api.createSession()
                    sessionId = JSONObject(json).getString("sessionId")
                    appendLog("session: $sessionId")
                } catch (e: Exception) {
                    appendLog("err: ${e.message}")
                }
            }.start()
        }

        val btnText = Button(this).apply { text = "发送文本问题" }
        btnText.setOnClickListener {
            val sid = sessionId ?: return@setOnClickListener appendLog("请先创建会话")
            val q = question.text?.toString()?.trim().orEmpty().ifBlank { "兜底文本问题" }
            Thread {
                try {
                    val body = api.postTextEvent(sid, q)
                    appendLog("text_event: $body")
                } catch (e: Exception) {
                    appendLog("err: ${e.message}")
                }
            }.start()
        }

        val btnStream = Button(this).apply { text = "订阅 SSE 流式回答" }
        btnStream.setOnClickListener {
            val sid = sessionId ?: return@setOnClickListener appendLog("请先创建会话")
            Thread {
                try {
                    appendLog("--- sse start ---")
                    api.streamAnswerLines(sid) { line -> appendLog(line) }
                    appendLog("--- sse end ---")
                } catch (e: Exception) {
                    appendLog("err: ${e.message}")
                }
            }.start()
        }

        val btnOnce = Button(this).apply { text = "一次性降级回答" }
        btnOnce.setOnClickListener {
            val sid = sessionId ?: return@setOnClickListener appendLog("请先创建会话")
            Thread {
                try {
                    appendLog(api.onceAnswer(sid))
                } catch (e: Exception) {
                    appendLog("err: ${e.message}")
                }
            }.start()
        }

        val btnPhotoQa = Button(this).apply { text = "拍照问答建议" }
        btnPhotoQa.setOnClickListener {
            val sid = sessionId ?: return@setOnClickListener appendLog("请先创建会话")
            Thread {
                try {
                    appendLog(api.photoQa(sid))
                } catch (e: Exception) {
                    appendLog("err: ${e.message}")
                }
            }.start()
        }

        val btnRealtimeStart = Button(this).apply { text = "开始实时语音(WebSocket)" }
        val btnRealtimeStop = Button(this).apply { text = "停止实时语音" }
        btnRealtimeStop.isEnabled = false
        btnRealtimeStart.setOnClickListener {
            if (recording) {
                appendLog("实时语音已在运行")
                return@setOnClickListener
            }
            val sid = sessionId ?: return@setOnClickListener appendLog("请先创建会话")
            if (!ensureRecordPermission()) {
                appendLog("请先授权麦克风权限")
                return@setOnClickListener
            }
            api.startRealtimeDiarization(
                sessionId = sid,
                mode = "unsupervised",
                onTextEvent = { raw ->
                    when (val evt = RealtimeEventParser.parse(raw)) {
                        is RealtimeInbound.Transcription -> {
                            appendLog("实时转写[${evt.speaker}] ${evt.text} (conf=${"%.2f".format(evt.confidence)})")
                        }
                        is RealtimeInbound.Turn -> {
                            val label = RealtimeEventParser.turnTypeToLabel(evt.event.type)
                            appendLog("回合事件: $label")
                            appendTimelineCard(evt.event.type, label, evt.event.text)
                            if (evt.event.text.isNotBlank()) {
                                appendLog("  内容: ${evt.event.text}")
                            }
                        }
                        is RealtimeInbound.Other -> appendLog("ws: ${evt.raw}")
                    }
                },
                onError = { appendLog("ws_err: $it") }
            )
            startMicCapture { frame -> api.sendPcmFrame(frame) }
            btnRealtimeStart.isEnabled = false
            btnRealtimeStop.isEnabled = true
            appendLog("实时语音已启动")
        }

        btnRealtimeStop.setOnClickListener {
            stopMicCapture()
            api.stopRealtimeDiarization()
            btnRealtimeStart.isEnabled = true
            btnRealtimeStop.isEnabled = false
            appendLog("实时语音已停止")
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(btnSession)
            addView(question)
            addView(btnText)
            addView(btnStream)
            addView(btnOnce)
            addView(btnPhotoQa)
            addView(btnRealtimeStart)
            addView(btnRealtimeStop)
            addView(timelineTitle)
            addView(timelineStats)
            addView(filterTabs)
            addView(actionRow)
            addView(timelineList, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                0.55f
            ))
            addView(logScroll, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            ))
        }

        setContentView(layout)
    }

    override fun onDestroy() {
        stopMicCapture()
        api.stopRealtimeDiarization()
        super.onDestroy()
    }

    private fun ensureRecordPermission(): Boolean {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            return true
        }
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1001)
        return false
    }

    private fun startMicCapture(onFrame: (ByteArray) -> Unit) {
        if (recording) return
        val sampleRate = 16000
        val min = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = maxOf(min, 1600)
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
        val recorder = audioRecord ?: return
        recorder.startRecording()
        recording = true
        Thread {
            val frame = ByteArray(800)
            while (recording) {
                val n = recorder.read(frame, 0, frame.size)
                if (n > 0) {
                    onFrame(if (n == frame.size) frame.copyOf() else frame.copyOf(n))
                }
            }
        }.start()
    }

    private fun stopMicCapture() {
        recording = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }

    private fun parseTimelineFilter(raw: String?): TimelineFilter {
        return try {
            TimelineFilter.valueOf(raw ?: TimelineFilter.ALL.name)
        } catch (_: Exception) {
            TimelineFilter.ALL
        }
    }

    private fun exportTimelineToFile(text: String): File? {
        return try {
            val sid = sessionId ?: "unknown_session"
            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val name = "timeline_${sid}_$ts.txt"
            val dir = getExternalFilesDir(null) ?: filesDir
            val out = File(dir, name)
            out.writeText(text)
            out
        } catch (_: Exception) {
            null
        }
    }

    private fun buildExportPayload(body: String, filter: TimelineFilter): String {
        val sid = sessionId ?: "unknown_session"
        val exportedAt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val header = listOf(
            "# MienMien Timeline Export",
            "sessionId: $sid",
            "exportedAt: $exportedAt",
            "filter: ${filter.name}",
            ""
        ).joinToString(separator = "\n")
        return header + body
    }
}
