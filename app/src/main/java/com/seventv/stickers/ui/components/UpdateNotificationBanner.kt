package com.seventv.stickers.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seventv.stickers.core.updater.AppUpdateManager
import com.seventv.stickers.core.updater.UpdateStatus
import com.seventv.stickers.ui.theme.DarkOnSurface
import com.seventv.stickers.ui.theme.DarkOnSurfaceVariant
import com.seventv.stickers.ui.theme.DarkSurfaceContainerHigh
import com.seventv.stickers.ui.theme.ElectricCyan
import com.seventv.stickers.ui.theme.ErrorRed
import com.seventv.stickers.ui.theme.GlassSurfaceCard
import com.seventv.stickers.ui.theme.NeonFuchsia
import com.seventv.stickers.ui.theme.SunsetYellow
import com.seventv.stickers.ui.theme.WhatsAppGreen

@Composable
fun UpdateNotificationBanner(
    updateManager: AppUpdateManager,
    modifier: Modifier = Modifier
) {
    val state by updateManager.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val isVisible = !state.isBannerDismissedInSession && when (state.status) {
        is UpdateStatus.Available,
        is UpdateStatus.Downloading,
        is UpdateStatus.ReadyToInstall,
        is UpdateStatus.AwaitingInstallPermission -> true
        is UpdateStatus.Error -> (state.status as UpdateStatus.Error).lastAvailableInfo != null
        else -> false
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        val borderBrush = when (state.status) {
            is UpdateStatus.ReadyToInstall -> Brush.horizontalGradient(listOf(WhatsAppGreen, ElectricCyan))
            is UpdateStatus.Error -> Brush.horizontalGradient(listOf(ErrorRed, NeonFuchsia))
            else -> Brush.horizontalGradient(listOf(SunsetYellow.copy(alpha = 0.8f), ElectricCyan.copy(alpha = 0.85f)))
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { updateManager.openChangelogDialog() },
            shape = RoundedCornerShape(16.dp),
            color = GlassSurfaceCard,
            border = BorderStroke(1.dp, borderBrush),
            shadowElevation = 8.dp
        ) {
            when (val status = state.status) {
                is UpdateStatus.Available -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = SunsetYellow.copy(alpha = 0.16f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SystemUpdate,
                                    contentDescription = null,
                                    tint = SunsetYellow,
                                    modifier = Modifier.padding(7.dp).size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Nueva versión ${status.info.latestVersion} disponible",
                                    color = DarkOnSurface,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Toca para ver novedades y actualizar",
                                    color = DarkOnSurfaceVariant,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { updateManager.openChangelogDialog() }) {
                                Text(
                                    text = "Ver novedades",
                                    color = ElectricCyan,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            IconButton(
                                onClick = { updateManager.dismissBanner() },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cerrar aviso",
                                    tint = DarkOnSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                is UpdateStatus.Downloading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp)
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
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = null,
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Descargando v${status.info.latestVersion}… ${status.progressPercent}%",
                                    color = DarkOnSurface,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            TextButton(onClick = { updateManager.cancelDownload() }) {
                                Text(
                                    text = "Cancelar",
                                    color = NeonFuchsia,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { (status.progressPercent / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = ElectricCyan,
                            trackColor = DarkSurfaceContainerHigh
                        )
                    }
                }

                is UpdateStatus.ReadyToInstall,
                is UpdateStatus.AwaitingInstallPermission -> {
                    val version = when (status) {
                        is UpdateStatus.ReadyToInstall -> status.info.latestVersion
                        is UpdateStatus.AwaitingInstallPermission -> status.info.latestVersion
                        else -> ""
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = WhatsAppGreen,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Actualización v$version lista",
                                    color = DarkOnSurface,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "APK verificado listo para instalar",
                                    color = DarkOnSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        TextButton(onClick = { updateManager.installDownloadedApk(context) }) {
                            Text(
                                text = "Instalar",
                                color = WhatsAppGreen,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                is UpdateStatus.Error -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = null,
                                tint = ErrorRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = status.message,
                                color = DarkOnSurface,
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        status.lastAvailableInfo?.let { info ->
                            TextButton(onClick = { updateManager.startDownload(info) }) {
                                Text(
                                    text = "Reintentar",
                                    color = ElectricCyan,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        IconButton(
                            onClick = { updateManager.dismissBanner() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cerrar",
                                tint = DarkOnSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                else -> Unit
            }
        }
    }
}