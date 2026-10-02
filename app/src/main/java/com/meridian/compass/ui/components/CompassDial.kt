package com.meridian.compass.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

data class DialMarker(
    val azimuthDegrees: Double,
    val color: Color,
    val glyph: String,
    val elevationDegrees: Double? = null
)

data class PinBadge(
    val id: Long,
    val azimuthDegrees: Double,
    val glyph: String,
    val color: Color
)

@Composable
fun CompassDial(
    headingDegrees: Float,
    targetBearingDegrees: Double?,
    markers: List<DialMarker>,
    pinBadges: List<PinBadge> = emptyList(),
    onPinTapped: (Long) -> Unit = {},
    ringColor: Color,
    dialBackground: Color,
    textColor: Color,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val labelTypeface = remember { Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .pointerInput(pinBadges, headingDegrees) {
                detectTapGestures { tapOffset ->
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radius = min(size.width, size.height) / 2f * 0.92f
                    val dx = tapOffset.x - center.x
                    val dy = tapOffset.y - center.y
                    val tapRadius = hypot(dx, dy)
                    val badgeRadius = radius * 0.84f
                    if (kotlin.math.abs(tapRadius - badgeRadius) > 36f) return@detectTapGestures

                    val screenAngleDeg = Math.toDegrees(atan2(dy, dx).toDouble())
                    val candidateAzimuth = ((screenAngleDeg + 90 + headingDegrees) % 360 + 360) % 360

                    val nearest = pinBadges.minByOrNull { badge ->
                        var diff = kotlin.math.abs(badge.azimuthDegrees - candidateAzimuth) % 360
                        if (diff > 180) diff = 360 - diff
                        diff
                    }
                    if (nearest != null) {
                        var diff = kotlin.math.abs(nearest.azimuthDegrees - candidateAzimuth) % 360
                        if (diff > 180) diff = 360 - diff
                        if (diff <= 16.0) onPinTapped(nearest.id)
                    }
                }
            }
    ) {
        val diameter = min(size.width, size.height)
        val radius = diameter / 2f * 0.92f
        val center = Offset(size.width / 2f, size.height / 2f)

        rotate(degrees = -headingDegrees, pivot = center) {
            drawDialFace(center, radius, ringColor, dialBackground)
            drawTicksAndLabels(center, radius, textColor, labelTypeface)

            markers.forEach { marker ->
                drawCelestialMarker(center, radius, marker)
            }
            pinBadges.forEach { badge ->
                drawPinBadge(center, radius, badge)
            }

            targetBearingDegrees?.let { bearing ->
                drawTargetArrow(center, radius, bearing, accentColor)
            }
        }

        targetBearingDegrees?.let { bearing ->
            drawTurnArc(center, radius, headingDegrees, bearing, accentColor)
        }

        drawHeadingPointer(center, radius, accentColor)
    }
}

private fun DrawScope.drawDialFace(center: Offset, radius: Float, ringColor: Color, dialBackground: Color) {
    drawCircle(color = dialBackground, radius = radius, center = center)
    drawCircle(color = ringColor, radius = radius, center = center, style = Stroke(width = 3f))
    drawCircle(color = ringColor.copy(alpha = 0.35f), radius = radius * 0.72f, center = center, style = Stroke(width = 1.5f))
}

private fun DrawScope.drawTicksAndLabels(center: Offset, radius: Float, textColor: Color, typeface: Typeface) {
    val cardinalLabels = mapOf(0 to "N", 90 to "E", 180 to "S", 270 to "W")
    val intercardinalLabels = mapOf(45 to "NE", 135 to "SE", 225 to "SW", 315 to "NW")

    for (deg in 0 until 360 step 5) {
        val isMajor = deg % 90 == 0
        val isMedium = deg % 45 == 0 && !isMajor
        val tickLength = when {
            isMajor -> radius * 0.14f
            isMedium -> radius * 0.10f
            deg % 10 == 0 -> radius * 0.07f
            else -> radius * 0.035f
        }
        val angleRad = Math.toRadians((deg - 90).toDouble())
        val outer = Offset(
            center.x + (radius * cos(angleRad)).toFloat(),
            center.y + (radius * sin(angleRad)).toFloat()
        )
        val inner = Offset(
            center.x + ((radius - tickLength) * cos(angleRad)).toFloat(),
            center.y + ((radius - tickLength) * sin(angleRad)).toFloat()
        )
        val tickColor = if (isMajor) textColor else textColor.copy(alpha = 0.55f)
        drawLine(tickColor, inner, outer, strokeWidth = if (isMajor) 4f else 2f)

        val label = cardinalLabels[deg] ?: intercardinalLabels[deg]
        if (label != null) {
            val labelRadius = radius - tickLength - 34f
            val labelPos = Offset(
                center.x + (labelRadius * cos(angleRad)).toFloat(),
                center.y + (labelRadius * sin(angleRad)).toFloat()
            )
            drawContext.canvas.nativeCanvas.apply {
                val paint = Paint().apply {
                    color = if (deg == 0) android.graphics.Color.parseColor("#FF5B54") else textColor.toArgb()
                    textSize = if (cardinalLabels.containsKey(deg)) 40f else 26f
                    textAlign = Paint.Align.CENTER
                    isAntiAlias = true
                    setTypeface(typeface)
                }
                drawText(label, labelPos.x, labelPos.y + paint.textSize / 3f, paint)
            }
        } else if (deg % 30 == 0) {
            val labelRadius = radius - tickLength - 22f
            val labelPos = Offset(
                center.x + (labelRadius * cos(angleRad)).toFloat(),
                center.y + (labelRadius * sin(angleRad)).toFloat()
            )
            drawContext.canvas.nativeCanvas.apply {
                val paint = Paint().apply {
                    color = textColor.copy(alpha = 0.55f).toArgb()
                    textSize = 20f
                    textAlign = Paint.Align.CENTER
                    isAntiAlias = true
                }
                drawText(deg.toString(), labelPos.x, labelPos.y + paint.textSize / 3f, paint)
            }
        }
    }
}

