package com.seventv.stickers.ui.components

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.seventv.stickers.ui.theme.DarkBackground
import com.seventv.stickers.ui.theme.DarkOnSurface
import com.seventv.stickers.ui.theme.DarkOnSurfaceVariant
import com.seventv.stickers.ui.theme.DarkSurface
import com.seventv.stickers.ui.theme.GlassSurfaceCard
import com.seventv.stickers.ui.theme.ElectricCyan
import com.seventv.stickers.ui.theme.GlassBorder
import com.seventv.stickers.ui.theme.GlassSurfaceBar
import com.seventv.stickers.ui.theme.NeonFuchsia
import com.seventv.stickers.ui.theme.RetroViolet
import com.seventv.stickers.ui.theme.SunsetYellow

object EmotePeekOverlayDefaults {
    const val BADGE_ANIMATED = "ANIM WebP"
    const val BADGE_STATIC = "STATIC"

    const val INITIAL_SCALE = 0.85f
    const val TARGET_SCALE = 1.0f
    const val EXIT_SCALE = 0.85f
    const val EXIT_DURATION_MS = 140

    val BLUR_RADIUS: Dp = 16.dp
    const val SCRIM_ALPHA = 0.70f
    val CARD_CORNER_RADIUS: Dp = 28.dp
    val PREVIEW_MAX_SIZE: Dp = 256.dp
    val HIT_SLOP: Dp = 14.dp

    fun badgeText(isAnimated: Boolean): String = if (isAnimated) BADGE_ANIMATED else BADGE_STATIC

    /**
     * Hit-tests [pointerPosition] against registered [actionBounds], optionally expanding
     * each candidate by [hitSlopPx] for accessible thumb-drag targeting.
     */
    fun findHoveredActionIndex(
        pointerPosition: Offset?,
        actionBounds: Map<Int, Rect>,
        hitSlopPx: Float = 0f
    ): Int? {
        if (pointerPosition == null || actionBounds.isEmpty()) return null
        for ((index, rect) in actionBounds) {
            val inflated = if (hitSlopPx > 0f) {
                Rect(
                    left = rect.left - hitSlopPx,
                    top = rect.top - hitSlopPx,
                    right = rect.right + hitSlopPx,
                    bottom = rect.bottom + hitSlopPx
                )
            } else {
                rect
            }
            if (inflated.contains(pointerPosition)) {
                return index
            }
        }
        return null
    }

    /**
     * Calculates the geometric emergence translation offset from the card's origin bounds.
     */
    fun calculateEmergenceOffset(
        sourceCenter: Offset?,
        containerCenter: Offset,
        progress: Float
    ): Offset {
        if (sourceCenter == null) return Offset.Zero
        val factor = ((1f - progress).coerceIn(0f, 1f)) * 0.35f
        if (factor == 0f) return Offset.Zero
        val vector = sourceCenter - containerCenter
        return Offset(vector.x * factor, vector.y * factor)
    }

    /**
     * Calculates scale factor from entrance/exit progress.
     */
    fun calculateScale(progress: Float, baseScale: Float = INITIAL_SCALE): Float {
        return baseScale + (TARGET_SCALE - baseScale) * progress.coerceIn(0f, 1.2f)
    }
}

/**
 * Representation of a quick action displayed in the horizontal pill bar beneath the floating peek card.
 */
@Immutable
data class EmotePeekAction(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val accentColor: Color = ElectricCyan,
    val isActive: Boolean = false,
    val onClick: () -> Unit
)

/**
 * Payload describing the emote or sticker being peeked.
 */
@Immutable
data class EmotePeekPayload(
    val id: String,
    val name: String,
    val isAnimated: Boolean,
    val imageModel: Any,
    val placeholderCacheKey: String? = null,
    val sourceLabel: String? = null,
    val metadataText: String? = null,
    val sourceBoundsInRoot: Rect? = null,
    val actions: List<EmotePeekAction> = emptyList()
)

/**
 * Controller holding the interactive state of an active or transitioning peek session.
 */
class EmotePeekState {
    var currentPayload by mutableStateOf<EmotePeekPayload?>(null)
        private set

    var isVisible by mutableStateOf(false)
        private set

    var pointerPositionInRoot by mutableStateOf<Offset?>(null)
        private set

    var hoveredActionIndex by mutableStateOf<Int?>(null)
        internal set

    internal var boundsSnapshotProvider: (() -> Map<Int, Rect>)? = null
    internal var hitSlopPx: Float = 0f

    val isActive: Boolean
        get() = isVisible && currentPayload != null

    fun show(payload: EmotePeekPayload) {
        currentPayload = payload
        hoveredActionIndex = null
        isVisible = true
    }

    fun updatePointer(positionInRoot: Offset) {
        pointerPositionInRoot = positionInRoot
    }

