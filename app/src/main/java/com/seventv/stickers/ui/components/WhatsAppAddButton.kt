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
import com.seventv.stickers.ui.theme.WhatsAppGlow
import com.seventv.stickers.ui.theme.WhatsAppGreen

data class WhatsAppAddButtonState(
    val isWhitelisted: Boolean,
    val isLoading: Boolean = false
) {
    val buttonText: String
        get() = if (isWhitelisted) "Añadido a WhatsApp" else "Añadir a WhatsApp"

    val isClickable: Boolean
        get() = !isLoading

    val showProgress: Boolean
        get() = isLoading

    val showCheckmark: Boolean
        get() = !isLoading && isWhitelisted
}

object WhatsAppAddButtonDefaults {
    fun buttonText(isWhitelisted: Boolean): String =
        if (isWhitelisted) "Añadido a WhatsApp" else "Añadir a WhatsApp"

    fun evaluateState(isWhitelisted: Boolean, isLoading: Boolean): WhatsAppAddButtonState =
        WhatsAppAddButtonState(isWhitelisted = isWhitelisted, isLoading = isLoading)
}

/**
 * Vaporwave + Glassmorphism WhatsApp CTA button with mint / electric cyan neon gradient,
 * glowing pill shape, and high-contrast styling.
 */
@Composable
fun WhatsAppAddButton(
    isWhitelisted: Boolean,
    isLoading: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state = WhatsAppAddButtonDefaults.evaluateState(isWhitelisted, isLoading)
    val buttonShape = RoundedCornerShape(24.dp)

    val gradientBrush = if (isWhitelisted) {
        Brush.horizontalGradient(
            listOf(
                Color(0x3505FFA1),
                Color(0x3501CDFE)
            )
        )
    } else {
        Brush.horizontalGradient(
            listOf(
                WhatsAppGlow,
                ElectricCyan
            )
        )
    }

    val contentColor = if (isWhitelisted) WhatsAppGlow else Color(0xFF0F081D)

    Surface(
        modifier = modifier
            .clip(buttonShape)
            .clickable(enabled = state.isClickable, onClick = onClick),
        shape = buttonShape,
        color = Color.Transparent,
        border = if (isWhitelisted) androidx.compose.foundation.BorderStroke(1.5.dp, WhatsAppGlow) else null,
        shadowElevation = if (isWhitelisted) 2.dp else 8.dp
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
