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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import com.meridian.compass.util.Bearing
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
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

private val RingBlue = Color(0xFF3D6BE8)
private val NorthRed = Color(0xFFFF5B54)
private val TargetOrange = Color(0xFFFFA726)

/**
 * Fixed blue ring in the middle with the heading readout; a much larger tick dial rotates
 * around it so only the top and bottom arcs of the dial are visible.
 */
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
    val numberTypeface = remember { Typeface.create("sans-serif-light", Typeface.NORMAL) }
    val pointLabel = Bearing.compassPointLabel(headingDegrees.toDouble())

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.58f)
            .clipToBounds()
            .pointerInput(pinBadges, headingDegrees) {
                detectTapGestures { tapOffset ->
                    val bigR = size.width * 0.86f
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val dx = tapOffset.x - center.x
                    val dy = tapOffset.y - center.y
                    val tapRadius = hypot(dx, dy)
                    if (abs(tapRadius - bigR * 0.595f) > bigR * 0.09f) return@detectTapGestures

                    val screenAngleDeg = Math.toDegrees(atan2(dy, dx).toDouble())
                    val candidateAzimuth = ((screenAngleDeg + 90 + headingDegrees) % 360 + 360) % 360

                    val nearest = pinBadges.minByOrNull { badge ->
                        var diff = abs(badge.azimuthDegrees - candidateAzimuth) % 360
                        if (diff > 180) diff = 360 - diff
                        diff
                    }
                    if (nearest != null) {
                        var diff = abs(nearest.azimuthDegrees - candidateAzimuth) % 360
                        if (diff > 180) diff = 360 - diff
                        if (diff <= 16.0) onPinTapped(nearest.id)
                    }
                }
            }
    ) {
        val s = size.width / 411f
        val bigR = size.width * 0.86f
        val ringR = bigR * 0.5f
        val center = Offset(size.width / 2f, size.height / 2f)

        rotate(degrees = -headingDegrees, pivot = center) {
            drawTicks(center, bigR, s, textColor)
            drawDialLabels(center, bigR, s, textColor, labelTypeface)

            markers.forEach { drawCelestialMarker(center, bigR, s, it) }
            pinBadges.forEach { drawPinBadge(center, bigR, s, it) }
            targetBearingDegrees?.let { drawTargetPointer(center, bigR, ringR, it) }
        }

        // Fixed blue ring
        drawCircle(color = RingBlue, radius = ringR, center = center, style = Stroke(width = 6f * s))

        targetBearingDegrees?.let {
            drawTurnArc(center, ringR - 12f * s, headingDegrees, it, s)
        }

        // Fixed pointer at top of ring
        drawLine(
            color = RingBlue,
            start = Offset(center.x, center.y - ringR - bigR * 0.14f),
            end = Offset(center.x, center.y - ringR - 10f * s),
            strokeWidth = 4f * s
        )
        val tri = Path().apply {
            moveTo(center.x, center.y - ringR - 14f * s)
            lineTo(center.x - 9f * s, center.y - ringR + 3f * s)
            lineTo(center.x + 9f * s, center.y - ringR + 3f * s)
            close()
        }
        drawPath(tri, color = RingBlue)

        // Heading readout inside the ring
        drawContext.canvas.nativeCanvas.apply {
            val numberPaint = Paint().apply {
                color = textColor.toArgb()
                textSize = ringR * 0.52f
                isAntiAlias = true
                typeface = numberTypeface
            }
            val labelPaint = Paint().apply {
                color = RingBlue.toArgb()
                textSize = ringR * 0.2f
                isAntiAlias = true
                typeface = labelTypeface
            }
            val numberText = "${headingDegrees.toInt()}°"
            val numW = numberPaint.measureText(numberText)
            val labW = labelPaint.measureText(pointLabel)
            val gap = 10f * s
            val startX = center.x - (numW + gap + labW) / 2f
            val baseY = center.y + numberPaint.textSize * 0.35f
            drawText(pointLabel, startX, baseY, labelPaint)
            drawText(numberText, startX + labW + gap, baseY, numberPaint)
        }
    }
}

private fun DrawScope.polar(center: Offset, radius: Float, azimuthDeg: Double): Offset {
    val a = Math.toRadians(azimuthDeg - 90)
    return Offset(center.x + (radius * cos(a)).toFloat(), center.y + (radius * sin(a)).toFloat())
}

private fun DrawScope.drawTicks(center: Offset, bigR: Float, s: Float, textColor: Color) {
    for (deg in 0 until 360 step 2) {
        val isLong = deg % 30 == 0
        val isMedium = deg % 10 == 0 && !isLong
        val len = when {
            isLong -> bigR * 0.14f
            isMedium -> bigR * 0.08f
            else -> bigR * 0.045f
        }
        val color = when {
            isLong -> textColor
            isMedium -> textColor.copy(alpha = 0.7f)
            else -> textColor.copy(alpha = 0.4f)
        }
        val width = when {
            isLong -> 5f * s
            isMedium -> 3f * s
            else -> 2f * s
        }
        drawLine(color, polar(center, bigR - len, deg.toDouble()), polar(center, bigR, deg.toDouble()), strokeWidth = width)
    }
}

