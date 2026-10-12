package com.seventv.stickers.ui.components

import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
 * Branded WhatsApp CTA button indicating addition, loading, or whitelisted status.
 */
@Composable
fun WhatsAppAddButton(
    isWhitelisted: Boolean,
    isLoading: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state = WhatsAppAddButtonDefaults.evaluateState(isWhitelisted, isLoading)

    Button(
        onClick = onClick,
        enabled = state.isClickable,
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.tertiary,
            contentColor = MaterialTheme.colorScheme.onTertiary,
            disabledContainerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f),
            disabledContentColor = MaterialTheme.colorScheme.onTertiary.copy(alpha = 0.8f)
        )
    ) {
        if (state.showProgress) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = MaterialTheme.colorScheme.onTertiary,
                strokeWidth = 2.dp
            )
        } else {
            if (state.showCheckmark) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = state.buttonText,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onTertiary
            )
        }
    }
}
