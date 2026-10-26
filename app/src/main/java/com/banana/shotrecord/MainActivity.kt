package com.banana.shotrecord

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.banana.shotrecord.audio.AudioRecorder
import com.banana.shotrecord.data.Record
import com.banana.shotrecord.data.RecordStore
import com.banana.shotrecord.databinding.ActivityMainBinding
import com.banana.shotrecord.ui.RecordListAdapter
import com.banana.shotrecord.ui.formatDuration
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var store: RecordStore
    private lateinit var adapter: RecordListAdapter

    private val recorder by lazy { AudioRecorder(this) }
    private var mediaPlayer: MediaPlayer? = null
    private var playingId: Long? = null

    private var recordStartTime = 0L
    private val timerHandler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            binding.timerText.text = formatDuration(System.currentTimeMillis() - recordStartTime)
            timerHandler.postDelayed(this, 200)
        }
    }

    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startRecording()
            } else {
                Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 目标 SDK 35+ 默认全面屏,只给底部导航栏留出边距(顶部由 ActionBar 处理)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, 0, bars.right, bars.bottom)
            insets
        }

        store = RecordStore(this)
        adapter = RecordListAdapter(onPlayClick = ::onPlayClicked, onDeleteClick = ::onDeleteClicked)
        binding.recordList.layoutManager = LinearLayoutManager(this)
        binding.recordList.adapter = adapter

        binding.recordButton.setOnClickListener {
            if (recorder.isRecording) {
                stopRecording()
            } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED
            ) {
                startRecording()
            } else {
                requestPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }

        refreshList()
    }

    private fun startRecording() {
        try {
            recorder.start()
        } catch (_: Exception) {
            Toast.makeText(this, R.string.record_start_failed, Toast.LENGTH_SHORT).show()
            return
        }
        recordStartTime = System.currentTimeMillis()
        binding.statusText.text = getString(R.string.status_recording)
        binding.timerText.text = formatDuration(0)
        binding.recordButton.setImageResource(R.drawable.ic_stop)
        binding.recordButton.backgroundTintList =
            ColorStateList.valueOf(ContextCompat.getColor(this, R.color.record_button_recording))
        timerHandler.post(timerRunnable)
    }

    private fun stopRecording() {
        timerHandler.removeCallbacks(timerRunnable)
        val duration = System.currentTimeMillis() - recordStartTime
        val file = recorder.stop()
        resetRecordUi()
        if (file == null) {
            Toast.makeText(this, R.string.record_too_short, Toast.LENGTH_SHORT).show()
            return
        }
        store.add(file.absolutePath, duration, recordStartTime)
        refreshList()
    }

    private fun resetRecordUi() {
        binding.statusText.text = getString(R.string.status_idle)
        binding.timerText.text = getString(R.string.timer_zero)
        binding.recordButton.setImageResource(R.drawable.ic_mic)
        binding.recordButton.backgroundTintList =
            ColorStateList.valueOf(ContextCompat.getColor(this, R.color.record_button))
    }

    private fun refreshList() {
        val records = store.getAll()
        adapter.submit(records)
        val empty = records.isEmpty()
        binding.emptyText.visibility = if (empty) android.view.View.VISIBLE else android.view.View.GONE
        binding.recordList.visibility = if (empty) android.view.View.GONE else android.view.View.VISIBLE
    }

    private fun onPlayClicked(record: Record) {
        if (playingId == record.id) {
            stopPlayback()
            return
        }
        stopPlayback()
        val player = MediaPlayer()
        try {
            player.setDataSource(record.filePath)
            player.setOnCompletionListener {
                if (playingId == record.id) stopPlayback()
            }
            player.prepare()
            player.start()
            mediaPlayer = player
            playingId = record.id
            adapter.setPlayingId(record.id)
        } catch (_: Exception) {
            player.release()
            Toast.makeText(this, R.string.play_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopPlayback() {
        mediaPlayer?.run {
            try {
                stop()
            } catch (_: IllegalStateException) {
            }
            release()
        }
        mediaPlayer = null
        playingId = null
        adapter.setPlayingId(null)
    }

    private fun onDeleteClicked(record: Record) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_confirm_title)
            .setMessage(R.string.delete_confirm_message)
            .setPositiveButton(R.string.delete) { _, _ ->
                if (playingId == record.id) stopPlayback()
                store.delete(record.id)
                File(record.filePath).delete()
                refreshList()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onDestroy() {
        if (recorder.isRecording) stopRecording()
        timerHandler.removeCallbacks(timerRunnable)
        stopPlayback()
        super.onDestroy()
    }
}
