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
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.banana.shotrecord.audio.AudioRecorder
import com.banana.shotrecord.data.AsrSettings
import com.banana.shotrecord.data.Record
import com.banana.shotrecord.data.RecordStore
import com.banana.shotrecord.network.DoubaoAsrClient
import com.banana.shotrecord.ui.LocalBrand
import com.banana.shotrecord.ui.ShotRecordTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.min
import kotlin.random.Random
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

private val dateFormat = SimpleDateFormat("M月d日 HH:mm", Locale.getDefault())

/** 大号细字重计时数字:等宽数字避免跳动 */
private val TimerStyle = TextStyle(
    fontSize = 64.sp,
    fontWeight = FontWeight.ExtraLight,
    letterSpacing = 2.sp,
    fontFeatureSettings = "tnum",
)

@Composable
fun ShotRecordApp() {
    val context = LocalContext.current
    val store = remember { RecordStore(context) }
    val asrSettings = remember { AsrSettings(context) }
    val recorder = remember { AudioRecorder(context) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val transcribeExecutor = remember { Executors.newSingleThreadExecutor() }

    var screen by rememberSaveable { mutableStateOf("home") }
    var records by remember { mutableStateOf<List<Record>>(emptyList()) }
    var isRecording by remember { mutableStateOf(false) }
    var recordStartAt by remember { mutableLongStateOf(0L) }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    var amplitudes by remember { mutableStateOf<List<Float>>(emptyList()) }
    var transcribingIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var failedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var playingId by remember { mutableStateOf<Long?>(null) }
    var playbackProgress by remember { mutableFloatStateOf(0f) }
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
        playbackProgress = 0f
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
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        recordStartAt = System.currentTimeMillis()
        elapsedMs = 0
        amplitudes = emptyList()
        isRecording = true
    }

    val stopRecording: () -> Unit = {
        val duration = System.currentTimeMillis() - recordStartAt
        val file = recorder.stop()
        isRecording = false
        amplitudes = emptyList()
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
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
                if (playingId == record.id) {
                    playingId = null
                    playbackProgress = 0f
                }
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

    // 计时 + 实时声浪采样(80ms 一格,保留 28 根)
    LaunchedEffect(isRecording) {
        while (isRecording) {
            elapsedMs = System.currentTimeMillis() - recordStartAt
            val amp = recorder.amplitude()
            val normalized = 0.12f + min(1f, amp) * 0.88f
            amplitudes = (amplitudes + normalized).takeLast(28)
            delay(80)
        }
    }

    // 播放进度采样(驱动波形染色与进度环)
    LaunchedEffect(playingId) {
        while (playingId != null) {
            val player = mediaPlayer ?: break
            playbackProgress = try {
                val duration = player.duration
                if (duration > 0) (player.currentPosition.toFloat() / duration) else 0f
            } catch (_: IllegalStateException) {
                break
            }
            delay(100)
        }
        playbackProgress = 0f
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
                amplitudes = amplitudes,
                playingId = playingId,
                playbackProgress = playbackProgress,
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
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
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
    amplitudes: List<Float>,
    playingId: Long?,
    playbackProgress: Float,
    transcribingIds: Set<Long>,
    failedIds: Set<Long>,
    onRecordButton: () -> Unit,
    onOpenSettings: () -> Unit,
    onPlay: (Record) -> Unit,
    onTranscribe: (Record) -> Unit,
    onDelete: (Record) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            RecordBar(
                isRecording = isRecording,
                elapsedMs = elapsedMs,
                amplitudes = amplitudes,
                onRecordButton = onRecordButton,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding(),
        ) {
            // 编辑感头部:大标题 + 标语 + 玻璃圆设置钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        stringResource(R.string.home_tagline),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, LocalBrand.current.cardBorder, CircleShape)
                        .clickable(onClick = onOpenSettings),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = stringResource(R.string.settings),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            if (records.isEmpty()) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier.size(120.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier
                                    .size(120.dp)
                                    .background(
                                        Brush.radialGradient(
                                            listOf(
                                                LocalBrand.current.glowColor.copy(alpha = 0.22f),
                                                Color.Transparent,
                                            )
                                        ),
                                        CircleShape,
                                    ),
                            )
                            Icon(
                                Icons.Filled.Mic,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Spacer(Modifier.height(20.dp))
                        Text(
                            stringResource(R.string.empty_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.empty_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 20.dp, end = 20.dp, bottom = 16.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(records, key = { it.id }) { record ->
                        RecordCard(
                            modifier = Modifier.animateItem(),
                            record = record,
                            isPlaying = playingId == record.id,
                            playbackProgress = playbackProgress,
                            isTranscribing = record.id in transcribingIds,
                            isFailed = record.id in failedIds,
                            onPlay = { onPlay(record) },
                            onTranscribe = { onTranscribe(record) },
                            onDelete = { onDelete(record) },
                        )
                    }
                }
            }
        }
    }
}

/** 底部录音区:实时声浪 + 计时 + 渐变光环按钮 */
@Composable
private fun RecordBar(
    isRecording: Boolean,
    elapsedMs: Long,
    amplitudes: List<Float>,
    onRecordButton: () -> Unit,
) {
    val brand = LocalBrand.current
    val pulse = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by pulse.animateFloat(
        initialValue = 0.55f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
        ),
        label = "pulseAlpha",
    )
    val pulseScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
        ),
        label = "pulseScale",
    )

    // 按压时按钮轻微缩小,松手回弹
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val buttonScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "buttonScale",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedContent(targetState = isRecording, label = "timerZone") { recording ->
            if (recording) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // 实时声浪
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.height(28.dp),
                    ) {
                        val bars = if (amplitudes.size < 28) {
                            amplitudes + List(28 - amplitudes.size) { 0.12f }
                        } else {
                            amplitudes
                        }
                        bars.forEach { amp ->
                            Box(
                                Modifier
                                    .padding(horizontal = 2.dp)
                                    .size(width = 3.dp, height = (4.dp.value + 24.dp.value * amp).dp)
                                    .background(
                                        MaterialTheme.colorScheme.primary,
                                        RoundedCornerShape(2.dp),
                                    ),
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        formatDuration(elapsedMs),
                        style = TimerStyle,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.status_recording),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Spacer(Modifier.height(96.dp))
            }
        }

        Box(contentAlignment = Alignment.Center) {
            // 录音时的呼吸光环
            if (isRecording) {
                Box(
                    Modifier
                        .size(96.dp)
                        .graphicsLayer {
                            scaleX = pulseScale
                            scaleY = pulseScale
                            this.alpha = pulseAlpha
                        }
                        .background(brand.recordGradient, CircleShape),
                )
            } else {
                // 待机时的品牌光晕
                Box(
                    Modifier
                        .size(110.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(brand.glowColor.copy(alpha = 0.25f), Color.Transparent)
                            ),
                            CircleShape,
                        ),
                )
            }
            // 渐变主按钮
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .scale(buttonScale)
                    .background(
                        if (isRecording) brand.recordGradient else brand.brandGradient,
                        CircleShape,
                    )
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = onRecordButton,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Crossfade(
                    targetState = isRecording,
                    animationSpec = tween(180),
                    label = "recordIcon",
                ) { recording ->
                    Icon(
                        if (recording) Icons.Filled.Stop else Icons.Filled.Mic,
                        contentDescription = stringResource(
                            if (recording) R.string.record_stop else R.string.record_start
                        ),
                        tint = Color.White,
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
        }
    }
}

