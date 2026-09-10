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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun statusColor(status: EcuStatus): Color = when (status.online) {
    true -> LeafColors.green
    false -> LeafColors.textDim
    null -> LeafColors.textDim
}

private fun statusText(status: EcuStatus): String = when (status.online) {
    true -> "Online"
    false -> "мовчить"
    null -> "не опитано"
}

@Composable
fun EcuScreen(onBack: () -> Unit, onOpenDetail: () -> Unit) {
    var tab by remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = "Електронні блоки (ECU)", onBack = onBack) {
            IconAction(R.drawable.ic_refresh, "Сканувати") { LeafService.scanEcus() }
        }

        Column(modifier = Modifier.padding(horizontal = 14.dp)) {
            TabPills(
                items = listOf("Список", "Ідентифікація"),
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
            ActionButton(
                text = if (Session.busy) Session.busyLabel.ifBlank { "Сканування…" } else "Сканувати блоки",
                modifier = Modifier.fillMaxWidth(),
                enabled = Session.isLive && !Session.busy,
                onClick = { LeafService.scanEcus() }
            )
            Spacer(Modifier.height(12.dp))

            if (tab == 0) {
                for (index in Session.ecus.indices) {
                    val status = Session.ecus[index]
                    EcuRow(status) {
                        Session.selectedEcuIndex = index
                        onOpenDetail()
                    }
                    Spacer(Modifier.height(8.dp))
                }
                if (!Session.ecusScanned) {
                    Spacer(Modifier.height(4.dp))
                    Panel(modifier = Modifier.fillMaxWidth()) {
                        Hint(
                            "Блоки ще не опитувалися. Сканування читає лише ідентифікацію — " +
                                    "жодних команд запису чи перепрошивки застосунок не надсилає."
                        )
                    }
                }
            } else {
                val identified = Session.ecus.filter { it.info?.hasAnything == true }
                if (identified.isEmpty()) {
                    Panel(modifier = Modifier.fillMaxWidth()) {
                        EmptyState(
                            "Жоден блок ще не повернув ідентифікацію.\n\n" +
                                    "Відсутність відповіді не означає несправність: блок може спати, " +
                                    "бути відсутнім у комплектації або не підтримувати цей DID."
                        )
                    }
                } else {
                    for (status in identified) {
                        Panel(modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = status.module.name,
                                    color = LeafColors.text,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                Pill(statusText(status), statusColor(status))
                            }
                            Spacer(Modifier.height(6.dp))
                            InfoRow("Модель / назва", status.info?.partNumber ?: "—")
                            InfoRow("Виробник", status.info?.supplier ?: "—")
                            InfoRow("Software", status.info?.software ?: "—")
                            InfoRow("Версія прошивки", status.info?.firmware ?: "—")
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun EcuRow(status: EcuStatus, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(LeafColors.card)
            .border(1.dp, LeafColors.stroke, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(statusColor(status).copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_chip),
                contentDescription = null,
                tint = statusColor(status),
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = status.module.name,
                color = LeafColors.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "ID: ${status.module.requestId} → ${status.module.responseId}",
                color = LeafColors.textDim,
                fontSize = 11.sp
            )
        }
        Pill(statusText(status), statusColor(status))
    }
}

@Composable
fun EcuDetailScreen(onBack: () -> Unit) {
    val index = Session.selectedEcuIndex
    val status = Session.selectedEcu()

    if (status == null) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(title = "Деталі ECU", onBack = onBack)
            Column(modifier = Modifier.padding(horizontal = 14.dp)) {
                Panel(modifier = Modifier.fillMaxWidth()) {
                    EmptyState("Блок не вибрано")
                }
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = "Деталі ECU", onBack = onBack)

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
                            .size(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(statusColor(status).copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_chip),
                            contentDescription = null,
                            tint = statusColor(status),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = status.module.name,
                            color = LeafColors.text,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = status.module.note.ifBlank { "діагностичний блок" },
                            color = LeafColors.textDim,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "ID: ${status.module.requestId} → ${status.module.responseId}",
                            color = LeafColors.textDim,
                            fontSize = 11.sp
                        )
                    }
                    Pill(statusText(status), statusColor(status))
                }
            }

            Spacer(Modifier.height(12.dp))
            Panel(modifier = Modifier.fillMaxWidth()) {
                SectionTitle("Ідентифікація")
                Spacer(Modifier.height(6.dp))
                InfoRow("Модель / назва", status.info?.partNumber ?: "—")
                InfoRow("Виробник", status.info?.supplier ?: "—")
                InfoRow("Hardware", status.info?.hardware ?: "—")
                InfoRow("Software", status.info?.software ?: "—")
                InfoRow("Версія прошивки", status.info?.firmware ?: "—")
                InfoRow("Діагностична версія", status.info?.diagVersion ?: "—")
                InfoRow("VIN", status.info?.vin ?: "—")
                status.lastVerdict?.let {
                    InfoRow("Останній обмін", Elm327.verdictText(it))
                }
            }

            Spacer(Modifier.height(12.dp))
            Panel(modifier = Modifier.fillMaxWidth()) {
                SectionTitle("Сирі дані")
                Spacer(Modifier.height(6.dp))
                val raw = status.info?.raw
                if (raw.isNullOrBlank()) {
                    EmptyState("Обміну ще не було")
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(LeafColors.bg)
                            .padding(10.dp)
                    ) {
                        MonoText(raw)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            ActionButton(
                text = "Оновити дані",
                modifier = Modifier.fillMaxWidth(),
                enabled = Session.isLive && !Session.busy,
                onClick = { LeafService.identifyOne(index) }
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}
