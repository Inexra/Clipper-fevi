package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SubtitlePosition
import com.example.data.model.SubtitleStyleConfig
import com.example.data.model.TranscriptSegment
import com.example.data.model.WordTiming

@Composable
fun SubtitleOverlay(
    segment: TranscriptSegment?,
    currentMs: Long,
    style: SubtitleStyleConfig,
    modifier: Modifier = Modifier
) {
    if (segment == null || segment.text.isBlank()) return

    val alignment = when (style.position) {
        SubtitlePosition.TOP -> Alignment.TopCenter
        SubtitlePosition.CENTER -> Alignment.Center
        SubtitlePosition.BOTTOM -> Alignment.BottomCenter
    }

    val topMargin = if (style.position == SubtitlePosition.TOP) 32.dp else 0.dp
    val bottomMargin = if (style.position == SubtitlePosition.BOTTOM) style.marginBottomDp.dp else 0.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .padding(top = topMargin, bottom = bottomMargin),
        contentAlignment = alignment
    ) {
        val bgColor = try {
            Color(android.graphics.Color.parseColor(style.backgroundColorHex))
        } catch (e: Exception) {
            Color.Transparent
        }

        val baseTextColor = try {
            Color(android.graphics.Color.parseColor(style.textColorHex))
        } catch (e: Exception) {
            Color.White
        }

        val activeWordColor = try {
            Color(android.graphics.Color.parseColor(style.activeWordColorHex))
        } catch (e: Exception) {
            Color(0xFFFFEB3B)
        }

        val shape = RoundedCornerShape(8.dp)

        Box(
            modifier = Modifier
                .clip(shape)
                .background(bgColor)
                .then(
                    if (bgColor != Color.Transparent) Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    else Modifier
                )
        ) {
            val annotated = buildAnnotatedString {
                if (style.wordHighlightEnabled && segment.words.isNotEmpty()) {
                    segment.words.forEachIndexed { index, wordTiming ->
                        val isSpokenNow = currentMs in wordTiming.startMs..wordTiming.endMs
                        val isPast = currentMs > wordTiming.endMs

                        val wordColor = if (isSpokenNow) {
                            activeWordColor
                        } else if (isPast) {
                            baseTextColor
                        } else {
                            baseTextColor.copy(alpha = 0.85f)
                        }

                        withStyle(
                            SpanStyle(
                                color = wordColor,
                                fontWeight = if (isSpokenNow) FontWeight.Black else FontWeight.Bold
                            )
                        ) {
                            append(wordTiming.word)
                        }

                        if (index < segment.words.size - 1) {
                            append(" ")
                        }
                    }
                } else {
                    withStyle(
                        SpanStyle(
                            color = baseTextColor,
                            fontWeight = FontWeight.Bold
                        )
                    ) {
                        append(segment.text)
                    }
                }
            }

            Text(
                text = annotated,
                fontSize = style.fontSizeSp.sp,
                textAlign = TextAlign.Center,
                lineHeight = (style.fontSizeSp * 1.25f).sp
            )
        }
    }
}
