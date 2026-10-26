package com.banana.shotrecord.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 用 MediaRecorder 录制 AAC/M4A 音频,保存到应用私有目录 */
class AudioRecorder(context: Context) {

    private val appContext = context.applicationContext
    private var recorder: MediaRecorder? = null
    private var outFile: File? = null

    val isRecording: Boolean get() = recorder != null

    /** 开始录音,返回正在写入的文件 */
    fun start(): File {
        check(recorder == null) { "Already recording" }
        val dir = File(appContext.getExternalFilesDir(null) ?: appContext.filesDir, "recordings")
            .apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "rec_$stamp.m4a")

        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(appContext)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioEncodingBitRate(128_000)
        r.setAudioSamplingRate(44_100)
        r.setAudioChannels(1)
        r.setOutputFile(file.absolutePath)
        r.prepare()
        r.start()
        recorder = r
        outFile = file
        return file
    }

    /** 停止录音并返回文件;录音太短等导致失败时删除文件并返回 null */
    fun stop(): File? {
        val r = recorder ?: return null
        val file = outFile
        recorder = null
        outFile = null
        val ok = try {
            r.stop()
            true
        } catch (_: RuntimeException) {
            file?.delete()
            false
        } finally {
            r.release()
        }
        return if (ok) file else null
    }

    fun release() {
        recorder?.release()
        recorder = null
        outFile = null
    }
}
