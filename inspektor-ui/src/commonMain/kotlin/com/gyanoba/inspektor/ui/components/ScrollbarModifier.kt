package com.gyanoba.inspektor.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp


@Composable
internal fun Modifier.verticalScrollbar(
    state: ScrollState,
    thumbBreadth: Dp = 6.dp,
    thumbMinLength: Dp = 30.dp,
    color: Color = Color.LightGray,
): Modifier {
    return this then Modifier.drawWithContent {
        drawContent()

        val (scrollbarHeight, scrollbarYoffset) = calculateScrollbarLengthAndOffset(
            viewportSize = state.viewportSize.toFloat(),
            totalContentSize = state.maxValue + state.viewportSize.toFloat(),
            scrollValue = state.value.toFloat(),
            maxScrollValue = state.maxValue,
            scrollbarMinLength = thumbMinLength
        )

        drawRoundRect(
            cornerRadius = CornerRadius(thumbBreadth.toPx() / 2, thumbBreadth.toPx() / 2),
            color = color,
            topLeft = Offset(this.size.width - thumbBreadth.toPx(), scrollbarYoffset),
            size = Size(thumbBreadth.toPx(), scrollbarHeight),
            alpha = if (state.isScrollInProgress) 1f else 0f
        )
    }
}

@Composable
internal fun Modifier.horizontalScrollbar(
    state: ScrollState,
    scrollbarBreadth: Dp = 6.dp,
    scrollbarMinLength: Dp = 30.dp,
    color: Color = Color.LightGray,
): Modifier {
    return this then Modifier.drawWithContent {
        drawContent()

        val (scrollbarWidth, scrollbarXoffset) = calculateScrollbarLengthAndOffset(
            viewportSize = state.viewportSize.toFloat(),
            totalContentSize = state.maxValue + state.viewportSize.toFloat(),
            scrollValue = state.value.toFloat(),
            maxScrollValue = state.maxValue,
            scrollbarMinLength = scrollbarMinLength
        )

        drawRoundRect(
            cornerRadius = CornerRadius(scrollbarBreadth.toPx() / 2, scrollbarBreadth.toPx() / 2),
            color = color,
            topLeft = Offset(scrollbarXoffset, this.size.height - scrollbarBreadth.toPx()),
            size = Size(scrollbarWidth, scrollbarBreadth.toPx()),
            alpha = if (state.isScrollInProgress) 1f else 0f
        )
    }
}


private fun ContentDrawScope.calculateScrollbarLengthAndOffset(
    viewportSize: Float,
    totalContentSize: Float,
    scrollValue: Float,
    maxScrollValue: Int,
    scrollbarMinLength: Dp,
): Pair<Float, Float> {
    val scrollbarLength =
        (viewportSize * (viewportSize / totalContentSize)).coerceIn(scrollbarMinLength.toPx()..viewportSize)
    val variableZone = viewportSize - scrollbarLength
    val scrollbarOffset = (scrollValue / maxScrollValue) * variableZone
    return Pair(scrollbarLength, scrollbarOffset)
}