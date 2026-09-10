package com.example.nissanleafdiag

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LoggingScreen(onBack: () -> Unit) {
    var files by remember { mutableStateOf(LeafService.logFiles()) }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = "Логування", onBack = onBack) {
            IconAction(R.drawable.ic_refresh, "Оновити список") { files = LeafService.logFiles() }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp)
        ) {
            Panel(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(if (Session.csvPath != null) LeafColors.green else LeafColors.textDim)
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (Session.csvPath != null) "Запис CSV іде" else "Запис зупинено",
                            color = LeafColors.text,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Точка кожну секунду в демо, кожні ${Session.refreshSeconds} с на машині",
                            color = LeafColors.textDim,
                            fontSize = 12.sp
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    ActionButton(
                        text = "Почати запис",
                        modifier = Modifier.weight(1f),
                        enabled = Session.csvPath == null && Session.isLive,
                        onClick = {
                            LeafService.startCsv()
                            files = LeafService.logFiles()
                        }
                    )
                    Spacer(Modifier.width(8.dp))
                    ActionButton(
                        text = "Зупинити",
                        modifier = Modifier.weight(1f),
                        tone = ButtonTone.Secondary,
                        enabled = Session.csvPath != null,
                        onClick = {
                            LeafService.stopCsv()
                            files = LeafService.logFiles()
                        }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            SectionTitle("Графік заряду")
            Spacer(Modifier.height(6.dp))
            Panel(modifier = Modifier.fillMaxWidth()) {
                val socValues = Session.samples.mapNotNull { it.socPercent }
                if (socValues.size < 2) {
                    EmptyState("Точок ще замало для графіка")
                } else {
                    SparkLine(
                        values = socValues.map { (it / 100.0).toFloat() },
                        color = LeafColors.accent,
                        modifier = Modifier.fillMaxWidth().height(90.dp)
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "точок: ${socValues.size}",
                            color = LeafColors.textDim,
                            fontSize = 11.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "останнє: ${fmt(socValues.last(), 1)} %",
                            color = LeafColors.textDim,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                ActionButton(
                    text = "Зняти дамп LBC",
                    modifier = Modifier.weight(1f),
                    tone = ButtonTone.Secondary,
                    enabled = Session.conn == ConnState.Connected && !Session.busy,
                    onClick = {
                        LeafService.dumpLbc()
                        files = LeafService.logFiles()
                    }
                )
                Spacer(Modifier.width(8.dp))
                ActionButton(
                    text = "Зберегти журнал",
                    modifier = Modifier.weight(1f),
                    tone = ButtonTone.Secondary,
                    onClick = {
                        LeafService.saveTextLog()
                        files = LeafService.logFiles()
                    }
                )
            }

            Session.lastSavedFile?.let {
                Spacer(Modifier.height(8.dp))
                Hint("Останній файл: $it", color = LeafColors.accent)
            }

            Spacer(Modifier.height(12.dp))
            SectionTitle("Файли")
            Spacer(Modifier.height(6.dp))
            if (files.isEmpty()) {
                Panel(modifier = Modifier.fillMaxWidth()) {
                    EmptyState("Файлів ще немає")
                }
            } else {
                for (file in files) {
                    Panel(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = file.name,
                                    color = LeafColors.text,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${file.sizeBytes / 1024} КБ",
                                    color = LeafColors.textDim,
                                    fontSize = 11.sp
                                )
                            }
                            ActionButton(
                                text = "Видалити",
                                modifier = Modifier.width(110.dp),
                                tone = ButtonTone.Secondary,
                                onClick = {
                                    LeafService.deleteLog(file.path)
                                    files = LeafService.logFiles()
                                }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            Spacer(Modifier.height(6.dp))
            Panel(modifier = Modifier.fillMaxWidth()) {
                SectionTitle("Де лежать файли")
                Spacer(Modifier.height(6.dp))
                MonoText(LeafService.logFolder())
                Spacer(Modifier.height(6.dp))
                Hint(
                    "Папка застосунку на зовнішньому сховищі. Нічого нікуди не надсилається: " +
                            "файл лежить у телефоні, доки ви самі його не заберете."
                )
            }

            Spacer(Modifier.height(12.dp))
            Panel(modifier = Modifier.fillMaxWidth()) {
                SectionTitle("Журнал")
                Spacer(Modifier.height(6.dp))
                if (Session.logLines.isEmpty()) {
                    EmptyState("Поки що порожньо")
                } else {
                    for (line in Session.logLines.takeLast(30)) {
                        MonoText(line)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun MonitorScreen(onBack: () -> Unit) {
    var filter by remember { mutableStateOf(Session.monitorFilter) }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = "CAN Монітор", onBack = onBack)

        Column(modifier = Modifier.padding(horizontal = 14.dp)) {
            Panel(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(if (Session.monitorRunning) LeafColors.green else LeafColors.textDim)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (Session.monitorRunning) "Монітор працює" else "Монітор зупинено",
                        color = LeafColors.text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "кадрів: ${Session.framesTotal}",
                        color = LeafColors.textDim,
                        fontSize = 12.sp
                    )
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = filter,
                    onValueChange = {
                        filter = it.uppercase()
                        Session.monitorFilter = filter
                    },
                    singleLine = true,
                    label = { Text("Фільтр CAN ID, напр. 5BC (порожньо — усі)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    ActionButton(
                        text = "Старт",
                        modifier = Modifier.weight(1f),
                        enabled = Session.isLive && !Session.monitorRunning,
                        onClick = { LeafService.startMonitor(filter) }
                    )
                    Spacer(Modifier.width(8.dp))
                    ActionButton(
                        text = "Стоп",
                        modifier = Modifier.weight(1f),
                        tone = ButtonTone.Secondary,
                        enabled = Session.monitorRunning,
                        onClick = { LeafService.stopMonitor() }
                    )
                    Spacer(Modifier.width(8.dp))
                    ActionButton(
                        text = "Очистити",
                        modifier = Modifier.weight(1f),
                        tone = ButtonTone.Secondary,
                        enabled = !Session.monitorRunning && Session.frames.isNotEmpty(),
                        onClick = { Session.frames.clear() }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(LeafColors.bg)
                .padding(10.dp)
        ) {
            if (Session.frames.isEmpty()) {
                EmptyState(
                    "Кадрів немає.\n\nПасивний перегляд нічого не пише в шину — " +
                            "він лише показує, що машина розсилає сама."
                )
            } else {
                val listState = rememberLazyListState()
                LaunchedEffect(Session.frames.size) {
                    if (Session.frames.isNotEmpty()) {
                        listState.scrollToItem(Session.frames.size - 1)
                    }
                }
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(Session.frames) { line ->
                        MonoText(line, color = LeafColors.accent.copy(alpha = 0.85f))
                    }
                }
            }
        }

        Column(modifier = Modifier.padding(horizontal = 14.dp)) {
            Spacer(Modifier.height(8.dp))
            Hint(
                "Порожній монітор при підключеному адаптері — найточніша ознака того, " +
                        "що машина не в READY або адаптер не бачить шину."
            )
            Spacer(Modifier.height(14.dp))
        }
    }
}
