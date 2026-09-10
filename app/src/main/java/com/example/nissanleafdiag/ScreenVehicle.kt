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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun VehicleScreen(onBack: () -> Unit) {
    val vehicle = Session.vehicle
    val battery = Session.battery

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = "Автомобіль", onBack = onBack) {
            IconAction(R.drawable.ic_refresh, "Оновити") { LeafService.refresh() }
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
                            .size(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(LeafColors.accentBlue.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_car),
                            contentDescription = null,
                            tint = LeafColors.accentBlue,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = vehicle.model ?: "Модель не визначена",
                            color = LeafColors.text,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = vehicle.vin ?: "VIN не зчитано",
                            color = LeafColors.textDim,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                StatTile("Одометр", fmtDistance(vehicle.odometerKm), modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                StatTile("Запас ходу", fmtDistance(vehicle.rangeKm), modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                StatTile("Швидкість", fmt(vehicle.speedKmh, 0), "км/год", modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                StatTile("Режим", vehicle.gear ?: "—", modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                StatTile("Повітря зовні", fmt(vehicle.ambientC), "°C", modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                StatTile("Заряд", fmt(battery.socPercent, 0), "%", modifier = Modifier.weight(1f))
            }

            Spacer(Modifier.height(12.dp))
            Panel(modifier = Modifier.fillMaxWidth()) {
                SectionTitle("Стан зчитування")
                Spacer(Modifier.height(6.dp))
                InfoRow("Оновлено", updatedAtText(vehicle.updatedAt))
                InfoRow("Одометр", "кадр 0x5C5, байти 1–3")
                InfoRow("VIN", "DID F190, читається при скануванні ECU")
                Spacer(Modifier.height(6.dp))
                if (!vehicle.hasAnything) {
                    Hint(
                        "Порожньо — це нормально до першого успішного зчитування. " +
                                "Швидкість, режим та температуру Leaf не віддає простим запитом: " +
                                "їх треба знімати з широкомовних кадрів, і вони поки не розібрані."
                    )
                } else {
                    Hint(
                        "Швидкість, режим і температура повітря заповнюються лише в демо-режимі: " +
                                "їхні кадри ще не звірені на живій машині."
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun ChargeScreen(onBack: () -> Unit) {
    val charge = Session.charge
    val battery = Session.battery

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = "Зарядка", onBack = onBack) {
            IconAction(R.drawable.ic_refresh, "Оновити") { LeafService.refresh() }
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
                            .size(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(LeafColors.amber.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_charge),
                            contentDescription = null,
                            tint = LeafColors.amber,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when (charge.charging) {
                                true -> "Йде зарядка"
                                false -> "Не заряджається"
                                null -> "Стан невідомий"
                            },
                            color = LeafColors.text,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = charge.mode ?: "режим не визначено",
                            color = LeafColors.textDim,
                            fontSize = 12.sp
                        )
                    }
                    when (charge.charging) {
                        true -> Pill("активна", LeafColors.green)
                        false -> Pill("очікування", LeafColors.textDim)
                        null -> Pill("немає даних", LeafColors.textDim)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                StatTile(
                    label = "Потужність",
                    value = fmt(charge.powerKw, 2),
                    unit = "кВт",
                    valueColor = if ((charge.powerKw ?: 0.0) > 0.5) LeafColors.green else LeafColors.text,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                StatTile("Напруга пакета", fmt(battery.packVoltage), "В", modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                StatTile("Швидкі (QC)", fmtInt(charge.quickCharges), modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                StatTile("Повільні (L1/L2)", fmtInt(charge.slowCharges), modifier = Modifier.weight(1f))
            }

            Spacer(Modifier.height(12.dp))
            Panel(modifier = Modifier.fillMaxWidth()) {
                SectionTitle("Як це рахується")
                Spacer(Modifier.height(6.dp))
                InfoRow("Стан зарядки", "знак струму з кадру 0x1DB")
                InfoRow("Потужність", "напруга × струм")
                InfoRow("Лічильники QC / L1-L2", "запит 21 01 до LBC — ще не звірено")
                Spacer(Modifier.height(6.dp))
                Hint(
                    "Лічильники зарядок лежать у тій самій відповіді LBC, що й Ah та Hx. " +
                            "Поки зсуви не перевірені на машині, застосунок їх не вигадує і показує «—»."
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
