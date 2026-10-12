package com.seventv.stickers.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.seventv.stickers.ui.theme.CheckerboardDark
import com.seventv.stickers.ui.theme.CheckerboardLight
import com.seventv.stickers.ui.theme.WhatsAppDarkChatBackground
import com.seventv.stickers.ui.theme.WhatsAppLightChatBackground

enum class ChatSimulatorMode {
    WHATSAPP_DARK,
    WHATSAPP_LIGHT,
    CHECKERBOARD
}

@Composable
fun ChatSimulatorBackground(
    mode: ChatSimulatorMode,
    modifier: Modifier = Modifier,
    checkerSquareSize: Dp = 16.dp,
    content: @Composable BoxScope.() -> Unit
) {
    when (mode) {
        ChatSimulatorMode.WHATSAPP_DARK -> {
            Box(
                modifier = modifier.background(WhatsAppDarkChatBackground),
                content = content
            )
        }
        ChatSimulatorMode.WHATSAPP_LIGHT -> {
            Box(
                modifier = modifier.background(WhatsAppLightChatBackground),
                content = content
            )
        }
        ChatSimulatorMode.CHECKERBOARD -> {
            Box(modifier = modifier) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val squarePx = checkerSquareSize.toPx()
                    if (squarePx > 0) {
                        val numCols = (size.width / squarePx).toInt() + 1
                        val numRows = (size.height / squarePx).toInt() + 1

                        // Draw base with CheckerboardLight
                        drawRect(CheckerboardLight)

                        // Draw dark checker squares
                        for (row in 0 until numRows) {
                            for (col in 0 until numCols) {
                                if ((row + col) % 2 == 1) {
                                    drawRect(
                                        color = CheckerboardDark,
                                        topLeft = Offset(col * squarePx, row * squarePx),
                                        size = Size(squarePx, squarePx)
                                    )
                                }
                            }
                        }
                    }
                }
                content()
            }
        }
    }
}