    fun onRelease(hapticFeedback: HapticFeedback? = null) {
        val payload = currentPayload
        val freshBounds = boundsSnapshotProvider?.invoke().orEmpty()
        val hoveredIdx = if (freshBounds.isNotEmpty() && pointerPositionInRoot != null) {
            EmotePeekOverlayDefaults.findHoveredActionIndex(
                pointerPosition = pointerPositionInRoot,
                actionBounds = freshBounds,
                hitSlopPx = hitSlopPx
            )
        } else {
            hoveredActionIndex
        }
        if (hoveredIdx != null && payload != null && hoveredIdx in payload.actions.indices) {
            hapticFeedback?.performHapticFeedback(HapticFeedbackType.LongPress)
            val action = payload.actions[hoveredIdx]
            action.onClick()
        }
        dismiss()
    }

    fun dismiss() {
        isVisible = false
        pointerPositionInRoot = null
        hoveredActionIndex = null
    }

    internal fun clearPayload() {
        currentPayload = null
        boundsSnapshotProvider = null
        hoveredActionIndex = null
        pointerPositionInRoot = null
    }
}

@Composable
fun rememberEmotePeekState(): EmotePeekState {
    return remember { EmotePeekState() }
}

val LocalEmotePeekController = compositionLocalOf<EmotePeekState?> { null }

/**
 * Modifier to attach to any grid item card. Tracks tap vs long press.
 * On long press, it consumes gestures and streams root drag coordinates until release.
 */
@Composable
fun Modifier.emotePeekGesture(
    key: Any? = Unit,
    enabled: Boolean = true,
    onPeekStart: (boundsInRoot: Rect) -> Unit,
    onPeekMove: (pointerPositionInRoot: Offset) -> Unit,
    onPeekRelease: () -> Unit
): Modifier {
    val coordsHolder = remember {
        object {
            var coords: LayoutCoordinates? = null
        }
    }
    val haptic = LocalHapticFeedback.current
    val currentStart by rememberUpdatedState(onPeekStart)
    val currentMove by rememberUpdatedState(onPeekMove)
    val currentRelease by rememberUpdatedState(onPeekRelease)

    return this
        .onGloballyPositioned { coordinates ->
            coordsHolder.coords = coordinates
        }
        .pointerInput(key, enabled) {
            if (!enabled) return@pointerInput

            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val startPosition = down.position
                val longPressTimeout = viewConfiguration.longPressTimeoutMillis
                val touchSlop = viewConfiguration.touchSlop

                var longPressFired = false
                try {
                    withTimeout(longPressTimeout) {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed || change.isConsumed) {
                                return@withTimeout
                            }
                            if ((change.position - startPosition).getDistance() > touchSlop) {
                                return@withTimeout
                            }
                        }
                    }
                } catch (_: PointerEventTimeoutCancellationException) {
                    longPressFired = true
                }

                if (longPressFired) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val coords = coordsHolder.coords
                    val bounds = coords?.takeIf { it.isAttached }?.boundsInRoot() ?: Rect(
                        startPosition.x - 40f,
                        startPosition.y - 40f,
                        startPosition.x + 40f,
                        startPosition.y + 40f
                    )
                    currentStart(bounds)
                    val initialRootPos = coords?.takeIf { it.isAttached }?.localToRoot(startPosition) ?: startPosition
                    currentMove(initialRootPos)

                    // Track continuous drag movements in Initial pass and consume them
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        event.changes.forEach { it.consume() }

                        val activeChange = event.changes.firstOrNull { it.id == down.id }
                            ?: event.changes.firstOrNull()

                        if (activeChange == null || !activeChange.pressed) {
                            currentRelease()
                            break
                        }

                        val activeCoords = coordsHolder.coords
                        val rootPos = if (activeCoords != null && activeCoords.isAttached) {
                            activeCoords.localToRoot(activeChange.position)
                        } else {
                            activeChange.position
                        }
                        currentMove(rootPos)
                    }
                }
            }
        }
}

/**
 * Top-level container hosting content with progressive background blur
 * and the floating [EmotePeekOverlay] rendered on top.
 */
@Composable
fun EmotePeekHost(
    state: EmotePeekState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalEmotePeekController provides state) {
        Box(modifier = modifier.fillMaxSize()) {
            val blurRadius by animateDpAsState(
                targetValue = if (state.isActive) EmotePeekOverlayDefaults.BLUR_RADIUS else 0.dp,
                animationSpec = tween(durationMillis = 180),
                label = "ProgressiveBlurAnimation"
            )

            val contentModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurRadius > 0.dp) {
                Modifier
                    .fillMaxSize()
                    .blur(radius = blurRadius, edgeTreatment = BlurredEdgeTreatment.Rectangle)
            } else {
                Modifier.fillMaxSize()
            }

            Box(modifier = contentModifier) {
                content()
            }

            EmotePeekOverlay(state = state)
        }
    }
}

