package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.example.data.model.DrawingStroke
import com.example.data.model.PointData
import java.util.UUID

@Composable
fun DrawingCanvas(
    strokes: List<DrawingStroke>,
    selectedColorHex: String,
    selectedWidth: Float,
    selectedTool: String, // PEN, PENCIL, MARKER, ERASER
    selectedShape: String?, // null = Freehand, RECTANGLE, CIRCLE, LINE
    onStrokesChanged: (List<DrawingStroke>) -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Black
) {
    // Current temporary stroke being sketched by active gesture
    var activeStrokePoints by remember { mutableStateOf<List<PointData>>(emptyList()) }
    var activeShapeStart by remember { mutableStateOf<Offset?>(null) }
    var activeShapeEnd by remember { mutableStateOf<Offset?>(null) }

    Box(
        modifier = modifier
            .background(backgroundColor)
            .pointerInput(selectedTool, selectedShape, selectedColorHex, selectedWidth) {
                detectDragGestures(
                    onDragStart = { offset ->
                        if (selectedShape == null) {
                            activeStrokePoints = listOf(PointData(offset.x, offset.y))
                        } else {
                            activeShapeStart = offset
                            activeShapeEnd = offset
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val currentOffset = change.position
                        
                        if (selectedShape == null) {
                            activeStrokePoints = activeStrokePoints + PointData(currentOffset.x, currentOffset.y)
                        } else {
                            activeShapeEnd = currentOffset
                        }
                    },
                    onDragEnd = {
                        if (selectedShape == null) {
                            if (activeStrokePoints.isNotEmpty()) {
                                val strokeColor = if (selectedTool == "ERASER") {
                                    // Use Hex code matching background canvas
                                    "#000000"
                                } else {
                                    selectedColorHex
                                }
                                
                                val newStroke = DrawingStroke(
                                    strokeId = UUID.randomUUID().toString(),
                                    points = activeStrokePoints,
                                    colorHex = strokeColor,
                                    strokeWidth = selectedWidth,
                                    brushType = selectedTool,
                                    shapeType = null
                                )
                                onStrokesChanged(strokes + newStroke)
                                activeStrokePoints = emptyList()
                            }
                        } else {
                            val start = activeShapeStart
                            val end = activeShapeEnd
                            if (start != null && end != null) {
                                val strokeColor = if (selectedTool == "ERASER") "#000000" else selectedColorHex
                                val newStroke = DrawingStroke(
                                    strokeId = UUID.randomUUID().toString(),
                                    points = emptyList(),
                                    colorHex = strokeColor,
                                    strokeWidth = selectedWidth,
                                    brushType = selectedTool,
                                    shapeType = selectedShape,
                                    startX = start.x,
                                    startY = start.y,
                                    endX = end.x,
                                    endY = end.y
                                )
                                onStrokesChanged(strokes + newStroke)
                                activeShapeStart = null
                                activeShapeEnd = null
                            }
                        }
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Draw completed historic strokes
            for (stroke in strokes) {
                val color = parseHexToColor(stroke.colorHex, stroke.brushType)
                val width = stroke.strokeWidth

                if (stroke.shapeType == null) {
                    val points = stroke.points
                    if (points.isNotEmpty()) {
                        val path = Path().apply {
                            moveTo(points[0].x, points[0].y)
                            for (index in 1 until points.size) {
                                lineTo(points[index].x, points[index].y)
                            }
                        }
                        drawPath(
                            path = path,
                            color = color,
                            style = Stroke(
                                width = width,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                } else {
                    // Draw Vector geometrical shapes
                    val sX = stroke.startX
                    val sY = stroke.startY
                    val eX = stroke.endX
                    val eY = stroke.endY

                    when (stroke.shapeType) {
                        "LINE" -> {
                            drawLine(
                                color = color,
                                start = Offset(sX, sY),
                                end = Offset(eX, eY),
                                strokeWidth = width,
                                cap = StrokeCap.Round
                            )
                        }
                        "RECTANGLE" -> {
                            val left = minOf(sX, eX)
                            val top = minOf(sY, eY)
                            val right = maxOf(sX, eX)
                            val bottom = maxOf(sY, eY)
                            drawRect(
                                color = color,
                                topLeft = Offset(left, top),
                                size = Size(right - left, bottom - top),
                                style = Stroke(width = width)
                            )
                        }
                        "CIRCLE" -> {
                            val radius = kotlin.math.hypot(eX - sX, eY - sY)
                            drawCircle(
                                color = color,
                                radius = radius,
                                center = Offset(sX, sY),
                                style = Stroke(width = width)
                            )
                        }
                    }
                }
            }

            // Draw active temporary freehand stroke
            if (activeStrokePoints.isNotEmpty()) {
                val activeColor = if (selectedTool == "ERASER") Color.DarkGray else parseHexToColor(selectedColorHex, selectedTool)
                val activePath = Path().apply {
                    moveTo(activeStrokePoints[0].x, activeStrokePoints[0].y)
                    for (index in 1 until activeStrokePoints.size) {
                        lineTo(activeStrokePoints[index].x, activeStrokePoints[index].y)
                    }
                }
                drawPath(
                    path = activePath,
                    color = activeColor,
                    style = Stroke(
                        width = selectedWidth,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }

            // Draw active temporary geometric shape preview
            val start = activeShapeStart
            val end = activeShapeEnd
            if (selectedShape != null && start != null && end != null) {
                val activePreviewColor = parseHexToColor(selectedColorHex, selectedTool).copy(alpha = 0.6f)
                when (selectedShape) {
                    "LINE" -> {
                        drawLine(
                            color = activePreviewColor,
                            start = start,
                            end = end,
                            strokeWidth = selectedWidth,
                            cap = StrokeCap.Round
                        )
                    }
                    "RECTANGLE" -> {
                        val left = minOf(start.x, end.x)
                        val top = minOf(start.y, end.y)
                        val right = maxOf(start.x, end.x)
                        val bottom = maxOf(start.y, end.y)
                        drawRect(
                            color = activePreviewColor,
                            topLeft = Offset(left, top),
                            size = Size(right - left, bottom - top),
                            style = Stroke(width = selectedWidth)
                        )
                    }
                    "CIRCLE" -> {
                        val radius = kotlin.math.hypot(end.x - start.x, end.y - start.y)
                        drawCircle(
                            color = activePreviewColor,
                            radius = radius,
                            center = start,
                            style = Stroke(width = selectedWidth)
                        )
                    }
                }
            }
        }
    }
}

// Utility to parse color hex values with proper alpha styling based on tool requirements
private fun parseHexToColor(hex: String, toolType: String): Color {
    val baseColor = try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (e: Exception) {
        Color.White
    }
    return when (toolType) {
        "PENCIL" -> baseColor.copy(alpha = 0.5f)
        "MARKER" -> baseColor.copy(alpha = 0.3f)
        else -> baseColor
    }
}
