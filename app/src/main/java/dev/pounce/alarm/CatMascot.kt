package dev.pounce.alarm

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.*

/** Original Pounce tabby; no downloaded artwork or animation runtime. */
enum class CatMood { SLEEPY, WAKE, THINK, PLAY, WALK, CELEBRATE }
private enum class CatMotion { BLINK, EAR_TWITCH, TAIL_CURL, KNEAD, YAWN, STRETCH, BOUNCE, DANCE, PEEK }

@Composable
fun CatMascot(
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
    awake: Boolean = false,
    mood: CatMood = if (awake) CatMood.WAKE else CatMood.SLEEPY
) {
    val variant = remember(mood) {
        when (mood) {
            CatMood.SLEEPY -> listOf(CatMotion.YAWN, CatMotion.KNEAD, CatMotion.STRETCH, CatMotion.BLINK)
            CatMood.THINK -> listOf(CatMotion.PEEK, CatMotion.EAR_TWITCH, CatMotion.TAIL_CURL)
            CatMood.WALK -> listOf(CatMotion.KNEAD, CatMotion.BOUNCE, CatMotion.TAIL_CURL)
            CatMood.CELEBRATE -> listOf(CatMotion.DANCE, CatMotion.BOUNCE, CatMotion.STRETCH)
            CatMood.PLAY -> CatMotion.entries.toList()
            CatMood.WAKE -> listOf(CatMotion.EAR_TWITCH, CatMotion.STRETCH, CatMotion.PEEK, CatMotion.TAIL_CURL)
        }.random()
    }
    // Do not run an infinite transition at all when the user requests reduced motion.
    val phase = if (reduceMotion) 0f else catPhase()
    val moving = !reduceMotion
    Canvas(modifier.semantics { contentDescription = "Pounce the orange cat" }) {
        val unit = min(size.width / 260f, size.height / 230f)
        translate((size.width - 260 * unit) / 2f, (size.height - 230 * unit) / 2f) {
            scale(unit, unit, Offset.Zero) {
                val orange = Color(0xFFDF8650)
                val darkOrange = Color(0xFFBE6138)
                val fur = Color(0xFFF7D39A)
                val pink = Color(0xFFF1AAA1)
                val cycle = sin(phase)
                val lift = if (moving && variant == CatMotion.BOUNCE) abs(sin(phase * 2)) * 10 else 0f
                val stretch = if (moving && variant == CatMotion.STRETCH) max(0f, sin(phase)) else 0f
                val knead = if (moving && (variant == CatMotion.KNEAD || mood == CatMood.WALK)) sin(phase * 3) * 5 else 0f
                val dancing = if (moving && variant == CatMotion.DANCE) sin(phase * 2) * 6 else 0f
                val tailWave = if (moving) sin(phase + .7f) * (if (variant == CatMotion.TAIL_CURL) 24 else 7) else 0f
                drawCircle(Color(0xFFF4D998).copy(alpha = .72f), 65f, Offset(160f, 84f))
                drawCircle(Color(0xFFE8EBDD), 10f, Offset(50f, 160f))
                drawCircle(Color(0xFFF4D998), 4f, Offset(216f, 50f))
                drawOval(Plum.copy(alpha = .07f), Offset(64f + lift / 2, 203f), Size(140f - lift, 12f))
                val spark = if (moving) cycle * 3 else 0f
                fun star(x: Float, y: Float, radius: Float, color: Color) {
                    val path = Path().apply {
                        moveTo(x, y - radius); quadraticBezierTo(x + 1f, y - 1f, x + radius, y)
                        quadraticBezierTo(x + 1f, y + 1f, x, y + radius)
                        quadraticBezierTo(x - 1f, y + 1f, x - radius, y)
                        quadraticBezierTo(x - 1f, y - 1f, x, y - radius); close()
                    }
                    drawPath(path, color)
                }
                if (mood == CatMood.CELEBRATE || mood == CatMood.PLAY) {
                    star(35f, 68f + spark, 9f, Color(0xFFDCA957))
                    star(227f, 104f - spark, 7f, Color(0xFF899473))
                    star(209f, 27f + spark, 5f, pink)
                }
                translate(0f, -lift - stretch * 3) {
                    rotate(dancing, Offset(133f, 191f)) {
                        val tail = Path().apply {
                            moveTo(177f, 178f)
                            cubicTo(232f, 206f, 253f, 144f + tailWave, 218f, 141f + tailWave)
                            if (variant == CatMotion.TAIL_CURL) quadraticBezierTo(201f, 140f + tailWave, 213f, 155f + tailWave)
                        }
                        drawPath(tail, darkOrange, style = Stroke(18f, cap = StrokeCap.Round))
                        drawOval(orange, Offset(76f, 122f - stretch * 6), Size(116f, 82f + stretch * 6))
                        drawOval(fur, Offset(106f, 155f - stretch * 4), Size(57f, 45f + stretch * 4))
                        val tilt = when {
                            !moving -> 0f
                            variant == CatMotion.PEEK -> cycle * 8
                            variant == CatMotion.EAR_TWITCH -> sin(phase * 2) * 2
                            else -> cycle * 1.5f
                        }
                        translate(0f, -stretch * 9) {
                            rotate(tilt, Offset(133f, 119f)) {
                                val twitch = if (moving && variant == CatMotion.EAR_TWITCH) max(0f, sin(phase * 3)) * 8 else 0f
                                val ears = Path().apply {
                                    moveTo(71f, 89f); lineTo(67f - twitch, 36f)
                                    quadraticBezierTo(66f - twitch, 22f, 80f, 33f)
                                    lineTo(111f, 63f); lineTo(156f, 63f); lineTo(190f + twitch / 2, 32f)
                                    quadraticBezierTo(203f + twitch / 2, 24f, 201f, 41f)
                                    lineTo(192f, 94f); close()
                                }
                                drawPath(ears, orange)
                                val inner = Path().apply {
                                    moveTo(77f - twitch / 2, 44f); lineTo(82f, 78f); lineTo(103f, 66f); close()
                                    moveTo(189f + twitch / 4, 44f); lineTo(166f, 67f); lineTo(187f, 78f); close()
                                }
                                drawPath(inner, pink)
                                drawOval(orange, Offset(65f, 59f), Size(136f, 110f))
                                drawOval(fur, Offset(85f, 108f), Size(100f, 56f))
                                for (x in listOf(120f, 132f, 144f)) {
                                    drawLine(darkOrange, Offset(x, 69f), Offset(x - 2, 83f), 4.5f, cap = StrokeCap.Round)
                                }
                                val blink = moving && (phase > 5.55f && phase < 5.86f || variant == CatMotion.BLINK && phase > 2.8f && phase < 3.08f)
                                val yawn = if (moving && variant == CatMotion.YAWN) max(0f, sin(phase - 1f)) else 0f
                                val closed = blink || mood == CatMood.SLEEPY && variant == CatMotion.KNEAD || yawn > .4f || mood == CatMood.CELEBRATE
                                for (x in listOf(105f, 160f)) {
                                    if (closed) {
                                        drawArc(Plum, if (mood == CatMood.CELEBRATE) 180f else 0f, 180f, false,
                                            Offset(x - 8f, 95f), Size(16f, 11f), style = Stroke(3.2f, cap = StrokeCap.Round))
                                    } else {
                                        val eyeShift = if (variant == CatMotion.PEEK && moving) cycle * 2 else 0f
                                        drawOval(Plum, Offset(x - 4f + eyeShift, 97f), Size(8f, 13f))
                                        drawCircle(Cream, 1.7f, Offset(x - 1f + eyeShift, 99f))
                                    }
                                }
                                drawOval(pink.copy(alpha = .8f), Offset(86f, 120f), Size(20f, 8f))
                                drawOval(pink.copy(alpha = .8f), Offset(160f, 120f), Size(20f, 8f))
                                val nose = Path().apply {
                                    moveTo(126f, 119f); quadraticBezierTo(133f, 115f, 140f, 119f)
                                    lineTo(133f, 125f); close()
                                }
                                drawPath(nose, Plum)
                                if (yawn > .1f) {
                                    drawOval(Plum, Offset(128f - yawn * 2, 128f), Size(10f + yawn * 4, 6f + yawn * 14))
                                    drawOval(pink, Offset(131f - yawn, 132f + yawn * 8), Size(5f + yawn * 2, 3f + yawn * 3))
                                } else {
                                    drawArc(Plum, 0f, 180f, false, Offset(120f, 123f), Size(13f, 10f), style = Stroke(2f))
                                    drawArc(Plum, 0f, 180f, false, Offset(133f, 123f), Size(13f, 10f), style = Stroke(2f))
                                }
                                for (dy in listOf(0f, 8f)) {
                                    drawLine(Plum.copy(alpha = .4f), Offset(91f, 127f + dy), Offset(54f, 122f + dy * 2), 1.4f)
                                    drawLine(Plum.copy(alpha = .4f), Offset(175f, 127f + dy), Offset(211f, 122f + dy * 2), 1.4f)
                                }
                            }
                        }
                        val pawLift = if (moving && variant == CatMotion.DANCE) (sin(phase * 2) + 1) * 10 else stretch * 12
                        drawOval(darkOrange, Offset(79f, 187f + knead - pawLift), Size(42f, 19f))
                        drawOval(darkOrange, Offset(149f, 187f - knead - pawLift), Size(42f, 19f))
                        for (x in listOf(91f, 101f)) drawLine(orange, Offset(x, 192f + knead - pawLift), Offset(x, 197f + knead - pawLift), 2f, cap = StrokeCap.Round)
                        for (x in listOf(161f, 171f)) drawLine(orange, Offset(x, 192f - knead - pawLift), Offset(x, 197f - knead - pawLift), 2f, cap = StrokeCap.Round)
                    }
                }
                if (mood == CatMood.SLEEPY) {
                    val zY = if (moving) -max(0f, cycle) * 5 else 0f
                    val sleepy = Path().apply { moveTo(216f, 74f + zY); lineTo(228f, 74f + zY); lineTo(216f, 86f + zY); lineTo(228f, 86f + zY) }
                    drawPath(sleepy, Leaf.copy(alpha = .7f), style = Stroke(2.5f, cap = StrokeCap.Round))
                }
            }
        }
    }
}

@Composable private fun catPhase(): Float {
    val transition = rememberInfiniteTransition(label = "Pounce's playful motion")
    val phase by transition.animateFloat(0f, (2 * PI).toFloat(),
        infiniteRepeatable(tween(4800, easing = LinearEasing)), label = "cat gesture")
    return phase
}
