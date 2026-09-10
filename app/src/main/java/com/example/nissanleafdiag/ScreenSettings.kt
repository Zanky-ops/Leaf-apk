package com.example.nissanleafdiag

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = "Налаштування", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp)
        ) {
            Panel(modifier = Modifier.fillMaxWidth()) {
                SwitchRow(
                    title = "Демо-режим",
                    subtitle = "Інтерфейс на згенерованих даних, без адаптера й машини",
                    checked = Session.conn == ConnState.Demo,
                    onChange = { LeafService.setDemo(it) }
                )
                Spacer(Modifier.height(8.dp))
                SwitchRow(
                    title = "Автоматичне оновлення",
                    subtitle = "Періодично перечитувати заряд, струм і напругу",
                    checked = Session.autoRefresh,
                    onChange = { Session.autoRefresh = it }
                )
                Spacer(Modifier.height(8.dp))
                SwitchRow(
                    title = "Милі замість кілометрів",
                    subtitle = "Стосується одометра та запасу ходу",
                    checked = Session.useMiles,
                    onChange = { Session.useMiles = it }
                )
            }

            Spacer(Modifier.height(12.dp))
            SectionTitle("Період опитування")
            Spacer(Modifier.height(6.dp))
            Panel(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (seconds in listOf(2, 5, 10, 30)) {
                        ActionButton(
                            text = "$seconds с",
                            modifier = Modifier.weight(1f),
                            tone = if (Session.refreshSeconds == seconds) ButtonTone.Primary else ButtonTone.Secondary,
                            onClick = { Session.refreshSeconds = seconds }
                        )
                        if (seconds != 30) Spacer(Modifier.width(6.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Hint(
                    "Кожне оновлення — це кілька секунд прослуховування шини. " +
                            "Занадто частий період нічого не покращує, а адаптер гріється й гальмує."
                )
            }

            Spacer(Modifier.height(12.dp))
            SectionTitle("Безпека")
            Spacer(Modifier.height(6.dp))
            Panel(modifier = Modifier.fillMaxWidth()) {
                SwitchRow(
                    title = "Дозволити стирання DTC",
                    subtitle = "Єдина операція запису в машину в усьому застосунку",
                    checked = Session.allowDtcClear,
                    onChange = { Session.allowDtcClear = it }
                )
                Spacer(Modifier.height(6.dp))
                Hint(
                    "Усе інше, що робить застосунок, — читання. Жодних команд кодування, " +
                            "перепрошивки чи зміни налаштувань блоків тут немає й не планується.",
                    color = LeafColors.amber
                )
            }

            Spacer(Modifier.height(12.dp))
            ActionButton(
                text = "Скинути зчитані дані",
                modifier = Modifier.fillMaxWidth(),
                tone = ButtonTone.Secondary,
                onClick = { Session.resetVehicleData() }
            )

            Spacer(Modifier.height(12.dp))
            Panel(modifier = Modifier.fillMaxWidth()) {
                InfoRow("Версія застосунку", APP_VERSION)
                InfoRow("Профіль машини", "Nissan Leaf ZE0 / AZE0")
                InfoRow("Протокол", "ISO 15765-4, CAN 11 біт, 500 кбіт/с")
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = "Про додаток", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LogoTile(size = 76)
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Nissan Leaf Diag",
                    color = LeafColors.text,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(text = "версія $APP_VERSION", color = LeafColors.textDim, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "BENBONA1990",
                    color = LeafColors.accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(6.dp))
            Panel(modifier = Modifier.fillMaxWidth()) {
                SectionTitle("Що вміє")
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "• підключення до ELM327 по Bluetooth Classic (SPP)\n" +
                            "• заряд, напруга, струм, GIDs і 96 комірок батареї\n" +
                            "• температури батареї\n" +
                            "• ідентифікація електронних блоків\n" +
                            "• зчитування кодів несправностей трьома протоколами\n" +
                            "• пасивний перегляд шини CAN\n" +
                            "• запис CSV і дамп LBC для перевірки декодерів\n" +
                            "• демо-режим — інтерфейс без машини й адаптера",
                    color = LeafColors.textDim,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            }

            Spacer(Modifier.height(12.dp))
            Panel(modifier = Modifier.fillMaxWidth()) {
                SectionTitle("Застереження")
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Застосунок читає дані. Єдина операція запису — стирання кодів " +
                            "несправностей, і вона вимкнена за замовчуванням.\n\n" +
                            "Користуйтеся якісним адаптером: дешевий клон на шині машини " +
                            "здатний нашкодити більше, ніж будь-яка діагностична програма.\n\n" +
                            "Значення SOH, Hx та Ah поки що потребують звірки з LeafSpy на живій машині.",
                    color = LeafColors.textDim,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            }

            Spacer(Modifier.height(12.dp))
            Panel(modifier = Modifier.fillMaxWidth()) {
                SectionTitle("Джерела протоколу")
                Spacer(Modifier.height(8.dp))
                MonoText(
                    "leaf-obd.readthedocs.io\n" +
                            "github.com/sethfischer/nissan-leaf-obd-manual\n" +
                            "github.com/openvehicles/Open-Vehicle-Monitoring-System-3\n" +
                            "проєкт CanZE — розбір кадрів Leaf"
                )
            }

            Spacer(Modifier.height(18.dp))
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
}
