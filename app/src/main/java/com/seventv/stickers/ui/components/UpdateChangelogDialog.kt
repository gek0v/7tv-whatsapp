package com.seventv.stickers.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seventv.stickers.core.updater.AppUpdateInfo
import com.seventv.stickers.core.updater.AppUpdateManager
import com.seventv.stickers.core.updater.SemVer
import com.seventv.stickers.core.updater.UpdateStatus
import com.seventv.stickers.ui.theme.DarkOnSurface
import com.seventv.stickers.ui.theme.DarkOnSurfaceVariant
import com.seventv.stickers.ui.theme.DarkSurface
import com.seventv.stickers.ui.theme.DarkSurfaceContainer
import com.seventv.stickers.ui.theme.DarkSurfaceContainerHigh
import com.seventv.stickers.ui.theme.ElectricCyan
import com.seventv.stickers.ui.theme.ErrorRed
import com.seventv.stickers.ui.theme.GlassBorder
import com.seventv.stickers.ui.theme.NeonFuchsia
import com.seventv.stickers.ui.theme.NightDarkPurple
import com.seventv.stickers.ui.theme.SunsetYellow
import com.seventv.stickers.ui.theme.WhatsAppGreen

@Composable
fun UpdateChangelogDialog(
    updateManager: AppUpdateManager
) {
    val state by updateManager.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    if (!state.isChangelogDialogVisible) return

    val info: AppUpdateInfo = when (val status = state.status) {
        is UpdateStatus.Available -> status.info
        is UpdateStatus.Downloading -> status.info
        is UpdateStatus.ReadyToInstall -> status.info
        is UpdateStatus.AwaitingInstallPermission -> status.info
        is UpdateStatus.Error -> status.lastAvailableInfo ?: return
        else -> return
    }

    if (!SemVer.isUpdateAvailable(state.currentVersion, info.latestVersion)) {
        return
    }

    Dialog(onDismissRequest = { updateManager.closeChangelogDialog() }) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = DarkSurface,
            border = BorderStroke(
                1.dp,
                Brush.linearGradient(
                    listOf(
                        ElectricCyan.copy(alpha = 0.7f),
                        NeonFuchsia.copy(alpha = 0.65f)
                    )
                )
            ),
            shadowElevation = 18.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
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
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = ElectricCyan.copy(alpha = 0.16f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier.padding(8.dp).size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = info.releaseTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = DarkOnSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = SunsetYellow.copy(alpha = 0.15f),
                                border = BorderStroke(0.5.dp, SunsetYellow.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = "v${info.currentVersion} → v${info.latestVersion}",
                                    color = SunsetYellow,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = { updateManager.closeChangelogDialog() },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = DarkOnSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (info.apkSizeBytes > 0L) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = DarkSurfaceContainer,
                            border = BorderStroke(0.5.dp, GlassBorder)
                        ) {
                            Text(
                                text = formatBytes(info.apkSizeBytes),
                                color = DarkOnSurfaceVariant,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    if (!info.expectedSha256.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = WhatsAppGreen.copy(alpha = 0.12f),
                            border = BorderStroke(0.5.dp, WhatsAppGreen.copy(alpha = 0.45f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = WhatsAppGreen,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "SHA-256",
                                    color = WhatsAppGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = DarkSurfaceContainer,
                    border = BorderStroke(0.5.dp, GlassBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.dp, max = 240.dp)
                ) {
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .verticalScroll(scrollState)
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val lines = info.changelogMarkdown
                            .ifBlank { "Mejoras de rendimiento, estabilidad y correcciones generales." }
                            .lines()
                            .filter { it.isNotBlank() }

                        lines.forEach { rawLine ->
                            val clean = rawLine.trim()
                            when {
                                clean.startsWith("### ") || clean.startsWith("## ") || clean.startsWith("# ") -> {
                                    Text(
                                        text = clean.trimStart('#').trim(),
                                        color = ElectricCyan,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                clean.startsWith("- ") || clean.startsWith("* ") -> {
                                    Text(
                                        text = "• ${clean.drop(2).replace("**", "")}",
                                        color = DarkOnSurface,
                                        fontSize = 12.sp,
                                        lineHeight = 17.sp
                                    )
                                }
                                else -> {
                                    Text(
                                        text = clean.replace("**", ""),
                                        color = DarkOnSurfaceVariant,
                                        fontSize = 12.sp,
                                        lineHeight = 17.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                when (val status = state.status) {
                    is UpdateStatus.Downloading -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Descargando paquete APK…",
                                    color = ElectricCyan,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${status.progressPercent}%",
                                    color = DarkOnSurface,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { (status.progressPercent / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(7.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = ElectricCyan,
                                trackColor = DarkSurfaceContainerHigh
                            )
                            if (status.totalBytes > 0L) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "${formatBytes(status.downloadedBytes)} / ${formatBytes(status.totalBytes)}",
                                    color = DarkOnSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = { updateManager.cancelDownload() },
                                border = BorderStroke(1.dp, NeonFuchsia.copy(alpha = 0.6f)),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Cancelar descarga", color = NeonFuchsia, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    is UpdateStatus.ReadyToInstall -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = WhatsAppGreen.copy(alpha = 0.14f),
                                border = BorderStroke(1.dp, WhatsAppGreen.copy(alpha = 0.45f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.VerifiedUser,
                                        contentDescription = null,
                                        tint = WhatsAppGreen,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (status.verifiedSha256) {
                                            "Descarga completada y verificada con SHA-256."
                                        } else {
                                            "Descarga completada. Lista para instalar."
                                        },
                                        color = DarkOnSurface,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = { updateManager.installDownloadedApk(context) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = WhatsAppGreen,
                                    contentColor = NightDarkPurple
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Instalar actualización", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    is UpdateStatus.AwaitingInstallPermission -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = SunsetYellow.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, SunsetYellow.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = null,
                                        tint = SunsetYellow,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Activa \"Permitir de esta fuente\" en Ajustes de Android para instalar la actualización.",
                                        color = DarkOnSurface,
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = { updateManager.installDownloadedApk(context) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SunsetYellow,
                                    contentColor = NightDarkPurple
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Abrir Ajustes / Reintentar", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    is UpdateStatus.Error -> {
                        Text(text = status.message, color = ErrorRed, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            TextButton(
                                onClick = { updateManager.remindLater(24L) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Recordar más tarde", color = DarkOnSurfaceVariant)
                            }
                            Button(
                                onClick = { updateManager.startDownload(info) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ElectricCyan,
                                    contentColor = NightDarkPurple
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Reintentar", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    else -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            TextButton(
                                onClick = { updateManager.remindLater(24L) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "Recordar más tarde",
                                    color = DarkOnSurfaceVariant,
                                    fontSize = 12.sp
                                )
                            }
                            Button(
                                onClick = { updateManager.startDownload(info) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ElectricCyan,
                                    contentColor = NightDarkPurple
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1.1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Actualizar ahora", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1.0 -> "%.1f MB".format(mb)
        kb >= 1.0 -> "%.0f KB".format(kb)
        else -> "$bytes B"
    }
}