/**
 * Vaporwave + Frosted Glass floating peek preview overlay.
 */
@Composable
fun EmotePeekOverlay(
    state: EmotePeekState,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var renderedPayload by remember { mutableStateOf<EmotePeekPayload?>(null) }
    val progress = remember { Animatable(0f) }

    // Keep payload alive during exit animation
    LaunchedEffect(state.isVisible, state.currentPayload?.id) {
        if (state.isVisible && state.currentPayload != null) {
            renderedPayload = state.currentPayload
            progress.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        } else {
            if (progress.value > 0f) {
                progress.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = EmotePeekOverlayDefaults.EXIT_DURATION_MS,
                        easing = FastOutSlowInEasing
                    )
                )
            }
            renderedPayload = null
            state.clearPayload()
        }
    }

    val payload = renderedPayload
    if (payload == null && progress.value == 0f) return

    BackHandler(enabled = state.isVisible) {
        state.dismiss()
    }

    val density = LocalDensity.current
    val hitSlopPx = remember(density) { with(density) { EmotePeekOverlayDefaults.HIT_SLOP.toPx() } }
    val actionCoords = remember(payload?.id) { mutableStateMapOf<Int, LayoutCoordinates>() }
    val actionBounds = remember(payload?.id) { mutableStateMapOf<Int, Rect>() }

    LaunchedEffect(payload?.id, hitSlopPx) {
        state.hitSlopPx = hitSlopPx
        state.boundsSnapshotProvider = {
            actionCoords.mapNotNull { (index, coords) ->
                if (coords.isAttached) index to coords.boundsInRoot() else null
            }.toMap()
        }
    }

    // Update actionBounds and hovered action index on pointer drag or animation progress
    LaunchedEffect(state.pointerPositionInRoot, progress.value, payload?.actions?.size) {
        actionCoords.forEach { (index, coords) ->
            if (coords.isAttached) {
                actionBounds[index] = coords.boundsInRoot()
            }
        }
        val hovered = EmotePeekOverlayDefaults.findHoveredActionIndex(
            pointerPosition = state.pointerPositionInRoot,
            actionBounds = actionBounds,
            hitSlopPx = hitSlopPx
        )
        if (hovered != state.hoveredActionIndex) {
            if (hovered != null) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            state.hoveredActionIndex = hovered
        }
    }

    val scrimAlpha = (progress.value * EmotePeekOverlayDefaults.SCRIM_ALPHA).coerceIn(0f, 1f)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        val containerSize = remember(constraints) {
            IntSize(constraints.maxWidth, constraints.maxHeight)
        }
        val containerCenter = remember(containerSize) {
            Offset(containerSize.width / 2f, containerSize.height / 2f)
        }

        // Translucent dark scrim background (tap to dismiss)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground.copy(alpha = scrimAlpha))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    state.dismiss()
                }
        )

        if (payload != null) {
            val sourceCenter = payload.sourceBoundsInRoot?.center
            val emergenceOffset = remember(sourceCenter, containerCenter, progress.value) {
                EmotePeekOverlayDefaults.calculateEmergenceOffset(
                    sourceCenter = sourceCenter,
                    containerCenter = containerCenter,
                    progress = progress.value
                )
            }
            val currentScale = remember(progress.value) {
                EmotePeekOverlayDefaults.calculateScale(progress.value)
            }

            Column(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = currentScale
                        scaleY = currentScale
                        alpha = progress.value.coerceIn(0f, 1f)
                        translationX = emergenceOffset.x
                        translationY = emergenceOffset.y
                    }
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                FrostedPeekCard(
                    payload = payload,
                    modifier = Modifier.widthIn(max = 320.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                QuickActionPillBar(
                    actions = payload.actions,
                    hoveredIndex = state.hoveredActionIndex,
                    onRegisterCoords = { index, coords ->
                        actionCoords[index] = coords
                        if (coords.isAttached) {
                            actionBounds[index] = coords.boundsInRoot()
                        }
                    },
                    onActionClick = { action ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        action.onClick()
                        state.dismiss()
                    }
                )
            }
        }
    }
}

/**
 * Frosted Glass central card displaying the full-size animated WebP emote,
 * title, and format/source badges.
 */
