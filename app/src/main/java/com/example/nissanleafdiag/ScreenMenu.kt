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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

fun connectionColor(): Color = when (Session.conn) {
    ConnState.Connected -> LeafColors.green
    ConnState.Demo -> LeafColors.amber
    ConnState.Connecting -> LeafColors.accent
    ConnState.Failed -> LeafColors.red
    ConnState.Disconnected -> LeafColors.textDim
}

fun connectionLabel(): String = when (Session.conn) {
    ConnState.Connected -> "Підключено"
    ConnState.Demo -> "Демо-режим"
    ConnState.Connecting -> "Підключення"
    ConnState.Failed -> "Помилка"
    ConnState.Disconnected -> "Не підключено"
}

@Composable
fun ConnectionBadge(modifier: Modifier = Modifier) {
    Pill(text = connectionLabel(), color = connectionColor(), modifier = modifier)
}

@Composable
fun LogoTile(size: Int = 62) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    listOf(LeafColors.accent.copy(alpha = 0.22f), LeafColors.accentBlue.copy(alpha = 0.22f))
                )
            )
            .border(1.dp, LeafColors.accent.copy(alpha = 0.45f), RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_leaf),
            contentDescription = null,
            tint = LeafColors.accent,
            modifier = Modifier.size((size * 0.5).dp)
        )
    }
}

@Composable
fun MenuScreen(onOpen: (Screen) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp)
    ) {
        Spacer(Modifier.height(22.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            LogoTile()
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "Nissan Leaf Diag",
                    color = LeafColors.text,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "BENBONA1990 · v$APP_VERSION",
                    color = LeafColors.textDim,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Panel(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(connectionColor())
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = Session.statusText,
                        color = LeafColors.text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = Session.adapterName ?: "адаптер не вибрано",
                        color = LeafColors.textDim,
                        fontSize = 12.sp
                    )
                }
                ConnectionBadge()
            }
            if (Session.busy) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = Session.busyLabel.ifBlank { "Виконується операція…" },
                    color = LeafColors.accent,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        val soc = Session.battery.socPercent
        MenuItem(
            iconRes = R.drawable.ic_plug,
            title = "Підключення",
            subtitle = "ELM327 через Bluetooth, демо-режим, тест адаптера",
            tint = LeafColors.accent,
            onClick = { onOpen(Screen.Connect) }
        )
        Spacer(Modifier.height(8.dp))
        MenuItem(
            iconRes = R.drawable.ic_battery,
            title = "Акумулятор",
            subtitle = "SOC, SOH, GIDs, 96 комірок, температури",
            tint = LeafColors.green,
            trailing = if (soc != null) "${fmt(soc, 0)} %" else null,
            onClick = { onOpen(Screen.Battery) }
        )
        Spacer(Modifier.height(8.dp))
        MenuItem(
            iconRes = R.drawable.ic_car,
            title = "Автомобіль",
            subtitle = "VIN, одометр, швидкість, режим",
            tint = LeafColors.accentBlue,
            onClick = { onOpen(Screen.Vehicle) }
        )
        Spacer(Modifier.height(8.dp))
        MenuItem(
            iconRes = R.drawable.ic_charge,
            title = "Зарядка",
            subtitle = "стан, потужність, лічильники L1/L2/QC",
            tint = LeafColors.amber,
            onClick = { onOpen(Screen.Charge) }
        )
        Spacer(Modifier.height(8.dp))
        MenuItem(
            iconRes = R.drawable.ic_chip,
            title = "Електронні блоки (ECU)",
            subtitle = "модель, виробник, версія прошивки",
            tint = LeafColors.accent,
            trailing = if (Session.ecusScanned) "${Session.ecus.count { it.online == true }} / ${Session.ecus.size}" else null,
            onClick = { onOpen(Screen.Ecu) }
        )
        Spacer(Modifier.height(8.dp))
        MenuItem(
            iconRes = R.drawable.ic_warning,
            title = "Помилки (DTC)",
            subtitle = "зчитування та стирання кодів",
            tint = LeafColors.red,
            trailing = if (Session.dtcScanned) "${Session.dtcs.size}" else null,
            onClick = { onOpen(Screen.Dtc) }
        )
        Spacer(Modifier.height(8.dp))
        MenuItem(
            iconRes = R.drawable.ic_chart,
            title = "Логування",
            subtitle = "запис CSV і графік показників",
            tint = LeafColors.green,
            onClick = { onOpen(Screen.Logging) }
        )
        Spacer(Modifier.height(8.dp))
        MenuItem(
            iconRes = R.drawable.ic_can,
            title = "CAN Монітор",
            subtitle = "сирі кадри шини, фільтр за ID",
            tint = LeafColors.accentBlue,
            trailing = if (Session.monitorRunning) "працює" else null,
            onClick = { onOpen(Screen.Monitor) }
        )
        Spacer(Modifier.height(8.dp))
        MenuItem(
            iconRes = R.drawable.ic_settings,
            title = "Налаштування",
            subtitle = "демо, одиниці, безпека, період опитування",
            tint = LeafColors.textDim,
            onClick = { onOpen(Screen.Settings) }
        )
        Spacer(Modifier.height(8.dp))
        MenuItem(
            iconRes = R.drawable.ic_info,
            title = "Про додаток",
            subtitle = "версія, джерела протоколу, застереження",
            tint = LeafColors.textDim,
            onClick = { onOpen(Screen.About) }
        )

        Spacer(Modifier.height(22.dp))
        Text(
            text = "BENBONA1990",
            color = LeafColors.accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = "Drive Electric · Think Green · Better Tomorrow",
            color = LeafColors.textDim,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(24.dp))
    }
}