private fun DrawScope.drawCelestialMarker(center: Offset, radius: Float, marker: DialMarker) {
    val angleRad = Math.toRadians((marker.azimuthDegrees - 90))
    val markerRadius = radius * 0.84f
    val pos = Offset(
        center.x + (markerRadius * cos(angleRad)).toFloat(),
        center.y + (markerRadius * sin(angleRad)).toFloat()
    )
    val belowHorizon = (marker.elevationDegrees ?: 1.0) < 0
    val alpha = if (belowHorizon) 0.35f else 1f
    drawCircle(color = marker.color.copy(alpha = alpha), radius = 16f, center = pos)
    drawContext.canvas.nativeCanvas.apply {
        val paint = Paint().apply {
            color = Color.Black.copy(alpha = alpha).toArgb()
            textSize = 18f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        drawText(marker.glyph, pos.x, pos.y + paint.textSize / 3f, paint)
    }
}

private fun DrawScope.drawPinBadge(center: Offset, radius: Float, badge: PinBadge) {
    val angleRad = Math.toRadians((badge.azimuthDegrees - 90))
    val badgeRadius = radius * 0.84f
    val pos = Offset(
        center.x + (badgeRadius * cos(angleRad)).toFloat(),
        center.y + (badgeRadius * sin(angleRad)).toFloat()
    )
    drawCircle(color = badge.color.copy(alpha = 0.22f), radius = 22f, center = pos)
    drawCircle(color = badge.color, radius = 18f, center = pos, style = Stroke(width = 2.5f))
    drawContext.canvas.nativeCanvas.apply {
        val paint = Paint().apply {
            textSize = 20f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        drawText(badge.glyph, pos.x, pos.y + paint.textSize / 3f, paint)
    }
}

private fun DrawScope.drawTargetArrow(center: Offset, radius: Float, bearing: Double, accentColor: Color) {
    val angleRad = Math.toRadians(bearing - 90)
    val tip = Offset(
        center.x + (radius * 0.62f * cos(angleRad)).toFloat(),
        center.y + (radius * 0.62f * sin(angleRad)).toFloat()
    )
    drawLine(accentColor, center, tip, strokeWidth = 6f)
    val leftAngle = Math.toRadians(bearing - 90 - 12)
    val rightAngle = Math.toRadians(bearing - 90 + 12)
    val arrowSize = radius * 0.09f
    val left = Offset(
        tip.x - (arrowSize * cos(leftAngle)).toFloat(),
        tip.y - (arrowSize * sin(leftAngle)).toFloat()
    )
    val right = Offset(
        tip.x - (arrowSize * cos(rightAngle)).toFloat(),
        tip.y - (arrowSize * sin(rightAngle)).toFloat()
    )
    drawLine(accentColor, tip, left, strokeWidth = 6f)
    drawLine(accentColor, tip, right, strokeWidth = 6f)
}

private fun DrawScope.drawTurnArc(center: Offset, radius: Float, headingDegrees: Float, targetBearing: Double, accentColor: Color) {
    val targetScreenAngle = targetBearing - 90 - headingDegrees
    var sweep = (targetScreenAngle - (-90.0))
    sweep = ((sweep + 540) % 360) - 180
    val arcRadius = radius * 0.98f
    drawArc(
        color = accentColor.copy(alpha = 0.55f),
        startAngle = -90f,
        sweepAngle = sweep.toFloat(),
        useCenter = false,
        topLeft = Offset(center.x - arcRadius, center.y - arcRadius),
        size = androidx.compose.ui.geometry.Size(arcRadius * 2, arcRadius * 2),
        style = Stroke(width = 6f)
    )
}

private fun DrawScope.drawHeadingPointer(center: Offset, radius: Float, accentColor: Color) {
    val topY = center.y - radius - 4f
    drawLine(
        color = accentColor,
        start = Offset(center.x, topY),
        end = Offset(center.x, center.y - radius * 0.62f),
        strokeWidth = 5f
    )
    val tipY = topY
    val path = androidx.compose.ui.graphics.Path().apply {
        moveTo(center.x, tipY - 2f)
        lineTo(center.x - 12f, tipY + 22f)
        lineTo(center.x + 12f, tipY + 22f)
        close()
    }
    drawPath(path, color = accentColor)
}