@Composable
private fun FrostedPeekCard(
    payload: EmotePeekPayload,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val infiniteTransition = rememberInfiniteTransition(label = "NeonGlowLoop")
    val glowPhase by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "GlowPhaseAnimation"
    )

    val primaryGlow = if (payload.isAnimated) ElectricCyan else RetroViolet
    val secondaryGlow = if (payload.isAnimated) NeonFuchsia else SunsetYellow

    Card(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                drawCircle(
                    color = primaryGlow.copy(alpha = 0.18f * glowPhase),
                    radius = size.maxDimension * 0.58f,
                    center = center
                )
            },
        shape = RoundedCornerShape(EmotePeekOverlayDefaults.CARD_CORNER_RADIUS),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xEE160D2E)
        ),
        border = BorderStroke(
            1.5.dp,
            Brush.linearGradient(
                listOf(
                    primaryGlow.copy(alpha = 0.45f + 0.35f * glowPhase),
                    secondaryGlow.copy(alpha = 0.45f + 0.35f * glowPhase)
                )
            )
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Emote Name
            Text(
                text = payload.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = DarkOnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Metadata Badges Row
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Animated / Static Format Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (payload.isAnimated) Color(0x3501CDFE) else Color(0x25FFFFFF),
                    border = BorderStroke(
                        0.8.dp,
                        if (payload.isAnimated) ElectricCyan.copy(alpha = 0.75f) else GlassBorder
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (payload.isAnimated) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(ElectricCyan, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                        }
                        Text(
                            text = EmotePeekOverlayDefaults.badgeText(payload.isAnimated),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (payload.isAnimated) ElectricCyan else DarkOnSurfaceVariant
                        )
                    }
                }

                if (!payload.sourceLabel.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0x251F1435),
                        border = BorderStroke(0.6.dp, GlassBorder)
                    ) {
                        Text(
                            text = payload.sourceLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = DarkOnSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                if (!payload.metadataText.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0x251F1435),
                        border = BorderStroke(0.6.dp, GlassBorder)
                    ) {
                        Text(
                            text = payload.metadataText,
                            style = MaterialTheme.typography.labelSmall,
                            color = SunsetYellow.copy(alpha = 0.9f),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Central Emote Image Well (frosted dark inner container)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .sizeIn(maxHeight = EmotePeekOverlayDefaults.PREVIEW_MAX_SIZE),
                shape = RoundedCornerShape(20.dp),
                color = Color(0x400F081D),
                border = BorderStroke(1.dp, Color(0x25FFFFFF))
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    val imageRequest = remember(payload.imageModel, payload.placeholderCacheKey) {
                        ImageRequest.Builder(context)
                            .data(payload.imageModel)
                            .apply {
                                if (!payload.placeholderCacheKey.isNullOrBlank()) {
                                    placeholderMemoryCacheKey(payload.placeholderCacheKey)
                                }
                            }
                            .crossfade(false)
                            .build()
                    }

                    AsyncImage(
                        model = imageRequest,
                        contentDescription = payload.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Translucent horizontal pill bar containing quick actions beneath the peek card.
 */
@Composable
private fun QuickActionPillBar(
    actions: List<EmotePeekAction>,
    hoveredIndex: Int?,
    onRegisterCoords: (Int, LayoutCoordinates) -> Unit,
    onActionClick: (EmotePeekAction) -> Unit,
    modifier: Modifier = Modifier
) {
    if (actions.isEmpty()) return

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = GlassSurfaceBar,
        border = BorderStroke(
            1.dp,
            Brush.horizontalGradient(
                listOf(
                    ElectricCyan.copy(alpha = 0.45f),
                    NeonFuchsia.copy(alpha = 0.45f)
                )
            )
        ),
        shadowElevation = 12.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            actions.forEachIndexed { index, action ->
                val isHovered = hoveredIndex == index
                val scale by animateFloatAsState(
                    targetValue = if (isHovered) 1.10f else 1.0f,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "PillScaleAnimation"
                )
                val backgroundColor by animateColorAsState(
                    targetValue = when {
                        isHovered -> action.accentColor.copy(alpha = 0.35f)
                        action.isActive -> action.accentColor.copy(alpha = 0.22f)
                        else -> Color(0x20FFFFFF)
                    },
                    animationSpec = tween(durationMillis = 150),
                    label = "PillBackgroundAnimation"
                )
                val borderColor by animateColorAsState(
                    targetValue = when {
                        isHovered -> action.accentColor
                        action.isActive -> action.accentColor.copy(alpha = 0.85f)
                        else -> Color(0x35FFFFFF)
                    },
                    animationSpec = tween(durationMillis = 150),
                    label = "PillBorderAnimation"
                )

                Surface(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .onGloballyPositioned { coords ->
                            onRegisterCoords(index, coords)
                        }
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onActionClick(action) },
                    shape = RoundedCornerShape(16.dp),
                    color = backgroundColor,
                    border = BorderStroke(if (isHovered) 1.5.dp else 1.dp, borderColor),
                    shadowElevation = if (isHovered) 8.dp else 0.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = action.icon,
                            contentDescription = action.label,
                            tint = if (isHovered || action.isActive) action.accentColor else DarkOnSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = action.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isHovered || action.isActive) FontWeight.Bold else FontWeight.Medium,
                            color = if (isHovered || action.isActive) DarkOnSurface else DarkOnSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
