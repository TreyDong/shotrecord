@file:OptIn(ExperimentalMaterial3Api::class)

package com.banana.shotrecord

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.banana.shotrecord.audio.AudioRecorder
import com.banana.shotrecord.data.AsrSettings
import com.banana.shotrecord.data.Record
import com.banana.shotrecord.data.RecordStore
import com.banana.shotrecord.network.DoubaoAsrClient
import com.banana.shotrecord.ui.ShotRecordTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ShotRecordTheme {
                ShotRecordApp()
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    return String.format(Locale.US, "%02d:%02d", totalSeconds / 60, totalSeconds % 60)
}

private val dateFormat = SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.getDefault())

@Composable
fun ShotRecordApp() {
    val context = LocalContext.current
    val store = remember { RecordStore(context) }
    val asrSettings = remember { AsrSettings(context) }
    val recorder = remember { AudioRecorder(context) }
    val scope = rememberCoroutineScope()
    val transcribeExecutor = remember { Executors.newSingleThreadExecutor() }

    var screen by rememberSaveable { mutableStateOf("home") }
    var records by remember { mutableStateOf<List<Record>>(emptyList()) }
    var isRecording by remember { mutableStateOf(false) }
    var recordStartAt by remember { mutableLongStateOf(0L) }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    var transcribingIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var failedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var playingId by remember { mutableStateOf<Long?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var pendingDelete by remember { mutableStateOf<Record?>(null) }

    val snackbar: (String) -> Unit = { message ->
        // 转写回调在后台线程执行,Toast 必须回到主线程
        scope.launch { Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
    }

    val reload: () -> Unit = {
        scope.launch { records = withContext(Dispatchers.IO) { store.getAll() } }
    }

    val stopPlayback: () -> Unit = {
        mediaPlayer?.run {
            try {
                stop()
            } catch (_: IllegalStateException) {
            }
            release()
        }
        mediaPlayer = null
        playingId = null
    }

    val transcribe: (Record) -> Unit = transcribe@{ record ->
        if (!asrSettings.isConfigured) {
            snackbar(context.getString(R.string.asr_not_configured))
            screen = "settings"
            return@transcribe
        }
        if (record.id in transcribingIds) return@transcribe
        transcribingIds = transcribingIds + record.id
        failedIds = failedIds - record.id
        transcribeExecutor.execute {
            val result = DoubaoAsrClient.transcribe(File(record.filePath), asrSettings)
            transcribingIds = transcribingIds - record.id
            result.onSuccess { text ->
                failedIds = failedIds - record.id
                store.updateTranscript(record.id, text)
            }.onFailure { e ->
                failedIds = failedIds + record.id
                snackbar(context.getString(R.string.transcribe_error_toast, e.message))
            }
            reload()
        }
    }

    val startRecording: () -> Unit = startRecording@{
        try {
            recorder.start()
        } catch (_: Exception) {
            snackbar(context.getString(R.string.record_start_failed))
            return@startRecording
        }
        recordStartAt = System.currentTimeMillis()
        elapsedMs = 0
        isRecording = true
    }

    val stopRecording: () -> Unit = {
        val duration = System.currentTimeMillis() - recordStartAt
        val file = recorder.stop()
        isRecording = false
        if (file == null) {
            snackbar(context.getString(R.string.record_too_short))
        } else {
            val record = store.add(file.absolutePath, duration, recordStartAt)
            reload()
            if (asrSettings.autoTranscribe && asrSettings.isConfigured) {
                transcribe(record)
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startRecording()
        else snackbar(context.getString(R.string.permission_denied))
    }

    val onRecordButton: () -> Unit = {
        when {
            isRecording -> stopRecording()
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED -> startRecording()
            else -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val onPlay: (Record) -> Unit = onPlay@{ record ->
        if (playingId == record.id) {
            stopPlayback()
            return@onPlay
        }
        stopPlayback()
        val player = MediaPlayer()
        try {
            player.setDataSource(record.filePath)
            player.setOnCompletionListener {
                player.release()
                if (playingId == record.id) playingId = null
            }
            player.prepare()
            player.start()
            mediaPlayer = player
            playingId = record.id
        } catch (_: Exception) {
            player.release()
            snackbar(context.getString(R.string.play_failed))
        }
    }

    val onDelete: (Record) -> Unit = { record ->
        if (playingId == record.id) stopPlayback()
        pendingDelete = record
    }

    DisposableEffect(Unit) {
        onDispose {
            recorder.release()
            mediaPlayer?.release()
            transcribeExecutor.shutdownNow()
        }
    }

    LaunchedEffect(isRecording) {
        while (isRecording) {
            elapsedMs = System.currentTimeMillis() - recordStartAt
            delay(200)
        }
    }

    LaunchedEffect(Unit) { reload() }

    AnimatedContent(
        targetState = screen,
        label = "screen",
        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(220)) },
    ) { current ->
        when (current) {
            "settings" -> SettingsScreen(
                asrSettings = asrSettings,
                onBack = { screen = "home" },
                onSaved = { snackbar(context.getString(R.string.saved)) },
            )
            else -> HomeScreen(
                records = records,
                isRecording = isRecording,
                elapsedMs = elapsedMs,
                playingId = playingId,
                transcribingIds = transcribingIds,
                failedIds = failedIds,
                onRecordButton = onRecordButton,
                onOpenSettings = { screen = "settings" },
                onPlay = onPlay,
                onTranscribe = transcribe,
                onDelete = onDelete,
            )
        }
    }

    pendingDelete?.let { record ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.delete_confirm_title)) },
            text = { Text(stringResource(R.string.delete_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    if (playingId == record.id) stopPlayback()
                    store.delete(record.id)
                    File(record.filePath).delete()
                    pendingDelete = null
                    reload()
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun HomeScreen(
    records: List<Record>,
    isRecording: Boolean,
    elapsedMs: Long,
    playingId: Long?,
    transcribingIds: Set<Long>,
    failedIds: Set<Long>,
    onRecordButton: () -> Unit,
    onOpenSettings: () -> Unit,
    onPlay: (Record) -> Unit,
    onTranscribe: (Record) -> Unit,
    onDelete: (Record) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.settings),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            RecordBar(
                isRecording = isRecording,
                elapsedMs = elapsedMs,
                onRecordButton = onRecordButton,
            )
        },
    ) { innerPadding ->
        if (records.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Mic,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.empty_list),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = innerPadding,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(records, key = { it.id }) { record ->
                    RecordCard(
                        record = record,
                        isPlaying = playingId == record.id,
                        isTranscribing = record.id in transcribingIds,
                        isFailed = record.id in failedIds,
                        onPlay = { onPlay(record) },
                        onTranscribe = { onTranscribe(record) },
                        onDelete = { onDelete(record) },
                    )
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

@Composable
private fun RecordBar(isRecording: Boolean, elapsedMs: Long, onRecordButton: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scale",
    )
    val buttonColor by animateFloatAsState(
        targetValue = if (isRecording) 1f else 0f,
        label = "recordColorProgress",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 20.dp, top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedContent(targetState = isRecording, label = "timer") { recording ->
            if (recording) {
                Text(
                    formatDuration(elapsedMs),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        Box(Modifier.size(76.dp), contentAlignment = Alignment.Center) {
            ExtendedFloatingActionButton(
                onClick = onRecordButton,
                shape = CircleShape,
                containerColor = if (buttonColor > 0.5f) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                contentColor = Color.White,
                modifier = Modifier
                    .size(76.dp)
                    .graphicsLayer {
                        val s = if (isRecording) scale else 1f
                        scaleX = s
                        scaleY = s
                    },
            ) {
                if (isRecording) {
                    Icon(
                        Icons.Filled.Stop,
                        contentDescription = stringResource(R.string.record_stop),
                        modifier = Modifier.size(32.dp),
                    )
                } else {
                    Icon(
                        Icons.Filled.Mic,
                        contentDescription = stringResource(R.string.record_start),
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordCard(
    record: Record,
    isPlaying: Boolean,
    isTranscribing: Boolean,
    isFailed: Boolean,
    onPlay: () -> Unit,
    onTranscribe: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by rememberSaveable(record.id) { mutableStateOf(false) }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        dateFormat.format(Date(record.createdAt)),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(R.string.record_duration, formatDuration(record.durationMs)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onPlay) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = stringResource(if (isPlaying) R.string.pause else R.string.play),
                        tint = if (isPlaying) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.delete),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            when {
                isTranscribing -> {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.transcribing),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                isFailed -> {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.transcribe_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.clickable(onClick = onTranscribe),
                    )
                }
                record.transcript != null -> {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        record.transcript,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (expanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { expanded = !expanded },
                    )
                }
                else -> {
                    Text(
                        stringResource(R.string.transcribe),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .clickable(onClick = onTranscribe)
                            .padding(vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    asrSettings: AsrSettings,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    var appKey by rememberSaveable { mutableStateOf(asrSettings.appKey) }
    var accessToken by rememberSaveable { mutableStateOf(asrSettings.accessToken) }
    var resourceId by rememberSaveable { mutableStateOf(asrSettings.resourceId) }
    var autoTranscribe by rememberSaveable { mutableStateOf(asrSettings.autoTranscribe) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text(
                stringResource(R.string.asr_settings_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = appKey,
                onValueChange = { appKey = it },
                label = { Text(stringResource(R.string.asr_app_key_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = accessToken,
                onValueChange = { accessToken = it },
                label = { Text(stringResource(R.string.asr_token_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = resourceId,
                onValueChange = { resourceId = it },
                label = { Text(stringResource(R.string.asr_resource_id_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(R.string.asr_auto_switch),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = autoTranscribe, onCheckedChange = { autoTranscribe = it })
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    asrSettings.appKey = appKey
                    asrSettings.accessToken = accessToken
                    asrSettings.resourceId = resourceId.ifBlank { AsrSettings.DEFAULT_RESOURCE_ID }
                    asrSettings.autoTranscribe = autoTranscribe
                    onSaved()
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.save))
            }
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.asr_help_text),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
