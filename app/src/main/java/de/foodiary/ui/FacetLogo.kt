package de.foodiary.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Das flächige Facetten-Motiv in den Farben von Version 3: ein Siebeneck mit innerem Stern,
 * helle Facetten, lavendelfarbener Kern und feine Kontur. Koordinaten sind auf 0..1 normiert.
 */
@Composable
fun FacetLogo(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val outer = listOf(
            pt(0.50f, 0.00f), pt(0.90f, 0.20f), pt(1.00f, 0.65f), pt(0.72f, 1.00f),
            pt(0.27f, 0.99f), pt(0.00f, 0.63f), pt(0.10f, 0.20f)
        )
        val inner = listOf(
            pt(0.60f, 0.30f), pt(0.73f, 0.47f), pt(0.69f, 0.68f), pt(0.50f, 0.77f),
            pt(0.31f, 0.68f), pt(0.27f, 0.47f), pt(0.40f, 0.30f)
        )
        val spikeColors = listOf(
            Palette.Cream, Palette.Lavender, Palette.Cream, Palette.Lavender,
            Palette.Cream, Palette.Lavender, Palette.Cream
        )

        // Jede äußere Spitze bildet mit ihren zwei Nachbarn im inneren Stern eine Fläche.
        for (k in 0 until 7) {
            val previous = inner[(k + 6) % 7]
            val next = inner[k]
            drawPath(polygon(listOf(outer[k], previous, next)), spikeColors[k])
        }
        drawPath(polygon(inner), Palette.LavenderDeep)
        drawPath(polygon(outer), Palette.Outline, style = Stroke(width = 2.dp.toPx()))
    }
}

private fun DrawScope.pt(x: Float, y: Float): Offset = Offset(x * size.width, y * size.height)

private fun polygon(points: List<Offset>): Path = Path().apply {
    moveTo(points[0].x, points[0].y)
    for (p in points.drop(1)) lineTo(p.x, p.y)
    close()
}
