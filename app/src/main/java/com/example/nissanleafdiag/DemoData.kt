package com.example.nissanleafdiag

import kotlin.math.abs
import kotlin.math.sin

/**
 * Демонстраційні дані.
 *
 * Потрібні не для краси: без машини і без CAN інакше неможливо ні подивитися
 * інтерфейс, ні перевірити, що екрани правильно поводяться з даними.
 * Значення підібрані під AZE0 24 кВт·год і між собою узгоджені:
 * 96 × 4.01 В ≈ 385 В, 281 GID × 77.5 Вт·год ≈ 21.8 кВт·год.
 */
object DemoData {

    fun battery(tick: Int): BatteryData {
        val wave = sin(tick / 6.0)
        val soc = 78.4 - (tick % 60) * 0.05
        val current = -12.6 + wave * 6.0
        val voltage = 384.5 + wave * 1.8

        val cells = ArrayList<Int>(96)
        for (i in 0 until 96) {
            val jitter = ((i * 37 + tick * 3) % 19) - 9
            cells.add(4008 + jitter + if (i == 61) -22 else 0)
        }

        val temps = listOf(
            18.5 + wave * 0.4,
            19.0 + wave * 0.3,
            19.5 + wave * 0.5,
            18.0 + wave * 0.2
        )

        return BatteryData(
            socPercent = soc,
            sohPercent = 91.2,
            hxPercent = 72.3,
            gids = 281 - (tick % 60) / 6,
            ah = 57.4,
            packVoltage = voltage,
            packCurrent = current,
            aux12V = 13.2 + wave * 0.1,
            cellsMv = cells,
            tempsC = temps,
            updatedAt = System.currentTimeMillis()
        )
    }

    fun vehicle(tick: Int): VehicleData = VehicleData(
        vin = "SJNFAAZE0U6012345",
        model = "Nissan Leaf AZE0 (24 кВт·год)",
        odometerKm = 96_431 + tick / 40,
        speedKmh = abs(sin(tick / 9.0)) * 47.0,
        ambientC = 16.0 + sin(tick / 20.0),
        gear = if ((tick / 10) % 2 == 0) "D" else "P",
        rangeKm = 118 - (tick % 60) / 4,
        updatedAt = System.currentTimeMillis()
    )

    fun charge(tick: Int): ChargeData = ChargeData(
        quickCharges = 214,
        slowCharges = 1_186,
        charging = (tick / 15) % 4 == 0,
        mode = if ((tick / 15) % 4 == 0) "L2 — 230 В змінного струму" else "не заряджається",
        powerKw = if ((tick / 15) % 4 == 0) 3.3 else 0.0,
        plugConnected = (tick / 15) % 4 == 0,
        updatedAt = System.currentTimeMillis()
    )

    fun ecus(): List<EcuStatus> = EcuCatalog.modules.mapIndexed { index, module ->
        val online = index % 7 != 5
        EcuStatus(
            module = module,
            online = online,
            info = if (online) ecuInfo(module) else null,
            lastVerdict = if (online) ElmVerdict.Ok else ElmVerdict.NoData
        )
    }

    fun ecuInfo(module: EcuModule): EcuInfo {
        val suffix = module.requestId.uppercase()
        return EcuInfo(
            partNumber = "237E0-3NF0B",
            supplier = "NISSAN",
            hardware = "237E0-3NF0B",
            software = "237E0-3NF0C",
            firmware = "04.02",
            diagVersion = "02.11",
            vin = if (module.name == "VCM") "SJNFAAZE0U6012345" else null,
            raw = """
                [F18A] ${module.responseId}0662 F18A 4E 49 53
                [F187] ${module.responseId}1012 62 F1 87 32 33 37
                [F194] ${module.responseId}1012 62 F1 94 32 33 37
                демо-режим, блок $suffix
            """.trimIndent()
        )
    }

    fun dtcs(): List<DtcItem> = listOf(
        DtcItem(
            code = "P0AA6",
            description = DtcCatalog.describe("P0AA6"),
            active = true,
            ecu = "LBC / BMS",
            raw = "0A A6 status 09"
        ),
        DtcItem(
            code = "P0A7A",
            description = DtcCatalog.describe("P0A7A"),
            active = false,
            ecu = "LBC / BMS",
            raw = "0A 7A status 08"
        ),
        DtcItem(
            code = "U1000",
            description = DtcCatalog.describe("U1000"),
            active = false,
            ecu = "VCM",
            raw = "10 00 status 08"
        )
    )

    private val demoIds = listOf("1DA", "1DB", "1DC", "55B", "5BC", "5B3", "5C5", "1F2", "284", "35D")

    fun frame(tick: Int): String {
        val id = demoIds[tick % demoIds.size]
        val sb = StringBuilder(id)
        for (i in 0 until 8) {
            sb.append(' ').append("%02X".format((tick * 7 + i * 29 + id.hashCode()) and 0xFF))
        }
        return sb.toString()
    }
}
