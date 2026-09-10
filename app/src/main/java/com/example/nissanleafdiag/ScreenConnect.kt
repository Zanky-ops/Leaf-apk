package com.example.nissanleafdiag

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = LeafColors.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(text = subtitle, color = LeafColors.textDim, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Spacer(Modifier.width(10.dp))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
fun ConnectScreen(onBack: () -> Unit) {
    var devices by remember { mutableStateOf(LeafService.pairedDevices()) }
    var selected by remember { mutableStateOf(devices.firstOrNull()) }
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = "Підключення", onBack = onBack) {
            IconAction(R.drawable.ic_refresh, "Оновити список") {
                devices = LeafService.pairedDevices()
                if (selected == null) selected = devices.firstOrNull()
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp)
        ) {
            Panel(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(connectionColor())
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = Session.statusText,
                        color = LeafColors.text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    ConnectionBadge()
                }
                Spacer(Modifier.height(6.dp))
                InfoRow("Адаптер", Session.adapterName ?: "—")
                InfoRow("Адреса", Session.adapterAddress ?: "—")
                InfoRow("Протокол", Session.protocolText ?: "—")
                Session.lastError?.let {
                    InfoRow("Остання помилка", it, LeafColors.red)
                }
            }

            Spacer(Modifier.height(12.dp))
            SectionTitleWithIcon(R.drawable.ic_bluetooth, "Пристрій")
            Spacer(Modifier.height(6.dp))

            Box(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(LeafColors.card)
                        .border(1.dp, LeafColors.stroke, RoundedCornerShape(14.dp))
                        .clickable { expanded = true }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selected?.let { "${it.name} — ${it.address}" }
                            ?: "Немає спарених пристроїв",
                        color = if (selected == null) LeafColors.textDim else LeafColors.text,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(text = "▾", color = LeafColors.textDim, fontSize = 16.sp)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    if (devices.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("Спаруйте ELM327 у налаштуваннях Android") },
                            onClick = { expanded = false }
                        )
                    }
                    for (device in devices) {
                        DropdownMenuItem(
                            text = { Text("${device.name} — ${device.address}") },
                            onClick = {
                                selected = device
                                expanded = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                ActionButton(
                    text = "Підключитися",
                    modifier = Modifier.weight(1f),
                    enabled = selected != null && !Session.busy && Session.conn != ConnState.Connected,
                    onClick = { selected?.let { LeafService.connect(it) } }
                )
                Spacer(Modifier.width(8.dp))
                ActionButton(
                    text = "Відключити",
                    modifier = Modifier.weight(1f),
                    tone = ButtonTone.Secondary,
                    enabled = Session.conn == ConnState.Connected,
                    onClick = { LeafService.disconnect() }
                )
            }

            Spacer(Modifier.height(12.dp))
            Panel(modifier = Modifier.fillMaxWidth()) {
                SwitchRow(
                    title = "Демо-режим",
                    subtitle = "Показує інтерфейс на згенерованих даних. Адаптер не потрібен.",
                    checked = Session.conn == ConnState.Demo,
                    onChange = { LeafService.setDemo(it) }
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                ActionButton(
                    text = "Тест адаптера",
                    modifier = Modifier.weight(1f),
                    tone = ButtonTone.Secondary,
                    enabled = Session.conn == ConnState.Connected && !Session.busy,
                    onClick = { LeafService.runElmTest() }
                )
                Spacer(Modifier.width(8.dp))
                ActionButton(
                    text = "Зчитати дані",
                    modifier = Modifier.weight(1f),
                    tone = ButtonTone.Secondary,
                    enabled = Session.conn == ConnState.Connected && !Session.busy,
                    onClick = { LeafService.refresh() }
                )
            }

            val report = Session.elmReport
            if (report != null) {
                Spacer(Modifier.height(14.dp))
                SectionTitle("Результат тесту")
                Spacer(Modifier.height(6.dp))
                Panel(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(if (report.busAlive) LeafColors.green else LeafColors.red)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = report.verdict,
                            color = if (report.busAlive) LeafColors.green else LeafColors.red,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 19.sp
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    InfoRow("Кадрів за 1.6 с", report.framesSeen.toString())
                    InfoRow("Напруга на роз'ємі", report.adapterVoltage ?: "—")
                    Spacer(Modifier.height(6.dp))
                    for (line in report.advice) {
                        Hint(text = "• $line")
                        Spacer(Modifier.height(6.dp))
                    }
                }

                Spacer(Modifier.height(10.dp))
                Panel(modifier = Modifier.fillMaxWidth()) {
                    SectionTitle("Команди")
                    Spacer(Modifier.height(6.dp))
                    for (item in report.results) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (item.ok) "✓" else "✗",
                                color = if (item.ok) LeafColors.green else LeafColors.red,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = item.command,
                                color = LeafColors.text,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        MonoText(
                            text = item.response.replace("\n", " ⏎ "),
                            modifier = Modifier.padding(start = 21.dp)
                        )
                        Text(
                            text = item.note,
                            color = LeafColors.textDim.copy(alpha = 0.75f),
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            modifier = Modifier.padding(start = 21.dp, bottom = 8.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            SectionTitle("Якщо адаптер каже CAN ERROR")
            Spacer(Modifier.height(6.dp))
            Panel(modifier = Modifier.fillMaxWidth()) {
                Hint("1. Машина має бути в READY: нога на гальмі та кнопка живлення. У режимі ACC блоки сплять і шина мовчить.")
                Spacer(Modifier.height(8.dp))
                Hint("2. CAN ERROR означає, що кадр не отримав підтвердження на шині. Це не помилка застосунку: адаптер фізично нікого не чує.")
                Spacer(Modifier.height(8.dp))
                Hint("3. NO DATA — навпаки, добра новина: шина жива, просто конкретний блок не відповів на цей запит.")
                Spacer(Modifier.height(8.dp))
                Hint("4. Leaf не підтримує звичайні OBD-режими, тому відповідь CAN ERROR саме на 0100 нічого не доводить. Дивіться на «Тест адаптера» — там рахуються реальні кадри шини.")
                Spacer(Modifier.height(8.dp))
                Hint("5. Дешеві клони ELM327 часто мають несправний приймач CAN. Перевірка на іншій машині відповідає на це за хвилину.")
            }

            Spacer(Modifier.height(14.dp))
            Panel(modifier = Modifier.fillMaxWidth()) {
                SectionTitle("Журнал")
                Spacer(Modifier.height(6.dp))
                if (Session.logLines.isEmpty()) {
                    EmptyState("Поки що порожньо")
                } else {
                    for (line in Session.logLines.takeLast(14)) {
                        MonoText(line, color = LeafColors.textDim)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
