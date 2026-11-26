package com.seventv.stickers.ui.studio

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.seventv.stickers.core.domain.manager.StickerShareManager
import com.seventv.stickers.core.media.sound.SoundStickerSpecs
import com.seventv.stickers.core.media.sound.StudioCanvasBackground
import com.seventv.stickers.core.media.sound.StudioLoopMode
import com.seventv.stickers.ui.components.CoilSetup
import com.seventv.stickers.ui.theme.DarkBackground
import com.seventv.stickers.ui.theme.DarkOnSurface
import com.seventv.stickers.ui.theme.DarkOnSurfaceVariant
import com.seventv.stickers.ui.theme.DarkSurfaceContainer
import com.seventv.stickers.ui.theme.ElectricCyan
import com.seventv.stickers.ui.theme.ErrorNeonRed
import com.seventv.stickers.ui.theme.GlassBorder
import com.seventv.stickers.ui.theme.GlassSurfaceCard
import com.seventv.stickers.ui.theme.NeonFuchsia
import com.seventv.stickers.ui.theme.RetroViolet
import com.seventv.stickers.ui.theme.SunsetYellow
import com.seventv.stickers.ui.theme.WhatsAppGlow
import com.seventv.stickers.whatsapp.WhatsAppConstants
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale

@Composable
fun StudioScreen(
    viewModel: StudioViewModel,
    shareManager: StickerShareManager,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Gallery visual picker (photo, GIF, WebP)
    val pickVisualLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.onSelectGalleryVisual(uri)
        }
    }

    // Audio / MP4 picker
    val pickAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.onImportAudioUri(uri)
        }
    }

    // Microphone runtime permission launcher
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.onStartVoiceRecording()
        } else {
            Toast.makeText(
                context,
                "Se necesita permiso de micrófono para grabar audio.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // Audio preview MediaPlayer scoped to playback toggle + trim bounds
    val readyAudio = state.audio as? StudioAudioSelection.Ready
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.let {
                try {
                    it.stop()
                } catch (_: Exception) {
                }
                it.release()
            }
            mediaPlayer = null
        }
    }

    LaunchedEffect(
        state.isAudioPreviewPlaying,
        readyAudio?.info?.uri,
        readyAudio?.trimStartMs,
        readyAudio?.trimEndMs
    ) {
        mediaPlayer?.let {
            try {
                it.stop()
            } catch (_: Exception) {
            }
            it.release()
            mediaPlayer = null
        }

        if (state.isAudioPreviewPlaying && readyAudio != null) {
            val mp = MediaPlayer()
            try {
                mp.setDataSource(context, readyAudio.info.uri)
                mp.prepare()
                mp.seekTo(readyAudio.trimStartMs.toInt())
                mp.start()
                mediaPlayer = mp

                while (state.isAudioPreviewPlaying && mp.isPlaying) {
                    if (mp.currentPosition >= readyAudio.trimEndMs) {
                        mp.seekTo(readyAudio.trimStartMs.toInt())
                    }
                    delay(50L)
                }
            } catch (_: Exception) {
                viewModel.onSetAudioPreviewPlaying(false)
                try {
                    mp.release()
                } catch (_: Exception) {
                }
                mediaPlayer = null
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Sticker Studio",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = DarkOnSurface
                )
                Text(
                    text = "Crea stickers propios con sonido para WhatsApp",
                    style = MaterialTheme.typography.bodySmall,
                    color = DarkOnSurfaceVariant
                )
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = ElectricCyan.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.4f))
            ) {
                Text(
                    text = if (state.isVisualAnimated) "Anim · máx 5s" else "Estático · máx 10s",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = ElectricCyan,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }

        // Live 512x512 Sound Sticker Preview Card
        StudioPreviewCard(
            visual = state.visual,
            audio = readyAudio,
            canvasBackground = state.canvasBackground,
            isPlaying = state.isAudioPreviewPlaying,
            onTogglePlay = {
                if (readyAudio != null) {
                    viewModel.onSetAudioPreviewPlaying(!state.isAudioPreviewPlaying)
                }
            }
        )

        // Step 1: Visual Selection
        StudioSectionCard(
            stepNumber = "1",
            title = "Imagen o Emote",
            accent = ElectricCyan
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        pickVisualLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricCyan),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Galería", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                Button(
                    onClick = { viewModel.onOpenEmotePicker() },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RetroViolet,
                        contentColor = DarkBackground
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.EmojiEmotions, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Emote 7TV", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            if (state.visual !is StudioVisualSelection.None) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Seleccionado: ${state.visualDisplayName ?: ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = DarkOnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { viewModel.onClearVisual() }) {
                        Text("Quitar", color = ErrorNeonRed, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Fondo del MP4 (sin canal alfa en vídeo):",
                style = MaterialTheme.typography.labelSmall,
                color = DarkOnSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StudioCanvasBackground.entries.forEach { bg ->
                    val selected = state.canvasBackground == bg
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) ElectricCyan.copy(alpha = 0.2f) else DarkSurfaceContainer,
                        border = BorderStroke(
                            width = if (selected) 1.5.dp else 1.dp,
                            color = if (selected) ElectricCyan else GlassBorder
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { viewModel.onSelectBackground(bg) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(Color(bg.argbColor))
                                    .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = bg.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) ElectricCyan else DarkOnSurfaceVariant,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = state.isVisualAnimated) {
                Column {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Si el audio dura más que la animación:",
                        style = MaterialTheme.typography.labelSmall,
                        color = DarkOnSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StudioLoopMode.entries.forEach { mode ->
                            val selected = state.loopMode == mode
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) ElectricCyan.copy(alpha = 0.2f) else DarkSurfaceContainer,
                                border = BorderStroke(
                                    width = if (selected) 1.5.dp else 1.dp,
                                    color = if (selected) ElectricCyan else GlassBorder
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { viewModel.onSelectLoopMode(mode) }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = when (mode) {
                                            StudioLoopMode.LOOP -> Icons.Default.Repeat
                                            StudioLoopMode.HOLD_LAST_FRAME -> Icons.Default.Pause
                                        },
                                        contentDescription = null,
                                        tint = if (selected) ElectricCyan else DarkOnSurfaceVariant,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = mode.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (selected) ElectricCyan else DarkOnSurfaceVariant,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Step 2: Audio Import / Voice Recorder + Trimmer
        StudioSectionCard(
            stepNumber = "2",
            title = "Pista de Audio",
            accent = NeonFuchsia
        ) {
            if (state.recordingState.isRecording) {
                LiveVoiceRecorderPanel(
                    elapsedMs = state.recordingState.elapsedMs,
                    maxDurationMs = state.maxClipDurationMs,
                    amplitude = state.recordingState.amplitudeNormalized,
                    onStop = { viewModel.onStopVoiceRecording() },
                    onCancel = { viewModel.onCancelVoiceRecording() }
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            pickAudioLauncher.launch(arrayOf("audio/*", "video/mp4", "video/*"))
                        },
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, NeonFuchsia.copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonFuchsia),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.AudioFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Subir Audio", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    Button(
                        onClick = {
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) {
                                viewModel.onStartVoiceRecording()
                            } else {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonFuchsia,
                            contentColor = DarkBackground
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Grabar Voz", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            if (state.isLoadingAudio) {
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    color = NeonFuchsia,
                    trackColor = DarkSurfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            state.audioError?.let { err ->
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ErrorNeonRed.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, ErrorNeonRed.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = ErrorNeonRed, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(err, style = MaterialTheme.typography.bodySmall, color = DarkOnSurface, modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.onDismissAudioError() }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = DarkOnSurfaceVariant, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            if (readyAudio != null && !state.recordingState.isRecording) {
                Spacer(modifier = Modifier.height(12.dp))
                AudioTrimmerPanel(
                    audio = readyAudio,
                    maxClipDurationMs = state.maxClipDurationMs,
                    isPlaying = state.isAudioPreviewPlaying,
                    onTrimChange = { s, e -> viewModel.onTrimRangeChanged(s, e) },
                    onTogglePlay = { viewModel.onSetAudioPreviewPlaying(!state.isAudioPreviewPlaying) },
                    onClearAudio = { viewModel.onClearAudio() }
                )
            }
        }

        // Step 3: Export to MP4 + WhatsApp Import Guide
        StudioSectionCard(
            stepNumber = "3",
            title = "Exportar Sound Sticker",
            accent = WhatsAppGlow
        ) {
            when (val exp = state.exportState) {
                is StudioExportState.Processing -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = exp.statusMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = ElectricCyan
                        )
                        LinearProgressIndicator(
                            progress = { exp.progress.coerceIn(0f, 1f) },
                            color = WhatsAppGlow,
                            trackColor = DarkSurfaceContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                    }
                }

                is StudioExportState.Success -> {
                    val res = exp.result
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = WhatsAppGlow.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, WhatsAppGlow.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = WhatsAppGlow)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (res.savedToGallery) {
                                        "Guardado en Galería (Movies/7TVStickers)"
                                    } else {
                                        "MP4 generado y listo para compartir"
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = WhatsAppGlow
                                )
                            }

                            Text(
                                text = "Duración: ${formatSec(res.durationMs)} · Tamaño: ${formatKb(res.fileSizeBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = DarkOnSurfaceVariant
                            )

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = DarkBackground.copy(alpha = 0.7f),
                                border = BorderStroke(1.dp, GlassBorder)
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "Cómo enviarlo en WhatsApp:",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = SunsetYellow
                                    )
                                    Text(
                                        text = "1. Abre cualquier chat en WhatsApp\n2. Ve a Stickers → Crear (+)\n3. Elige este vídeo en Galería (conserva el sonido)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = DarkOnSurface
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val pm = context.packageManager
                                        val waIntent = pm.getLaunchIntentForPackage(WhatsAppConstants.PACKAGE_WHATSAPP)
                                            ?: pm.getLaunchIntentForPackage(WhatsAppConstants.PACKAGE_WHATSAPP_SMB)
                                        if (waIntent != null) {
                                            waIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            context.startActivity(waIntent)
                                        } else {
                                            Toast.makeText(context, "WhatsApp no está instalado", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = WhatsAppGlow,
                                        contentColor = DarkBackground
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Abrir WhatsApp", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }

                                OutlinedButton(
                                    onClick = {
                                        val mp4File = File(res.shareFilePath)
                                        val uri: Uri = try {
                                            FileProvider.getUriForFile(
                                                context,
                                                StickerShareManager.FILE_PROVIDER_AUTHORITY,
                                                mp4File
                                            )
                                        } catch (_: Exception) {
                                            res.galleryUri
                                        }
                                        shareManager.launchShare(
                                            context = context,
                                            uri = uri,
                                            mimeType = "video/mp4",
                                            chooserTitle = "Compartir Sound Sticker (MP4)"
                                        )
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, ElectricCyan),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricCyan),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Compartir", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }

                is StudioExportState.Error -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = ErrorNeonRed.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, ErrorNeonRed.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = exp.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = DarkOnSurface,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                        GenerateCtaButton(
                            enabled = state.canGenerate,
                            onClick = { viewModel.onGenerateSoundSticker() }
                        )
                    }
                }

                StudioExportState.Idle -> {
                    GenerateCtaButton(
                        enabled = state.canGenerate,
                        onClick = { viewModel.onGenerateSoundSticker() }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    if (state.isEmotePickerOpen) {
        StudioEmotePickerModal(
            query = state.emoteSearchQuery,
            results = state.emotePickerResults,
            isLoading = state.isSearchingEmotes,
            onQueryChange = { viewModel.onEmoteSearchQueryChanged(it) },
            onSelectEmote = { viewModel.onSelectEmoteVisual(it) },
            onDismiss = { viewModel.onDismissEmotePicker() }
        )
    }
}

@Composable
private fun StudioPreviewCard(
    visual: StudioVisualSelection,
    audio: StudioAudioSelection.Ready?,
    canvasBackground: StudioCanvasBackground,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit
) {
    val context = LocalContext.current
    val previewModel: Any? = when (visual) {
        StudioVisualSelection.None -> null
        is StudioVisualSelection.EmoteVisual -> visual.emote.previewUrl
        is StudioVisualSelection.GalleryVisual -> visual.uri
        is StudioVisualSelection.LocalFileVisual -> File(visual.filePath)
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(canvasBackground.argbColor),
            border = BorderStroke(
                1.5.dp,
                Brush.linearGradient(listOf(ElectricCyan, NeonFuchsia, RetroViolet))
            ),
            shadowElevation = 10.dp,
            modifier = Modifier
                .size(210.dp)
                .clip(RoundedCornerShape(24.dp))
                .clickable(enabled = audio != null) { onTogglePlay() }
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (previewModel != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(previewModel)
                            .apply {
                                if (visual is StudioVisualSelection.EmoteVisual) {
                                    memoryCacheKey(CoilSetup.emotePreviewCacheKey(visual.emote.id))
                                }
                            }
                            .crossfade(true)
                            .build(),
                        contentDescription = "Vista previa del sticker",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp)
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MovieCreation,
                            contentDescription = null,
                            tint = ElectricCyan.copy(alpha = 0.7f),
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Elige una imagen o emote para empezar",
                            style = MaterialTheme.typography.bodySmall,
                            color = DarkOnSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // WhatsApp Sound Sticker icon badge preview
                if (audio != null) {
                    Surface(
                        shape = CircleShape,
                        color = DarkBackground.copy(alpha = 0.85f),
                        border = BorderStroke(1.dp, if (isPlaying) WhatsAppGlow else ElectricCyan),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                            .size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Escuchar",
                                tint = if (isPlaying) WhatsAppGlow else ElectricCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioSectionCard(
    stepNumber: String,
    title: String,
    accent: Color,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = GlassSurfaceCard),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = accent.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, accent),
                    modifier = Modifier.size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = stepNumber,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = accent
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = DarkOnSurface
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun LiveVoiceRecorderPanel(
    elapsedMs: Long,
    maxDurationMs: Long,
    amplitude: Float,
    onStop: () -> Unit,
    onCancel: () -> Unit
) {
    val pulseTransition = rememberInfiniteTransition(label = "recPulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "recPulseAlpha"
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = ErrorNeonRed.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, ErrorNeonRed.copy(alpha = pulseAlpha))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(ErrorNeonRed.copy(alpha = pulseAlpha))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Grabando voz...",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = DarkOnSurface
                    )
                }
                Text(
                    text = "${formatSec(elapsedMs)} / ${formatSec(maxDurationMs)}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = ErrorNeonRed
                )
            }

            // Live VU Amplitude Bar
            LinearProgressIndicator(
                progress = { amplitude.coerceIn(0.05f, 1f) },
                color = NeonFuchsia,
                trackColor = DarkSurfaceContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, DarkOnSurfaceVariant),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancelar", color = DarkOnSurfaceVariant)
                }
                Button(
                    onClick = onStop,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ErrorNeonRed,
                        contentColor = DarkOnSurface
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Detener", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun AudioTrimmerPanel(
    audio: StudioAudioSelection.Ready,
    maxClipDurationMs: Long,
    isPlaying: Boolean,
    onTrimChange: (Long, Long) -> Unit,
    onTogglePlay: () -> Unit,
    onClearAudio: () -> Unit
) {
    val totalMs = audio.info.durationMs.coerceAtLeast(1L)
    val startFraction = (audio.trimStartMs.toFloat() / totalMs).coerceIn(0f, 1f)
    val endFraction = (audio.trimEndMs.toFloat() / totalMs).coerceIn(startFraction, 1f)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = DarkSurfaceContainer,
        border = BorderStroke(1.dp, GlassBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.GraphicEq, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (audio.isFromMicrophone) "Nota de voz grabada" else audio.info.displayName,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = DarkOnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onClearAudio, modifier = Modifier.size(26.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Quitar audio", tint = ErrorNeonRed, modifier = Modifier.size(16.dp))
                }
            }

            // Waveform Bars Canvas highlighting active trim window
            val bars = audio.info.waveformBars
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                if (bars.isEmpty()) return@Canvas
                val barCount = bars.size
                val slotWidth = size.width / barCount
                val barWidth = (slotWidth * 0.58f).coerceAtLeast(2f)
                bars.forEachIndexed { idx, amp ->
                    val frac = idx.toFloat() / barCount.toFloat()
                    val inRange = frac >= startFraction && frac <= endFraction
                    val barHeight = (size.height * amp.coerceIn(0.15f, 1f))
                    val top = (size.height - barHeight) / 2f
                    val x = idx * slotWidth + (slotWidth - barWidth) / 2f
                    drawRoundRect(
                        color = if (inRange) ElectricCyan else DarkOnSurfaceVariant.copy(alpha = 0.28f),
                        topLeft = Offset(x, top),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                    )
                }
            }

            if (totalMs > SoundStickerSpecs.MIN_CLIP_DURATION_MS) {
                RangeSlider(
                    value = audio.trimStartMs.toFloat()..audio.trimEndMs.toFloat(),
                    onValueChange = { range ->
                        onTrimChange(range.start.toLong(), range.endInclusive.toLong())
                    },
                    valueRange = 0f..totalMs.toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = NeonFuchsia,
                        activeTrackColor = ElectricCyan,
                        inactiveTrackColor = GlassBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${formatSec(audio.trimStartMs)} – ${formatSec(audio.trimEndMs)} (${formatSec(audio.clipDurationMs)} / máx ${formatSec(maxClipDurationMs)})",
                    style = MaterialTheme.typography.labelSmall,
                    color = SunsetYellow
                )
                OutlinedButton(
                    onClick = onTogglePlay,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, if (isPlaying) WhatsAppGlow else ElectricCyan),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = if (isPlaying) WhatsAppGlow else ElectricCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isPlaying) "Pausar" else "Escuchar",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isPlaying) WhatsAppGlow else ElectricCyan
                    )
                }
            }
        }
    }
}

@Composable
private fun GenerateCtaButton(
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = WhatsAppGlow,
            contentColor = DarkBackground,
            disabledContainerColor = DarkSurfaceContainer,
            disabledContentColor = DarkOnSurfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        Icon(Icons.Default.MovieCreation, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Crear Sticker con Audio (MP4)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

private fun formatSec(ms: Long): String {
    return String.format(Locale.US, "%.1fs", ms / 1000f)
}

private fun formatKb(bytes: Long): String {
    return if (bytes >= 1024 * 1024) {
        String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
    } else {
        String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    }
}
