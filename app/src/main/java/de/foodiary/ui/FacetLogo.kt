package de.foodiary.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

private val LogoDark = Color(0xFF7C6AA3)

/**
 * Das Logo aus dem Designkonzept (app-icon-pastell.svg): ein Siebeneck, dessen Facetten in einem
 * versetzten Mittelpunkt zusammenlaufen. Drei helle, drei dunkle, eine in Hellrot; die siebte bleibt
 * frei und zeigt den Hintergrund. Koordinaten sind auf 0..1 normiert.
 */
@Composable
fun FacetLogo(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val outer = listOf(
            pt(0.488f, 0.000f),
            pt(0.915f, 0.172f),
            pt(1.000f, 0.618f),
            pt(0.744f, 0.968f),
            pt(0.256f, 1.000f),
            pt(0.000f, 0.650f),
            pt(0.073f, 0.204f)
        )
        val center = pt(0.360f, 0.414f)
        val colors = listOf(Palette.Cream, LogoDark, Palette.SoftRed, LogoDark, Palette.Cream, LogoDark, null)

        for (k in 0 until 7) {
            val color = colors[k] ?: continue
            drawPath(polygon(listOf(outer[k], outer[(k + 1) % 7], center)), color)
        }
        drawPath(polygon(outer), LogoDark, style = Stroke(width = 2.dp.toPx()))
    }
}

private fun DrawScope.pt(x: Float, y: Float): Offset = Offset(x * size.width, y * size.height)

private fun polygon(points: List<Offset>): Path = Path().apply {
    moveTo(points[0].x, points[0].y)
    for (p in points.drop(1)) lineTo(p.x, p.y)
    close()
}
