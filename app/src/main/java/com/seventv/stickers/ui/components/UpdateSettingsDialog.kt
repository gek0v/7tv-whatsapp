package com.seventv.stickers.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seventv.stickers.core.updater.AppUpdateManager
import com.seventv.stickers.core.updater.UpdateStatus
import com.seventv.stickers.ui.theme.DarkOnSurface
import com.seventv.stickers.ui.theme.DarkOnSurfaceVariant
import com.seventv.stickers.ui.theme.DarkSurface
import com.seventv.stickers.ui.theme.DarkSurfaceContainer
import com.seventv.stickers.ui.theme.ElectricCyan
import com.seventv.stickers.ui.theme.ErrorRed
import com.seventv.stickers.ui.theme.GlassBorder
import com.seventv.stickers.ui.theme.NeonFuchsia
import com.seventv.stickers.ui.theme.NightDarkPurple
import com.seventv.stickers.ui.theme.WhatsAppGreen

@Composable
fun UpdateSettingsDialog(
    updateManager: AppUpdateManager
) {
    val state by updateManager.state.collectAsStateWithLifecycle()
    if (!state.isSettingsDialogVisible) return

    Dialog(onDismissRequest = { updateManager.closeSettingsDialog() }) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = DarkSurface,
            border = BorderStroke(
                1.dp,
                Brush.linearGradient(
                    listOf(
                        ElectricCyan.copy(alpha = 0.65f),
                        NeonFuchsia.copy(alpha = 0.55f)
                    )
                )
            ),
            shadowElevation = 16.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Actualizaciones",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = DarkOnSurface
                            )
                            Text(
                                text = "Versión instalada v${state.currentVersion}",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = DarkOnSurfaceVariant
                            )
                        }
                    }
                    IconButton(
                        onClick = { updateManager.closeSettingsDialog() },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = DarkOnSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = DarkSurfaceContainer,
                    border = BorderStroke(0.5.dp, GlassBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Comprobación automática",
                                    color = DarkOnSurface,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Buscar nuevas releases en GitHub al abrir la app",
                                    color = DarkOnSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = state.settings.autoCheckEnabled,
                                onCheckedChange = { updateManager.setAutoCheckEnabled(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = NightDarkPurple,
                                    checkedTrackColor = ElectricCyan
                                )
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Descargar solo con Wi-Fi",
                                    color = DarkOnSurface,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Evitar descargas de APK usando datos móviles",
                                    color = DarkOnSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = state.settings.wifiOnlyDownload,
                                onCheckedChange = { updateManager.setWifiOnlyDownload(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = NightDarkPurple,
                                    checkedTrackColor = NeonFuchsia
                                )
                            )
                        }
                    }
                }

                when (val status = state.status) {
                    is UpdateStatus.UpToDate -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = WhatsAppGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Ya tienes la última versión disponible.",
                                color = WhatsAppGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    is UpdateStatus.Error -> {
                        Text(
                            text = status.message,
                            color = ErrorRed,
                            fontSize = 12.sp
                        )
                    }
                    else -> Unit
                }

                val isChecking = state.status is UpdateStatus.Checking
                Button(
                    onClick = { updateManager.checkForUpdatesNow() },
                    enabled = !isChecking,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricCyan,
                        contentColor = NightDarkPurple
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isChecking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = NightDarkPurple
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Buscando…", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Buscar actualizaciones ahora", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}