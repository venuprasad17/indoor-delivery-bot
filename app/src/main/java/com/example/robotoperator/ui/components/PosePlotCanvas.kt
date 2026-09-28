package com.example.robotoperator.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
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
    modifier: Modifier = Modifier,
    heightDp: Int = 240
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("pose_plot_canvas")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Pose plot (2D)",
                color = Slate700,
                fontSize = 13.sp,
                fontFamily = FontFamily.Default
            )
            Text(
                text = "Trail since screen opened",
                color = Slate400,
                fontSize = 11.sp
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(heightDp.dp)
                .background(PureWhite, shape = RoundedCornerShape(6.dp))
                .border(1.dp, Slate200, shape = RoundedCornerShape(6.dp))
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            ) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val centerX = canvasWidth / 2f
                val centerY = canvasHeight / 2f

                // Scale: 40 pixels per meter
                val pixelsPerMeter = 40f

                // Calculate offset so current robot pose stays near center
                val robotX = currentPose.x.toFloat()
                val robotY = currentPose.y.toFloat()
                val robotTheta = currentPose.theta.toFloat()

                // Coordinate transform: X is right, Y is up (standard robot / ROS frame)
                fun worldToScreen(wx: Float, wy: Float): Offset {
                    val sx = centerX + (wx - robotX) * pixelsPerMeter
                    val sy = centerY - (wy - robotY) * pixelsPerMeter
                    return Offset(sx, sy)
                }

                // 1. Draw Grid Lines (1 meter intervals)
                val gridSpacing = pixelsPerMeter
                val startX = (centerX % gridSpacing)
                var gx = startX
                while (gx < canvasWidth) {
                    drawLine(
                        color = Slate100,
                        start = Offset(gx, 0f),
                        end = Offset(gx, canvasHeight),
                        strokeWidth = 1f
                    )
                    gx += gridSpacing
                }

                val startY = (centerY % gridSpacing)
                var gy = startY
                while (gy < canvasHeight) {
                    drawLine(
                        color = Slate100,
                        start = Offset(0f, gy),
                        end = Offset(canvasWidth, gy),
                        strokeWidth = 1f
                    )
                    gy += gridSpacing
                }

                // 2. Center Crosshairs (origin at current robot view center)
                drawLine(
                    color = Slate300,
                    start = Offset(0f, centerY),
                    end = Offset(canvasWidth, centerY),
                    strokeWidth = 1f
                )
                drawLine(
                    color = Slate300,
                    start = Offset(centerX, 0f),
                    end = Offset(centerX, canvasHeight),
                    strokeWidth = 1f
                )

                // 3. Draw Trail Path
                if (poseTrail.size > 1) {
                    val path = Path()
                    val firstPt = worldToScreen(poseTrail[0].x.toFloat(), poseTrail[0].y.toFloat())
                    path.moveTo(firstPt.x, firstPt.y)

                    for (i in 1 until poseTrail.size) {
                        val pt = worldToScreen(poseTrail[i].x.toFloat(), poseTrail[i].y.toFloat())
                        path.lineTo(pt.x, pt.y)
                    }

                    drawPath(
                        path = path,
                        color = PrimaryBlue.copy(alpha = 0.6f),
                        style = Stroke(width = 2.5f, cap = StrokeCap.Round)
                    )
                }

                // 4. Draw Current Robot Position & Heading Arrow
                val robotScreenPos = Offset(centerX, centerY)

                // Robot base circle
                drawCircle(
                    color = PrimaryBlueLight,
                    radius = 16f,
                    center = robotScreenPos
                )
                drawCircle(
                    color = PrimaryBlue,
                    radius = 8f,
                    center = robotScreenPos
                )

                // Heading arrow: length 24px in direction of robotTheta (radians)
                // In math coords: angle 0 is +X (right), PI/2 is +Y (up).
                // Screen coords: +Y is down, so dy = -sin(theta)
                val arrowLen = 22f
                val arrowTip = Offset(
                    robotScreenPos.x + arrowLen * cos(robotTheta),
                    robotScreenPos.y - arrowLen * sin(robotTheta)
                )

                drawLine(
                    color = StatusGreenText,
                    start = robotScreenPos,
                    end = arrowTip,
                    strokeWidth = 3.5f,
                    cap = StrokeCap.Round
                )

                // Arrow head wings
                val wingLen = 7f
                val wingAngle = 0.5f
                val wing1 = Offset(
                    arrowTip.x - wingLen * cos(robotTheta - wingAngle),
                    arrowTip.y + wingLen * sin(robotTheta - wingAngle)
                )
                val wing2 = Offset(
                    arrowTip.x - wingLen * cos(robotTheta + wingAngle),
                    arrowTip.y + wingLen * sin(robotTheta + wingAngle)
                )

                drawLine(
                    color = StatusGreenText,
                    start = arrowTip,
                    end = wing1,
                    strokeWidth = 3f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = StatusGreenText,
                    start = arrowTip,
                    end = wing2,
                    strokeWidth = 3f,
                    cap = StrokeCap.Round
                )
            }

            // Bottom-right scale indicator & coordinates badge
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .background(Slate900.copy(alpha = 0.75f), shape = RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "X: ${currentPose.formattedX()} Y: ${currentPose.formattedY()} θ: ${currentPose.formattedTheta()} rad",
                    color = PureWhite,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Top-left scale rule
            Text(
                text = "1 grid = 1.0 m",
                color = Slate400,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            )
        }
    }
}
