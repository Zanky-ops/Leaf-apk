package com.example.nissanleafdiag

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

object LeafColors {
    val bg = Color(0xFF070B12)
    val bgTop = Color(0xFF0C1424)
    val card = Color(0xFF111A2B)
    val cardSoft = Color(0xFF16223A)
    val stroke = Color(0xFF1E2B42)
    val text = Color(0xFFE7EEF9)
    val textDim = Color(0xFF8496AE)
    val accent = Color(0xFF00E5C0)
    val accentBlue = Color(0xFF2E8DFF)
    val green = Color(0xFF3DDC84)
    val amber = Color(0xFFF5B301)
    val red = Color(0xFFFF4D5E)
}

@Composable
fun LeafTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = LeafColors.accent,
            onPrimary = LeafColors.bg,
            background = LeafColors.bg,
            onBackground = LeafColors.text,
            surface = LeafColors.card,
            onSurface = LeafColors.text,
            error = LeafColors.red
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(LeafColors.bgTop, LeafColors.bg)))
        ) {
            content()
        }
    }
}

// ------------------------------------------------------------------ форматування

fun fmt(value: Double?, digits: Int = 1): String =
    if (value == null) "—" else "%.${digits}f".format(Locale.US, value)

fun fmtInt(value: Int?): String = value?.toString() ?: "—"

fun fmtDistance(km: Int?): String {
    if (km == null) return "—"
    return if (Session.useMiles) "%,d mi".format(Locale.US, (km * 0.621371).toInt())
    else "%,d км".format(Locale.US, km)
}

// -------------------------------------------------------------------- елементи

@Composable
fun ScreenHeader(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_back),
                    contentDescription = "Назад",
                    tint = LeafColors.text,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = title,
            color = LeafColors.text,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        actions()
    }
}

@Composable
fun IconAction(iconRes: Int, description: String, tint: Color = LeafColors.textDim, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(LeafColors.card)
            .border(1.dp, LeafColors.stroke, RoundedCornerShape(16.dp))
            .padding(14.dp),
        content = content
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        color = LeafColors.textDim,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp,
        modifier = modifier
    )
}

@Composable
fun SectionTitleWithIcon(iconRes: Int, text: String, tint: Color = LeafColors.accent) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(6.dp))
        SectionTitle(text)
    }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text = text, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun InfoRow(label: String, value: String, valueColor: Color = LeafColors.text) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = LeafColors.textDim, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(
            text = value,
            color = valueColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End
        )
    }
}

@Composable
fun StatTile(
    label: String,
    value: String,
    unit: String = "",
    valueColor: Color = LeafColors.text,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(LeafColors.cardSoft)
            .border(1.dp, LeafColors.stroke, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(text = label, color = LeafColors.textDim, fontSize = 11.sp)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(text = value, color = valueColor, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            if (unit.isNotEmpty()) {
                Spacer(Modifier.width(3.dp))
                Text(
                    text = unit,
                    color = LeafColors.textDim,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
    }
}

enum class ButtonTone { Primary, Secondary, Danger }

@Composable
fun ActionButton(
    text: String,
    modifier: Modifier = Modifier,
    tone: ButtonTone = ButtonTone.Primary,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val background = when (tone) {
        ButtonTone.Primary -> LeafColors.accent
        ButtonTone.Secondary -> LeafColors.cardSoft
        ButtonTone.Danger -> LeafColors.red
    }
    val textColor = when (tone) {
        ButtonTone.Primary -> LeafColors.bg
        ButtonTone.Secondary -> LeafColors.text
        ButtonTone.Danger -> Color.White
    }
    val alpha = if (enabled) 1f else 0.35f

    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(background.copy(alpha = alpha))
            .border(
                1.dp,
                if (tone == ButtonTone.Secondary) LeafColors.stroke else Color.Transparent,
                RoundedCornerShape(14.dp)
            )
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor.copy(alpha = alpha),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
    }
}

@Composable
fun TabPills(items: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(LeafColors.card)
            .border(1.dp, LeafColors.stroke, RoundedCornerShape(14.dp))
            .padding(4.dp)
    ) {
        for (index in items.indices) {
            val active = index == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (active) LeafColors.accent.copy(alpha = 0.16f) else Color.Transparent)
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = items[index],
                    color = if (active) LeafColors.accent else LeafColors.textDim,
                    fontSize = 12.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
fun MenuItem(
    iconRes: Int,
    title: String,
    subtitle: String? = null,
    tint: Color = LeafColors.accent,
    trailing: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(LeafColors.card)
            .border(1.dp, LeafColors.stroke, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(21.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = LeafColors.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Text(text = subtitle, color = LeafColors.textDim, fontSize = 12.sp)
            }
        }
        if (trailing != null) {
            Text(text = trailing, color = LeafColors.textDim, fontSize = 12.sp)
        }
    }
}

@Composable
fun Hint(text: String, color: Color = LeafColors.textDim, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(if (text.length > 90) 46.dp else 24.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color.copy(alpha = 0.5f))
        )
        Spacer(Modifier.width(8.dp))
        Text(text = text, color = color, fontSize = 12.sp, lineHeight = 16.sp)
    }
}

@Composable
fun MonoText(text: String, modifier: Modifier = Modifier, color: Color = LeafColors.textDim) {
    Text(
        text = text,
        color = color,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        fontFamily = FontFamily.Monospace,
        modifier = modifier
    )
}

/** Дуга заряду. Порожній стан теж малюється — інакше екран виглядає зламаним. */
@Composable
fun SocGauge(percent: Double?, modifier: Modifier = Modifier) {
    val value = percent?.coerceIn(0.0, 100.0)
    val fraction = ((value ?: 0.0) / 100.0).toFloat()

    Box(modifier = modifier.size(200.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val thickness = 18.dp.toPx()
            val inset = thickness / 2f
            val arcSize = Size(size.width - thickness, size.height - thickness)

            drawArc(
                color = LeafColors.cardSoft,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = thickness, cap = StrokeCap.Round)
            )

            if (fraction > 0f) {
                drawArc(
                    brush = Brush.linearGradient(
                        listOf(LeafColors.accent, LeafColors.green, LeafColors.accentBlue)
                    ),
                    startAngle = 135f,
                    sweepAngle = 270f * fraction,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = thickness, cap = StrokeCap.Round)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (value == null) "—" else "%.0f".format(Locale.US, value),
                color = LeafColors.text,
                fontSize = 46.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (value == null) "немає даних" else "% SOC",
                color = LeafColors.textDim,
                fontSize = 13.sp
            )
        }
    }
}

/** Проста лінія графіка по точках 0..1. */
@Composable
fun SparkLine(values: List<Float>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        if (values.size < 2) return@Canvas
        val stepX = size.width / (values.size - 1).toFloat()
        var previous = Offset(0f, size.height * (1f - values[0].coerceIn(0f, 1f)))
        for (i in 1 until values.size) {
            val point = Offset(
                stepX * i,
                size.height * (1f - values[i].coerceIn(0f, 1f))
            )
            drawLine(
                color = color,
                start = previous,
                end = point,
                strokeWidth = 2.5f,
                cap = StrokeCap.Round
            )
            previous = point
        }
    }
}

@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(8.dp)
            .clip(RoundedCornerShape(50))
            .background(color)
    )
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = text,
            color = LeafColors.textDim,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )
    }
}