private fun DrawScope.drawDialLabels(center: Offset, bigR: Float, s: Float, textColor: Color, typeface: Typeface) {
    val cardinals = mapOf(0 to "N", 90 to "E", 180 to "S", 270 to "W")
    val inter = mapOf(45 to "NE", 135 to "SE", 225 to "SW", 315 to "NW")
    val canvas = drawContext.canvas.nativeCanvas

    val numPaint = Paint().apply {
        color = textColor.copy(alpha = 0.85f).toArgb()
        textSize = bigR * 0.058f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }
    for (deg in 0 until 360 step 30) {
        val p = polar(center, bigR * 0.80f, deg.toDouble())
        canvas.save()
        canvas.rotate(deg.toFloat(), p.x, p.y)
        canvas.drawText(deg.toString(), p.x, p.y + numPaint.textSize * 0.35f, numPaint)
        canvas.restore()
    }

    for ((deg, label) in cardinals) {
        val paint = Paint().apply {
            color = (if (deg == 0) NorthRed else RingBlue).toArgb()
            textSize = bigR * 0.085f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            this.typeface = typeface
        }
        val p = polar(center, bigR * 0.69f, deg.toDouble())
        canvas.save()
        canvas.rotate(deg.toFloat(), p.x, p.y)
        canvas.drawText(label, p.x, p.y + paint.textSize * 0.35f, paint)
        canvas.restore()
    }
    for ((deg, label) in inter) {
        val paint = Paint().apply {
            color = textColor.copy(alpha = 0.55f).toArgb()
            textSize = bigR * 0.05f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            this.typeface = typeface
        }
        val p = polar(center, bigR * 0.69f, deg.toDouble())
        canvas.save()
        canvas.rotate(deg.toFloat(), p.x, p.y)
        canvas.drawText(label, p.x, p.y + paint.textSize * 0.35f, paint)
        canvas.restore()
    }
}

private fun DrawScope.drawCelestialMarker(center: Offset, bigR: Float, s: Float, marker: DialMarker) {
    val pos = polar(center, bigR * 0.595f, marker.azimuthDegrees)
    val belowHorizon = (marker.elevationDegrees ?: 1.0) < 0
    val alpha = if (belowHorizon) 0.35f else 1f
    val rad = bigR * 0.045f
    drawCircle(color = marker.color.copy(alpha = alpha), radius = rad, center = pos)
    drawContext.canvas.nativeCanvas.apply {
        val paint = Paint().apply {
            color = Color.Black.copy(alpha = alpha).toArgb()
            textSize = rad * 1.1f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        drawText(marker.glyph, pos.x, pos.y + paint.textSize / 3f, paint)
    }
}

private fun DrawScope.drawPinBadge(center: Offset, bigR: Float, s: Float, badge: PinBadge) {
    val pos = polar(center, bigR * 0.595f, badge.azimuthDegrees)
    val rad = bigR * 0.05f
    drawCircle(color = badge.color.copy(alpha = 0.22f), radius = rad * 1.2f, center = pos)
    drawCircle(color = badge.color, radius = rad, center = pos, style = Stroke(width = 2.5f * s))
    drawContext.canvas.nativeCanvas.apply {
        val paint = Paint().apply {
            textSize = rad * 1.1f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        drawText(badge.glyph, pos.x, pos.y + paint.textSize / 3f, paint)
    }
}

private fun DrawScope.drawTargetPointer(center: Offset, bigR: Float, ringR: Float, bearing: Double) {
    val tip = polar(center, bigR * 0.60f, bearing)
    val baseLeft = polar(center, ringR + 4f, bearing - 5.0)
    val baseRight = polar(center, ringR + 4f, bearing + 5.0)
    val path = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(baseLeft.x, baseLeft.y)
        lineTo(baseRight.x, baseRight.y)
        close()
    }
    drawPath(path, color = TargetOrange)
}

private fun DrawScope.drawTurnArc(center: Offset, arcRadius: Float, headingDegrees: Float, targetBearing: Double, s: Float) {
    val targetScreenAngle = targetBearing - 90 - headingDegrees
    var sweep = targetScreenAngle - (-90.0)
    sweep = ((sweep + 540) % 360) - 180
    drawArc(
        color = TargetOrange.copy(alpha = 0.8f),
        startAngle = -90f,
        sweepAngle = sweep.toFloat(),
        useCenter = false,
        topLeft = Offset(center.x - arcRadius, center.y - arcRadius),
        size = Size(arcRadius * 2, arcRadius * 2),
        style = Stroke(width = 5f * s)
    )
}
