package com.example.nissanleafdiag

/** Версія застосунку. Тримається тут, щоб не вмикати генерацію BuildConfig. */
const val APP_VERSION = "0.5.0"

/** 1 GID ≈ 77.5 Вт·год — оцінка, прийнята спільнотою Leaf. */
const val WH_PER_GID = 77.5

enum class ConnState { Disconnected, Connecting, Connected, Demo, Failed }

/** Класифікація відповіді ELM327. Саме вона відрізняє «шини немає» від «ECU мовчить». */
enum class ElmVerdict {
    Ok,
    NoData,
    CanError,
    BusError,
    BufferFull,
    Stopped,
    UnableToConnect,
    Unknown,
    Empty
}

data class BatteryData(
    val socPercent: Double? = null,
    val sohPercent: Double? = null,
    val hxPercent: Double? = null,
    val gids: Int? = null,
    val ah: Double? = null,
    val packVoltage: Double? = null,
    val packCurrent: Double? = null,
    val aux12V: Double? = null,
    val cellsMv: List<Int> = emptyList(),
    val tempsC: List<Double> = emptyList(),
    val updatedAt: Long = 0L
) {
    val powerKw: Double?
        get() {
            val v = packVoltage
            val a = packCurrent
            return if (v != null && a != null) v * a / 1000.0 else null
        }

    val energyKwh: Double?
        get() = gids?.let { it * WH_PER_GID / 1000.0 }

    val cellMinMv: Int? get() = cellsMv.minOrNull()
    val cellMaxMv: Int? get() = cellsMv.maxOrNull()
    val cellDeltaMv: Int?
        get() {
            val lo = cellMinMv
            val hi = cellMaxMv
            return if (lo != null && hi != null) hi - lo else null
        }

    val hasAnything: Boolean
        get() = socPercent != null || sohPercent != null || gids != null ||
                packVoltage != null || cellsMv.isNotEmpty() || tempsC.isNotEmpty()
}

data class VehicleData(
    val vin: String? = null,
    val model: String? = null,
    val odometerKm: Int? = null,
    val speedKmh: Double? = null,
    val ambientC: Double? = null,
    val gear: String? = null,
    val rangeKm: Int? = null,
    val updatedAt: Long = 0L
) {
    val hasAnything: Boolean
        get() = vin != null || model != null || odometerKm != null ||
                speedKmh != null || ambientC != null || gear != null || rangeKm != null
}

data class ChargeData(
    val quickCharges: Int? = null,
    val slowCharges: Int? = null,
    val charging: Boolean? = null,
    val mode: String? = null,
    val powerKw: Double? = null,
    val plugConnected: Boolean? = null,
    val updatedAt: Long = 0L
) {
    val hasAnything: Boolean
        get() = quickCharges != null || slowCharges != null || charging != null ||
                mode != null || powerKw != null || plugConnected != null
}

data class EcuModule(
    val name: String,
    val requestId: String,
    val responseId: String,
    val note: String = ""
)

data class EcuInfo(
    val partNumber: String? = null,
    val supplier: String? = null,
    val hardware: String? = null,
    val software: String? = null,
    val firmware: String? = null,
    val diagVersion: String? = null,
    val vin: String? = null,
    val raw: String = ""
) {
    val hasAnything: Boolean
        get() = partNumber != null || supplier != null || hardware != null ||
                software != null || firmware != null || diagVersion != null || vin != null
}

/** null в [online] означає «не опитували», а не «немає». */
data class EcuStatus(
    val module: EcuModule,
    val online: Boolean? = null,
    val info: EcuInfo? = null,
    val lastVerdict: ElmVerdict? = null
)

data class DtcItem(
    val code: String,
    val description: String,
    val active: Boolean,
    val ecu: String,
    val raw: String
)

data class ElmTestResult(
    val command: String,
    val response: String,
    val ok: Boolean,
    val note: String
)

/** Точка для графіка та CSV. */
data class Sample(
    val timeMs: Long,
    val socPercent: Double?,
    val packVoltage: Double?,
    val packCurrent: Double?,
    val gids: Int?
)

data class LogFileInfo(
    val name: String,
    val sizeBytes: Long,
    val path: String
)
