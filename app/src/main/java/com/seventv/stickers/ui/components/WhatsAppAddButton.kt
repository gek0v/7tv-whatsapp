package com.seventv.stickers.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.seventv.stickers.ui.theme.ElectricCyan
import com.seventv.stickers.ui.theme.NeonFuchsia
import com.seventv.stickers.ui.theme.SunsetYellow
import com.seventv.stickers.ui.theme.WhatsAppGlow
import com.seventv.stickers.ui.theme.WhatsAppGreen

data class WhatsAppAddButtonState(
    val isWhitelisted: Boolean,
    val isLoading: Boolean = false,
    val hasPendingChanges: Boolean = false
) {
    val buttonText: String
        get() = when {
            !isWhitelisted -> "Añadir a WhatsApp"
            hasPendingChanges -> "Actualizar en WhatsApp"
            else -> "Añadido a WhatsApp"
        }

    val isClickable: Boolean
        get() = !isLoading

    val showProgress: Boolean
        get() = isLoading

    val showCheckmark: Boolean
        get() = !isLoading && isWhitelisted && !hasPendingChanges

    val showUpdateIcon: Boolean
        get() = !isLoading && isWhitelisted && hasPendingChanges
}

object WhatsAppAddButtonDefaults {
    fun buttonText(isWhitelisted: Boolean, hasPendingChanges: Boolean = false): String = when {
        !isWhitelisted -> "Añadir a WhatsApp"
        hasPendingChanges -> "Actualizar en WhatsApp"
        else -> "Añadido a WhatsApp"
    }

    fun evaluateState(
        isWhitelisted: Boolean,
        isLoading: Boolean,
        hasPendingChanges: Boolean = false
    ): WhatsAppAddButtonState =
        WhatsAppAddButtonState(
            isWhitelisted = isWhitelisted,
            isLoading = isLoading,
            hasPendingChanges = hasPendingChanges
        )
}

/**
 * Vaporwave + Glassmorphism WhatsApp CTA button with mint / electric cyan neon gradient,
 * glowing pill shape, and high-contrast styling.
 */
@Composable
fun WhatsAppAddButton(
    isWhitelisted: Boolean,
    isLoading: Boolean = false,
    hasPendingChanges: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state = WhatsAppAddButtonDefaults.evaluateState(isWhitelisted, isLoading, hasPendingChanges)
    val buttonShape = RoundedCornerShape(24.dp)
    val isPendingUpdate = isWhitelisted && hasPendingChanges

    val gradientBrush = when {
        isPendingUpdate -> {
            Brush.horizontalGradient(
                listOf(
                    SunsetYellow,
                    NeonFuchsia
                )
            )
        }
        isWhitelisted -> {
            Brush.horizontalGradient(
                listOf(
                    Color(0x3505FFA1),
                    Color(0x3501CDFE)
                )
            )
        }
        else -> {
            Brush.horizontalGradient(
                listOf(
                    WhatsAppGlow,
                    ElectricCyan
                )
            )
        }
    }

    val contentColor = if (isWhitelisted && !isPendingUpdate) WhatsAppGlow else Color(0xFF0F081D)

    val borderStroke = when {
        isPendingUpdate -> androidx.compose.foundation.BorderStroke(1.5.dp, SunsetYellow)
        isWhitelisted -> androidx.compose.foundation.BorderStroke(1.5.dp, WhatsAppGlow)
        else -> null
    }

    val shadowElevation = if (isWhitelisted && !isPendingUpdate) 2.dp else 8.dp

    Surface(
        modifier = modifier
            .clip(buttonShape)
            .clickable(enabled = state.isClickable, onClick = onClick),
        shape = buttonShape,
        color = Color.Transparent,
        border = borderStroke,
        shadowElevation = shadowElevation
    ) {
        Box(
            modifier = Modifier
                .background(gradientBrush)
                .padding(horizontal = 24.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.showProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = contentColor,
                        strokeWidth = 2.5.dp
                    )
                } else {
                    if (state.showCheckmark) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    } else if (state.showUpdateIcon) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = state.buttonText,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )
                }
            }
        }
    }
}