/** 滑动删除包裹的玻璃质感录音卡片;播放时波形按进度染色 */
@Composable
private fun RecordCard(
    modifier: Modifier,
    record: Record,
    isPlaying: Boolean,
    playbackProgress: Float,
    isTranscribing: Boolean,
    isFailed: Boolean,
    onPlay: () -> Unit,
    onTranscribe: () -> Unit,
    onDelete: () -> Unit,
) {
    val brand = LocalBrand.current
    val haptics = LocalHapticFeedback.current
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onDelete()
            }
            // 始终回弹:真正的删除由确认对话框完成
            false
        },
        positionalThreshold = { total -> total * 0.55f },
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(22.dp))
                    .background(brand.recordGradient),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = Color.White,
                    modifier = Modifier.padding(horizontal = 28.dp),
                )
            }
        },
    ) {
        RecordCardContent(
            record = record,
            isPlaying = isPlaying,
            playbackProgress = playbackProgress,
            isTranscribing = isTranscribing,
            isFailed = isFailed,
            onPlay = onPlay,
            onTranscribe = onTranscribe,
            onDelete = onDelete,
        )
    }
}

@Composable
private fun RecordCardContent(
    record: Record,
    isPlaying: Boolean,
    playbackProgress: Float,
    isTranscribing: Boolean,
    isFailed: Boolean,
    onPlay: () -> Unit,
    onTranscribe: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by rememberSaveable(record.id) { mutableStateOf(false) }
    val brand = LocalBrand.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(22.dp))
            .border(1.dp, brand.cardBorder, RoundedCornerShape(22.dp))
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    dateFormat.format(Date(record.createdAt)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    stringResource(R.string.record_duration, formatDuration(record.durationMs)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // 装饰波形:播放时按进度染色
            WaveformDecor(
                id = record.id,
                progress = if (isPlaying) playbackProgress else 0f,
                playedColor = MaterialTheme.colorScheme.primary,
                idleColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
            )
            Spacer(Modifier.width(2.dp))
            // 播放按钮:播放中显示圆形进度环
            Box(contentAlignment = Alignment.Center) {
                if (isPlaying) {
                    val ringTrack = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    val ringFill = MaterialTheme.colorScheme.primary
                    Canvas(Modifier.size(44.dp)) {
                        val stroke = 2.dp.toPx()
                        drawArc(
                            color = ringTrack,
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(stroke, cap = StrokeCap.Round),
                        )
                        drawArc(
                            color = ringFill,
                            startAngle = -90f,
                            sweepAngle = 360f * playbackProgress,
                            useCenter = false,
                            style = Stroke(stroke, cap = StrokeCap.Round),
                        )
                    }
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
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
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
                Spacer(Modifier.height(10.dp))
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
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.transcribe),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable(onClick = onTranscribe),
                )
            }
        }
    }
}

/** 由 id 确定性生成的一组装饰波形条;progress>0 时按播放进度染色 */
@Composable
private fun WaveformDecor(
    id: Long,
    progress: Float,
    playedColor: Color,
    idleColor: Color,
) {
    val bars = remember(id) {
        val r = Random(id * 31 + 7)
        List(18) { 0.18f + r.nextFloat() * 0.82f }
    }
    val playedCount = (progress * bars.size).toInt()
    Row(verticalAlignment = Alignment.CenterVertically) {
        bars.forEachIndexed { index, amp ->
            Box(
                Modifier
                    .padding(horizontal = 1.dp)
                    .size(width = 2.dp, height = (3.dp.value + 14.dp.value * amp).dp)
                    .background(
                        if (index < playedCount) playedColor else idleColor,
                        RoundedCornerShape(1.dp),
                    ),
            )
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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
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
                .padding(horizontal = 20.dp),
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
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = accessToken,
                onValueChange = { accessToken = it },
                label = { Text(stringResource(R.string.asr_token_hint)) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = resourceId,
                onValueChange = { resourceId = it },
                label = { Text(stringResource(R.string.asr_resource_id_hint)) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
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
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
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
