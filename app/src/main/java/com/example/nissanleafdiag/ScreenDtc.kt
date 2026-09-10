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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DtcScreen(onBack: () -> Unit) {
    var confirmClear by remember { mutableStateOf(false) }
    val items = Session.dtcs

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = "Помилки (DTC)", onBack = onBack) {
            IconAction(R.drawable.ic_refresh, "Зчитати") { LeafService.scanDtc() }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp)
        ) {
            Panel(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                (if (items.isEmpty()) LeafColors.green else LeafColors.red)
                                    .copy(alpha = 0.14f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_warning),
                            contentDescription = null,
                            tint = if (items.isEmpty()) LeafColors.green else LeafColors.red,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when {
                                !Session.dtcScanned -> "Помилки ще не зчитувалися"
                                items.isEmpty() -> "Помилок не знайдено"
                                else -> "Знайдено помилки: ${items.size}"
                            },
                            color = if (items.isEmpty()) LeafColors.text else LeafColors.red,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Опитуються всі блоки з каталогу",
                            color = LeafColors.textDim,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            for (item in items) {
                DtcCard(item)
                Spacer(Modifier.height(8.dp))
            }

            if (Session.dtcScanned && items.isEmpty()) {
                Panel(modifier = Modifier.fillMaxWidth()) {
                    EmptyState("Жоден блок не повернув активних або збережених кодів.")
                }
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(4.dp))
            ActionButton(
                text = if (Session.busy) Session.busyLabel.ifBlank { "Зчитування…" } else "Зчитати DTC",
                modifier = Modifier.fillMaxWidth(),
                enabled = Session.isLive && !Session.busy,
                onClick = { LeafService.scanDtc() }
            )
            Spacer(Modifier.height(8.dp))
            ActionButton(
                text = "Очистити DTC",
                modifier = Modifier.fillMaxWidth(),
                tone = ButtonTone.Danger,
                enabled = Session.isLive && !Session.busy && items.isNotEmpty() && Session.allowDtcClear,
                onClick = { confirmClear = true }
            )

            if (!Session.allowDtcClear) {
                Spacer(Modifier.height(8.dp))
                Hint(
                    "Стирання вимкнене. Увімкніть його в налаштуваннях, якщо справді потрібно: " +
                            "стерті коди забирають із собою історію несправності, а несправність — ні."
                )
            }

            Spacer(Modifier.height(12.dp))
            Panel(modifier = Modifier.fillMaxWidth()) {
                SectionTitle("Як читаються коди")
                Spacer(Modifier.height(6.dp))
                InfoRow("UDS", "19 02 FF")
                InfoRow("Nissan / KWP", "18 00 FF 00")
                InfoRow("Стандартний OBD", "03")
                Spacer(Modifier.height(6.dp))
                Hint(
                    "Різні блоки Leaf розмовляють різними мовами, тому застосунок пробує три " +
                            "послідовно і бере ту, на яку блок відповів."
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = LeafColors.card,
            title = { Text("Стерти коди несправностей?", color = LeafColors.text) },
            text = {
                Text(
                    text = "Команда 14 FF FF FF піде в кожен блок зі списку. Це запис у машину. " +
                            "Якщо несправність жива, код повернеться — але історія та стоп-кадри зникнуть назавжди.",
                    color = LeafColors.textDim,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    LeafService.clearDtc()
                }) {
                    Text("Стерти", color = LeafColors.red)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text("Скасувати", color = LeafColors.textDim)
                }
            }
        )
    }
}

@Composable
private fun DtcCard(item: DtcItem) {
    Panel(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = item.code,
                color = LeafColors.text,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (item.active) {
                Pill("Активна", LeafColors.red)
            } else {
                Pill("Збережена", LeafColors.amber)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = item.description,
            color = LeafColors.textDim,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = item.ecu, color = LeafColors.accent, fontSize = 12.sp, modifier = Modifier.weight(1f))
            MonoText(item.raw)
        }
    }
}
