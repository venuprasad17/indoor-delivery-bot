package com.example.robotoperator.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.robotoperator.data.model.Pose
import com.example.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun PosePlotCanvas(
    currentPose: Pose,
    poseTrail: List<Pose>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(PureWhite)
            .border(1.dp, BorderHairline, RoundedCornerShape(6.dp))
            .padding(12.dp)
    ) {
        // Header info with coordinates & label
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "LIVE POSE 2D PLOT",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Slate500
                )
                Text(
                    text = "X: ${String.format("%.2f", currentPose.x)}m  Y: ${String.format("%.2f", currentPose.y)}m  θ: ${String.format("%.1f", Math.toDegrees(currentPose.theta))}°",
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Slate900
                )
            }
            Text(
                text = "Trail: ${poseTrail.size} pts",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = Slate400
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 2D Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(SurfaceCanvas, RoundedCornerShape(4.dp))
                .border(1.dp, BorderHairline, RoundedCornerShape(4.dp))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val centerX = canvasWidth / 2f
                val centerY = canvasHeight / 2f

                // Scale: 1 meter = 24 dp on screen
                val pixelsPerMeter = 24.dp.toPx()

                // Draw Grid (every 1 meter)
                val gridStep = pixelsPerMeter
                var gx = centerX % gridStep
                while (gx < canvasWidth) {
                    drawLine(
                        color = BorderHairline.copy(alpha = 0.8f),
                        start = Offset(gx, 0f),
                        end = Offset(gx, canvasHeight),
                        strokeWidth = 1f
                    )
                    gx += gridStep
                }
                var gy = centerY % gridStep
                while (gy < canvasHeight) {
                    drawLine(
                        color = BorderHairline.copy(alpha = 0.8f),
                        start = Offset(0f, gy),
                        end = Offset(canvasWidth, gy),
                        strokeWidth = 1f
                    )
                    gy += gridStep
                }

                // Draw Axes (X and Y centered)
                drawLine(
                    color = Slate300,
                    start = Offset(0f, centerY),
                    end = Offset(canvasWidth, centerY),
                    strokeWidth = 1.5f
                )
                drawLine(
                    color = Slate300,
                    start = Offset(centerX, 0f),
                    end = Offset(centerX, canvasHeight),
                    strokeWidth = 1.5f
                )

                // Function to convert world coords to screen
                fun toScreen(wx: Double, wy: Double): Offset {
                    val sx = centerX + (wx.toFloat() * pixelsPerMeter)
                    val sy = centerY - (wy.toFloat() * pixelsPerMeter) // Invert Y
                    return Offset(sx, sy)
                }

                // Draw Pose Trail
                if (poseTrail.size > 1) {
                    val path = Path()
                    val first = toScreen(poseTrail.first().x, poseTrail.first().y)
                    path.moveTo(first.x, first.y)
                    for (i in 1 until poseTrail.size) {
                        val pt = toScreen(poseTrail[i].x, poseTrail[i].y)
                        path.lineTo(pt.x, pt.y)
                    }
                    drawPath(
                        path = path,
                        color = ActionBlue.copy(alpha = 0.45f),
                        style = Stroke(width = 2.5f)
                    )
                }

                // Draw Current Robot Position & Heading
                val robotScreen = toScreen(currentPose.x, currentPose.y)

                // Robot Footprint Circle
                drawCircle(
                    color = ActionBlue,
                    radius = 9.dp.toPx(),
                    center = robotScreen
                )
                drawCircle(
                    color = PureWhite,
                    radius = 4.dp.toPx(),
                    center = robotScreen
                )

                // Heading Arrow (based on theta)
                val headingLength = 18.dp.toPx()
                val arrowTip = Offset(
                    x = robotScreen.x + (headingLength * cos(currentPose.theta)).toFloat(),
                    y = robotScreen.y - (headingLength * sin(currentPose.theta)).toFloat()
                )
                drawLine(
                    color = StatusAmber,
                    start = robotScreen,
                    end = arrowTip,
                    strokeWidth = 3f
                )
                drawCircle(
                    color = StatusAmber,
                    radius = 3.dp.toPx(),
                    center = arrowTip
                )
            }

            // Bottom-right scale legend
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .background(PureWhite.copy(alpha = 0.85f), RoundedCornerShape(2.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "Scale: 1m grid",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Slate600
                )
            }

            // Top-left label: Trail since screen opened
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .background(PureWhite.copy(alpha = 0.85f), RoundedCornerShape(2.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "Trail since screen opened",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Slate500
                )
            }
        }
    }
}
