package com.example.nissanleafdiag

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Увесь стан застосунку в одному місці. Екрани лише читають його й малюють. */
object Session {

    var conn by mutableStateOf(ConnState.Disconnected)
    var statusText by mutableStateOf("Не підключено")
    var adapterName by mutableStateOf<String?>(null)
    var adapterAddress by mutableStateOf<String?>(null)
    var protocolText by mutableStateOf<String?>(null)

    var busy by mutableStateOf(false)
    var busyLabel by mutableStateOf("")
    var lastError by mutableStateOf<String?>(null)

    var battery by mutableStateOf(BatteryData())
    var vehicle by mutableStateOf(VehicleData())
    var charge by mutableStateOf(ChargeData())

    val ecus = mutableStateListOf<EcuStatus>()
    val dtcs = mutableStateListOf<DtcItem>()
    val frames = mutableStateListOf<String>()
    val samples = mutableStateListOf<Sample>()
    val logLines = mutableStateListOf<String>()

    var elmReport by mutableStateOf<ElmTestReport?>(null)
    var selectedEcuIndex by mutableStateOf(-1)

    var dtcScanned by mutableStateOf(false)
    var ecusScanned by mutableStateOf(false)
    var monitorRunning by mutableStateOf(false)
    var monitorFilter by mutableStateOf("")
    var framesTotal by mutableStateOf(0)
    var csvPath by mutableStateOf<String?>(null)
    var lastSavedFile by mutableStateOf<String?>(null)

    // Налаштування
    var demoMode by mutableStateOf(false)
    var allowDtcClear by mutableStateOf(false)
    var autoRefresh by mutableStateOf(true)
    var refreshSeconds by mutableStateOf(5)
    var useMiles by mutableStateOf(false)

    val isLive: Boolean get() = conn == ConnState.Connected || conn == ConnState.Demo

    init {
        ecus.addAll(EcuCatalog.modules.map { EcuStatus(module = it) })
    }

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun log(line: String) {
        val stamped = "${timeFormat.format(Date())}  $line"
        logLines.add(stamped)
        while (logLines.size > 600) logLines.removeAt(0)
    }

    fun addFrame(line: String) {
        frames.add(line)
        while (frames.size > 400) frames.removeAt(0)
    }

    fun addSample(sample: Sample) {
        samples.add(sample)
        while (samples.size > 600) samples.removeAt(0)
    }

    fun resetVehicleData() {
        battery = BatteryData()
        vehicle = VehicleData()
        charge = ChargeData()
        dtcs.clear()
        frames.clear()
        samples.clear()
        dtcScanned = false
        ecusScanned = false
        elmReport = null
        selectedEcuIndex = -1
        for (i in ecus.indices) {
            ecus[i] = EcuStatus(module = ecus[i].module)
        }
    }

    fun updateEcu(index: Int, status: EcuStatus) {
        if (index in ecus.indices) ecus[index] = status
    }

    fun selectedEcu(): EcuStatus? = ecus.getOrNull(selectedEcuIndex)
}
