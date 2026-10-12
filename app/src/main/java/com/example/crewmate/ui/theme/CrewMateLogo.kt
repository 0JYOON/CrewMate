package com.example.crewmate.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun CrewMateLogo(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
) {
    val context = LocalContext.current
    val customDrawableId = remember(context) {
        context.resources.getIdentifier("crewmate_logo", "drawable", context.packageName)
    }

    if (customDrawableId != 0) {
        androidx.compose.material3.Surface(
            shape = RoundedCornerShape(size * 0.28f),
            color = Color.Transparent,
            border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xB3FFFFFF)),
            modifier = modifier.size(size)
        ) {
            Image(
                painter = painterResource(id = customDrawableId),
                contentDescription = "CrewMate Logo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        return
    }

    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        val navyBg = Color(0xFF041E42)
        val blueStripe = Color(0xFF0D74E7)
        val goldAccent = Color(0xFFF6BE2C)

        // 1. Deep Navy Squircle Background
        drawRoundRect(
            color = navyBg,
            cornerRadius = CornerRadius(w * 0.24f, h * 0.24f),
            size = Size(w, h),
        )

        // 2. Aerodynamic KTX Upper Roof Crest (상단 흰색 유선형 루프 라인)
        val roofCrest = Path().apply {
            moveTo(w * 0.35f, h * 0.34f)
            cubicTo(w * 0.46f, h * 0.28f, w * 0.58f, h * 0.255f, w * 0.70f, h * 0.275f)
            cubicTo(w * 0.58f, h * 0.285f, w * 0.48f, h * 0.31f, w * 0.40f, h * 0.34f)
            close()
        }
        drawPath(path = roofCrest, color = Color.White)

        // 3. Main KTX Streamlined Body (좌측으로 뻗은 유선형 선두부)
        val trainBody = Path().apply {
            moveTo(w * 0.57f, h * 0.32f)
            lineTo(w * 0.36f, h * 0.335f)
            cubicTo(w * 0.26f, h * 0.38f, w * 0.16f, h * 0.49f, w * 0.115f, h * 0.585f)
            cubicTo(w * 0.10f, h * 0.625f, w * 0.115f, h * 0.655f, w * 0.16f, h * 0.665f)
            cubicTo(w * 0.22f, h * 0.675f, w * 0.30f, h * 0.67f, w * 0.36f, h * 0.665f)
            cubicTo(w * 0.25f, h * 0.66f, w * 0.17f, h * 0.65f, w * 0.15f, h * 0.64f)
            cubicTo(w * 0.29f, h * 0.635f, w * 0.45f, h * 0.57f, w * 0.58f, h * 0.47f)
            close()
        }
        drawPath(path = trainBody, color = Color.White)

        // 4. Cockpit Window Cutout (운전실 창문)
        val windowCutout = Path().apply {
            moveTo(w * 0.485f, h * 0.36f)
            lineTo(w * 0.35f, h * 0.37f)
            cubicTo(w * 0.29f, h * 0.41f, w * 0.23f, h * 0.465f, w * 0.21f, h * 0.505f)
            cubicTo(w * 0.205f, h * 0.52f, w * 0.22f, h * 0.525f, w * 0.25f, h * 0.525f)
            lineTo(w * 0.36f, h * 0.515f)
            cubicTo(w * 0.40f, h * 0.48f, w * 0.45f, h * 0.42f, w * 0.485f, h * 0.36f)
            close()
        }
        drawPath(path = windowCutout, color = navyBg)

        // 5. Blue Dynamic Stripe on KTX Nose (파란색 유선형 도색 라인)
        val stripePath = Path().apply {
            moveTo(w * 0.118f, h * 0.605f)
            cubicTo(w * 0.27f, h * 0.595f, w * 0.43f, h * 0.52f, w * 0.555f, h * 0.425f)
            lineTo(w * 0.575f, h * 0.455f)
            cubicTo(w * 0.44f, h * 0.555f, w * 0.28f, h * 0.625f, w * 0.125f, h * 0.635f)
            close()
        }
        drawPath(path = stripePath, color = blueStripe)

        // =====================================================================
        // Railway Crew Officer (우측 전면 승무원)
        // =====================================================================

        // 6. Dark Navy Separation Halo around Cap & Head
        val capHalo = Path().apply {
            moveTo(w * 0.52f, h * 0.39f)
            cubicTo(w * 0.52f, h * 0.27f, w * 0.89f, h * 0.27f, w * 0.89f, h * 0.39f)
            cubicTo(w * 0.89f, h * 0.44f, w * 0.85f, h * 0.47f, w * 0.82f, h * 0.48f)
            lineTo(w * 0.59f, h * 0.48f)
            cubicTo(w * 0.55f, h * 0.47f, w * 0.52f, h * 0.44f, w * 0.52f, h * 0.39f)
            close()
        }
        drawPath(path = capHalo, color = navyBg)

        // 7. Peaked Cap White Outline & Navy Crown (승무원 정모)
        val capOutline = Path().apply {
            moveTo(w * 0.545f, h * 0.395f)
            cubicTo(w * 0.545f, h * 0.295f, w * 0.865f, h * 0.295f, w * 0.865f, h * 0.395f)
            cubicTo(w * 0.865f, h * 0.425f, w * 0.845f, h * 0.445f, w * 0.825f, h * 0.45f)
            lineTo(w * 0.585f, h * 0.45f)
            cubicTo(w * 0.565f, h * 0.445f, w * 0.545f, h * 0.425f, w * 0.545f, h * 0.395f)
            close()
        }
        drawPath(path = capOutline, color = navyBg)
        drawPath(
            path = capOutline,
            color = Color.White,
            style = Stroke(width = w * 0.022f, join = StrokeJoin.Round),
        )

        // 8. Gold Emblem Circle on Cap Center (정모 중앙 금빛 원형 배지)
        drawCircle(
            color = goldAccent,
            radius = w * 0.026f,
            center = Offset(w * 0.705f, h * 0.365f),
        )

        // 9. Gold Cap Band (정모 금테)
        val goldBand = Path().apply {
            moveTo(w * 0.588f, h * 0.442f)
            quadraticBezierTo(w * 0.705f, h * 0.405f, w * 0.822f, h * 0.442f)
            lineTo(w * 0.822f, h * 0.464f)
            quadraticBezierTo(w * 0.705f, h * 0.427f, w * 0.588f, h * 0.464f)
            close()
        }
        drawPath(path = goldBand, color = goldAccent)

        // 10. Face & Ears Outline (얼굴 윤곽선 및 양쪽 귀)
        val leftEar = Path().apply {
            moveTo(w * 0.61f, h * 0.495f)
            cubicTo(w * 0.57f, h * 0.485f, w * 0.57f, h * 0.555f, w * 0.615f, h * 0.555f)
            close()
        }
        val rightEar = Path().apply {
            moveTo(w * 0.80f, h * 0.495f)
            cubicTo(w * 0.84f, h * 0.485f, w * 0.84f, h * 0.555f, w * 0.795f, h * 0.555f)
            close()
        }
        drawPath(path = leftEar, color = Color.White)
        drawPath(path = rightEar, color = Color.White)

        val faceOutline = Path().apply {
            moveTo(w * 0.612f, h * 0.488f)
            quadraticBezierTo(w * 0.705f, h * 0.505f, w * 0.798f, h * 0.488f)
            lineTo(w * 0.792f, h * 0.545f)
            cubicTo(w * 0.785f, h * 0.61f, w * 0.745f, h * 0.642f, w * 0.705f, h * 0.642f)
            cubicTo(w * 0.665f, h * 0.642f, w * 0.625f, h * 0.61f, w * 0.618f, h * 0.545f)
            close()
        }
        drawPath(path = faceOutline, color = navyBg)
        drawPath(
            path = faceOutline,
            color = Color.White,
            style = Stroke(width = w * 0.02f, join = StrokeJoin.Round),
        )

        // 11. White Shirt Collar & Neck (제복 와이셔츠 깃)
        val shirtV = Path().apply {
            moveTo(w * 0.65f, h * 0.615f)
            lineTo(w * 0.625f, h * 0.655f)
            lineTo(w * 0.705f, h * 0.79f)
            lineTo(w * 0.785f, h * 0.655f)
            lineTo(w * 0.76f, h * 0.615f)
            cubicTo(w * 0.74f, h * 0.655f, w * 0.67f, h * 0.655f, w * 0.65f, h * 0.615f)
            close()
        }
        drawPath(path = shirtV, color = Color.White)

        // Neck inside collar
        val neckDark = Path().apply {
            moveTo(w * 0.66f, h * 0.615f)
            cubicTo(w * 0.68f, h * 0.645f, w * 0.73f, h * 0.645f, w * 0.75f, h * 0.615f)
            lineTo(w * 0.745f, h * 0.64f)
            cubicTo(w * 0.725f, h * 0.66f, w * 0.685f, h * 0.66f, w * 0.665f, h * 0.64f)
            close()
        }
        drawPath(path = neckDark, color = navyBg)

        // 12. Blue Tie (파란색 넥타이)
        val tiePath = Path().apply {
            moveTo(w * 0.705f, h * 0.668f)
            lineTo(w * 0.736f, h * 0.692f)
            lineTo(w * 0.720f, h * 0.715f)
            lineTo(w * 0.730f, h * 0.745f)
            lineTo(w * 0.705f, h * 0.792f)
            lineTo(w * 0.680f, h * 0.745f)
            lineTo(w * 0.690f, h * 0.715f)
            lineTo(w * 0.674f, h * 0.692f)
            close()
        }
        drawPath(path = tiePath, color = blueStripe)

        // 13. Gold Shoulder Epaulettes (양쪽 금빛 견장)
        val leftEpaulette = Path().apply {
            moveTo(w * 0.512f, h * 0.666f)
            lineTo(w * 0.590f, h * 0.644f)
            lineTo(w * 0.598f, h * 0.668f)
            lineTo(w * 0.520f, h * 0.690f)
            close()
        }
        val rightEpaulette = Path().apply {
            moveTo(w * 0.820f, h * 0.644f)
            lineTo(w * 0.898f, h * 0.666f)
            lineTo(w * 0.890f, h * 0.690f)
            lineTo(w * 0.812f, h * 0.668f)
            close()
        }
        drawPath(
            path = leftEpaulette,
            color = goldAccent,
            style = Stroke(width = w * 0.012f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        drawPath(path = leftEpaulette, color = goldAccent)
        drawPath(
            path = rightEpaulette,
            color = goldAccent,
            style = Stroke(width = w * 0.012f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        drawPath(path = rightEpaulette, color = goldAccent)
    }
}