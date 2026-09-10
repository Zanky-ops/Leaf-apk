package com.example.nissanleafdiag

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private val clockFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

fun updatedAtText(timeMs: Long): String =
    if (timeMs <= 0L) "дані ще не зчитувалися"
    else "оновлено о ${clockFormat.format(Date(timeMs))}"

@Composable
fun BatteryScreen(onBack: () -> Unit) {
    var tab by remember { mutableStateOf(0) }
    val battery = Session.battery

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = "Акумулятор", onBack = onBack) {
            IconAction(R.drawable.ic_refresh, "Оновити") { LeafService.refresh() }
        }

        Column(modifier = Modifier.padding(horizontal = 14.dp)) {
            TabPills(
                items = listOf("Огляд", "Комірки", "Температури"),
                selected = tab,
                onSelect = { tab = it }
            )
        }
        Spacer(Modifier.height(12.dp))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp)
        ) {
            when (tab) {
                0 -> BatteryOverview(battery)
                1 -> BatteryCells(battery)
                else -> BatteryTemps(battery)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun BatteryOverview(battery: BatteryData) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        SocGauge(percent = battery.socPercent)
    }

    Spacer(Modifier.height(8.dp))
    Text(
        text = updatedAtText(battery.updatedAt),
        color = LeafColors.textDim,
        fontSize = 11.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(14.dp))

    Row(modifier = Modifier.fillMaxWidth()) {
        StatTile(
            label = "SOH",
            value = fmt(battery.sohPercent),
            unit = "%",
            valueColor = sohColor(battery.sohPercent),
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        StatTile(
            label = "Hx",
            value = fmt(battery.hxPercent),
            unit = "%",
            modifier = Modifier.weight(1f)
        )
    }
    Spacer(Modifier.height(8.dp))
    Row(modifier = Modifier.fillMaxWidth()) {
        StatTile("GIDs", fmtInt(battery.gids), modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        StatTile("Ah", fmt(battery.ah), modifier = Modifier.weight(1f))
    }
    Spacer(Modifier.height(8.dp))
    Row(modifier = Modifier.fillMaxWidth()) {
        StatTile("Напруга", fmt(battery.packVoltage), "В", modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        StatTile(
            label = "Струм",
            value = fmt(battery.packCurrent),
            unit = "А",
            valueColor = currentColor(battery.packCurrent),
            modifier = Modifier.weight(1f)
        )
    }
    Spacer(Modifier.height(8.dp))
    Row(modifier = Modifier.fillMaxWidth()) {
        StatTile(
            label = "Потужність",
            value = fmt(battery.powerKw, 2),
            unit = "кВт",
            valueColor = currentColor(battery.packCurrent),
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        StatTile("Енергія", fmt(battery.energyKwh, 1), "кВт·год", modifier = Modifier.weight(1f))
    }

    Spacer(Modifier.height(12.dp))
    Panel(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Акумулятор 12 В",
                color = LeafColors.textDim,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${fmt(battery.aux12V)} В",
                color = LeafColors.text,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.width(10.dp))
            val aux = battery.aux12V
            when {
                aux == null -> Pill("немає даних", LeafColors.textDim)
                aux < 11.8 -> Pill("низька", LeafColors.red)
                aux < 12.4 -> Pill("на межі", LeafColors.amber)
                else -> Pill("в нормі", LeafColors.green)
            }
        }
    }

    if (!battery.hasAnything) {
        Spacer(Modifier.height(12.dp))
        Panel(modifier = Modifier.fillMaxWidth()) {
            EmptyState(
                "Даних ще немає.\n\nПідключіть ELM327 і натисніть «Зчитати дані», " +
                        "або увімкніть демо-режим на екрані «Підключення», щоб подивитися інтерфейс без машини."
            )
        }
    }

    Spacer(Modifier.height(12.dp))
    Panel(modifier = Modifier.fillMaxWidth()) {
        SectionTitle("Звідки беруться значення")
        Spacer(Modifier.height(8.dp))
        InfoRow("SOC", "широкомовний кадр 0x55B")
        InfoRow("GIDs", "широкомовний кадр 0x5BC")
        InfoRow("Струм і напруга", "широкомовний кадр 0x1DB")
        InfoRow("Комірки", "запит 21 02 до LBC (79B → 7BB)")
        InfoRow("Температури", "запит 21 04 до LBC")
        InfoRow("Hx, Ah", "запит 21 01 до LBC")
        InfoRow("12 В", "поки не розібрано — лише в демо-режимі")
        Spacer(Modifier.height(8.dp))
        Hint(
            "SOH, Hx та Ah розібрані за відкритими джерелами і ще не звірені на живій машині. " +
                    "Якщо число виглядає дивним — зніміть дамп LBC на екрані «Логування» і звіряйте за ним."
        )
    }
}

fun sohColor(soh: Double?): Color = when {
    soh == null -> LeafColors.text
    soh >= 85.0 -> LeafColors.green
    soh >= 70.0 -> LeafColors.amber
    else -> LeafColors.red
}

fun currentColor(current: Double?): Color = when {
    current == null -> LeafColors.text
    current > 1.0 -> LeafColors.green
    current < -1.0 -> LeafColors.accentBlue
    else -> LeafColors.text
}

@Composable
private fun BatteryCells(battery: BatteryData) {
    val cells = battery.cellsMv
    if (cells.isEmpty()) {
        Panel(modifier = Modifier.fillMaxWidth()) {
            EmptyState(
                "Напруг комірок ще немає.\n\nВони приходять у відповідь на запит 21 02 до LBC — " +
                        "натисніть «Оновити» вгорі при підключеному адаптері."
            )
        }
        return
    }

    val min = cells.minOrNull() ?: 0
    val max = cells.maxOrNull() ?: 0
    val average = cells.sum() / cells.size

    SectionTitleWithIcon(R.drawable.ic_cells, "Напруга комірок (В)")
    Spacer(Modifier.height(8.dp))

    Panel(modifier = Modifier.fillMaxWidth()) {
        val columns = 8
        val rows = (cells.size + columns - 1) / columns
        for (row in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (column in 0 until columns) {
                    val index = row * columns + column
                    Box(modifier = Modifier.weight(1f).padding(1.dp)) {
                        if (index < cells.size) {
                            CellChip(index + 1, cells[index], average)
                        }
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(10.dp))
    Row(modifier = Modifier.fillMaxWidth()) {
        StatTile("Мінімум", "%.3f".format(Locale.US, min / 1000.0), "В", modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        StatTile("Максимум", "%.3f".format(Locale.US, max / 1000.0), "В", modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        StatTile(
            label = "Δ",
            value = "%.3f".format(Locale.US, (max - min) / 1000.0),
            unit = "В",
            valueColor = deltaColor(max - min),
            modifier = Modifier.weight(1f)
        )
    }

    Spacer(Modifier.height(10.dp))
    Panel(modifier = Modifier.fillMaxWidth()) {
        InfoRow("Комірок зчитано", "${cells.size}")
        InfoRow("Середня", "%.3f В".format(Locale.US, average / 1000.0))
        Spacer(Modifier.height(4.dp))
        Hint(
            "Колір комірки — відхилення від середньої: до 30 мВ норма, до 60 мВ варто спостерігати, " +
                    "більше 60 мВ — комірка виділяється з пакета."
        )
    }
}

fun deltaColor(deltaMv: Int): Color = when {
    deltaMv <= 30 -> LeafColors.green
    deltaMv <= 60 -> LeafColors.amber
    else -> LeafColors.red
}

@Composable
private fun CellChip(number: Int, valueMv: Int, averageMv: Int) {
    val deviation = abs(valueMv - averageMv)
    val color = deltaColor(deviation)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
            .padding(vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "$number", color = LeafColors.textDim, fontSize = 7.sp)
        Text(
            text = "%.2f".format(Locale.US, valueMv / 1000.0),
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun BatteryTemps(battery: BatteryData) {
    val temps = battery.tempsC
    if (temps.isEmpty()) {
        Panel(modifier = Modifier.fillMaxWidth()) {
            EmptyState(
                "Температур ще немає.\n\nВони приходять у відповідь на запит 21 04 до LBC."
            )
        }
        return
    }

    SectionTitleWithIcon(R.drawable.ic_temp, "Датчики температури батареї", LeafColors.amber)
    Spacer(Modifier.height(8.dp))

    for (index in temps.indices) {
        val value = temps[index]
        Panel(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Датчик ${index + 1}",
                    color = LeafColors.textDim,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${fmt(value)} °C",
                    color = tempColor(value),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(8.dp))
            TempBar(value)
        }
        Spacer(Modifier.height(8.dp))
    }

    val hottest = temps.maxOrNull() ?: 0.0
    val coldest = temps.minOrNull() ?: 0.0
    Row(modifier = Modifier.fillMaxWidth()) {
        StatTile("Найнижча", fmt(coldest), "°C", modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        StatTile("Найвища", fmt(hottest), "°C", valueColor = tempColor(hottest), modifier = Modifier.weight(1f))
    }

    Spacer(Modifier.height(10.dp))
    Panel(modifier = Modifier.fillMaxWidth()) {
        Hint(
            "Для Leaf небезпечна саме верхня межа: від 45 °C батарея помітно втрачає ресурс, " +
                    "а швидка зарядка гарячого пакета гріє його ще сильніше."
        )
    }
}

fun tempColor(value: Double): Color = when {
    value >= 45.0 -> LeafColors.red
    value >= 35.0 -> LeafColors.amber
    value <= 0.0 -> LeafColors.accentBlue
    else -> LeafColors.green
}

@Composable
private fun TempBar(value: Double) {
    val fraction = (((value + 20.0) / 80.0).coerceIn(0.0, 1.0)).toFloat()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(LeafColors.cardSoft)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(tempColor(value))
        )
    }
}
