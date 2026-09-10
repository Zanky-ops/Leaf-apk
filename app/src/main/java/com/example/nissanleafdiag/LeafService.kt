package com.example.nissanleafdiag

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

data class BtDevice(val name: String, val address: String, val device: BluetoothDevice)

/**
 * Уся робота з адаптером.
 *
 * Один потік [io] на всі команди ELM327: адаптер послідовний, і дві операції
 * одночасно неминуче переплутали б заголовки ATSH між собою.
 * Таймер [ticker] лише підкидає задачі в цей потік.
 */
@SuppressLint("MissingPermission")
object LeafService {

    private var appContext: Context? = null
    private val main = Handler(Looper.getMainLooper())
    private val io: ExecutorService = Executors.newSingleThreadExecutor()
    private val ticker: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private var tickerHandle: ScheduledFuture<*>? = null

    private var socket: BluetoothSocket? = null
    private var elm: Elm327? = null
    private var tickCount = 0

    private fun ui(block: () -> Unit) {
        main.post { block() }
    }

    private const val PREFS = "leaf_diag"

    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
            loadSettings()
        }
        if (tickerHandle == null) {
            tickerHandle = ticker.scheduleWithFixedDelay({
                try {
                    onTick()
                } catch (_: Exception) {
                }
            }, 1, 1, TimeUnit.SECONDS)
        }
    }

    private fun submit(label: String, showBusy: Boolean = true, block: () -> Unit) {
        io.execute {
            if (showBusy) ui {
                Session.busy = true
                Session.busyLabel = label
            }
            try {
                block()
            } catch (e: Exception) {
                ui { Session.log("$label — помилка: ${e.message ?: e.toString()}") }
            } finally {
                if (showBusy) ui {
                    Session.busy = false
                    Session.busyLabel = ""
                }
            }
        }
    }

    // ------------------------------------------------------------------ Bluetooth

    fun pairedDevices(): List<BtDevice> {
        val ctx = appContext ?: return emptyList()
        return try {
            val manager = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = manager?.adapter ?: return emptyList()
            if (!adapter.isEnabled) return emptyList()
            adapter.bondedDevices
                .map { BtDevice(it.name ?: "Без назви", it.address, it) }
                .sortedBy { it.name }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun bluetoothEnabled(): Boolean {
        val ctx = appContext ?: return false
        return try {
            val manager = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            manager?.adapter?.isEnabled == true
        } catch (e: Exception) {
            false
        }
    }

    fun connect(target: BtDevice) {
        if (Session.busy) return
        setDemo(false)
        ui {
            Session.conn = ConnState.Connecting
            Session.statusText = "Підключення до ${target.name}…"
            Session.lastError = null
        }
        submit("Підключення") {
            closeSocketQuietly()
            try {
                val sock = target.device.createRfcommSocketToServiceRecord(Elm327.SPP_UUID)
                sock.connect()
                socket = sock
                val e = Elm327(sock.inputStream, sock.outputStream)
                elm = e
                val protocol = LeafProtocol.initAdapter(e) { line -> ui { Session.log(line) } }
                ui {
                    Session.conn = ConnState.Connected
                    Session.adapterName = target.name
                    Session.adapterAddress = target.address
                    Session.protocolText = protocol.ifBlank { "невідомий" }
                    Session.statusText = "ELM327 підключено"
                    Session.log("Підключено до ${target.name} (${target.address})")
                }
                doRefreshFull()
            } catch (e: Exception) {
                closeSocketQuietly()
                ui {
                    Session.conn = ConnState.Failed
                    Session.statusText = "Не вдалося підключитися"
                    Session.lastError = e.message ?: "невідома помилка Bluetooth"
                    Session.log("Помилка підключення: ${e.message}")
                }
            }
        }
    }

    fun disconnect() {
        Session.monitorRunning = false
        submit("Відключення") {
            closeSocketQuietly()
            ui {
                Session.conn = ConnState.Disconnected
                Session.statusText = "Не підключено"
                Session.adapterName = null
                Session.adapterAddress = null
                Session.protocolText = null
                Session.log("Відключено")
            }
        }
    }

    private fun closeSocketQuietly() {
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        socket = null
        elm = null
    }

    // ---------------------------------------------------------------- демо-режим

    fun setDemo(on: Boolean) {
        if (on == (Session.conn == ConnState.Demo)) {
            Session.demoMode = on
            return
        }
        Session.demoMode = on
        Session.monitorRunning = false
        if (on) {
            submit("Демо-режим", showBusy = false) { closeSocketQuietly() }
            Session.resetVehicleData()
            Session.conn = ConnState.Demo
            Session.statusText = "Демо-режим: дані генерує застосунок"
            Session.adapterName = "Демо-джерело"
            Session.adapterAddress = null
            Session.protocolText = "ISO 15765-4 (CAN 11/500) — імітація"
            Session.ecus.clear()
            Session.ecus.addAll(DemoData.ecus())
            Session.ecusScanned = true
            Session.dtcs.clear()
            Session.dtcs.addAll(DemoData.dtcs())
            Session.dtcScanned = true
            Session.log("Увімкнено демо-режим — жодних звернень до адаптера")
        } else {
            Session.conn = ConnState.Disconnected
            Session.statusText = "Не підключено"
            Session.adapterName = null
            Session.protocolText = null
            Session.resetVehicleData()
            Session.ecus.clear()
            Session.ecus.addAll(EcuCatalog.modules.map { EcuStatus(module = it) })
            Session.log("Демо-режим вимкнено")
        }
    }

    // -------------------------------------------------------------------- таймер

    private fun onTick() {
        tickCount++
        when (Session.conn) {
            ConnState.Demo -> demoTick(tickCount)
            ConnState.Connected -> {
                val period = Session.refreshSeconds.coerceAtLeast(2)
                if (Session.autoRefresh && !Session.monitorRunning && !Session.busy &&
                    tickCount % period == 0
                ) {
                    submit("Оновлення", showBusy = false) { doRefreshFast() }
                }
            }
            else -> Unit
        }
    }

    private fun demoTick(t: Int) {
        val battery = DemoData.battery(t)
        val vehicle = DemoData.vehicle(t)
        val charge = DemoData.charge(t)
        val sample = Sample(
            timeMs = System.currentTimeMillis(),
            socPercent = battery.socPercent,
            packVoltage = battery.packVoltage,
            packCurrent = battery.packCurrent,
            gids = battery.gids
        )
        ui {
            Session.battery = battery
            Session.vehicle = vehicle
            Session.charge = charge
            Session.addSample(sample)
            if (Session.monitorRunning) {
                Session.addFrame(DemoData.frame(t))
                Session.framesTotal += 1
            }
        }
        if (Logger.isWriting()) Logger.appendCsv(sample)
    }

    // ------------------------------------------------------------------ зчитування

    fun refresh() {
        if (Session.conn == ConnState.Demo) return
        if (elm == null) {
            Session.log("Немає підключення до адаптера")
            return
        }
        submit("Зчитування даних") { doRefreshFull() }
    }

    private fun alive(): Boolean = true

    private fun doRefreshFast() {
        val e = elm ?: return
        val soc = LeafDecoder.socFrom55B(LeafProtocol.sniff(e, "55B", 600) { alive() })
        val iv = LeafDecoder.currentAndVoltageFrom1DB(LeafProtocol.sniff(e, "1DB", 600) { alive() })
        val gids = LeafDecoder.gidsFrom5BC(LeafProtocol.sniff(e, "5BC", 600) { alive() })

        val current = iv.first
        val voltage = iv.second
        ui {
            val old = Session.battery
            Session.battery = old.copy(
                socPercent = soc ?: old.socPercent,
                packCurrent = current ?: old.packCurrent,
                packVoltage = voltage ?: old.packVoltage,
                gids = gids ?: old.gids,
                updatedAt = System.currentTimeMillis()
            )
            Session.charge = Session.charge.copy(
                charging = current?.let { it > 1.0 },
                powerKw = if (current != null && voltage != null) current * voltage / 1000.0 else null,
                updatedAt = System.currentTimeMillis()
            )
        }
        val sample = Sample(System.currentTimeMillis(), soc, voltage, current, gids)
        ui { Session.addSample(sample) }
        if (Logger.isWriting()) Logger.appendCsv(sample)
    }

    private fun doRefreshFull() {
        val e = elm ?: return

        ui { Session.busyLabel = "Слухаю широкомовні кадри" }
        val f55B = LeafProtocol.sniff(e, "55B", 700) { alive() }
        val f5BC = LeafProtocol.sniff(e, "5BC", 700) { alive() }
        val f5B3 = LeafProtocol.sniff(e, "5B3", 700) { alive() }
        val f5C5 = LeafProtocol.sniff(e, "5C5", 700) { alive() }
        val f1DB = LeafProtocol.sniff(e, "1DB", 700) { alive() }

        val soc = LeafDecoder.socFrom55B(f55B)
        val gids = LeafDecoder.gidsFrom5BC(f5BC)
        val soh = LeafDecoder.sohFrom5B3(f5B3)
        val odo = LeafDecoder.odometerFrom5C5(f5C5)
        val iv = LeafDecoder.currentAndVoltageFrom1DB(f1DB)

        val frameCount = f55B.size + f5BC.size + f5B3.size + f5C5.size + f1DB.size
        ui { Session.log("Широкомовні кадри: прийнято $frameCount") }

        ui { Session.busyLabel = "Опитую LBC" }
        val lbc = EcuCatalog.modules.first { it.requestId == "79B" }

        val g1 = LeafProtocol.request(e, lbc, "2101", 3500)
        val g2 = LeafProtocol.request(e, lbc, "2102", 4500)
        val g4 = LeafProtocol.request(e, lbc, "2104", 3500)

        val group1 = g1.pdu?.let { if (IsoTp.isPositive(it, 0x21)) LeafDecoder.group1(it) else null }
        val cells = g2.pdu?.let { if (IsoTp.isPositive(it, 0x21)) LeafDecoder.cellsFromGroup2(it) else null }
            ?: emptyList()
        val temps = g4.pdu?.let { if (IsoTp.isPositive(it, 0x21)) LeafDecoder.tempsFromGroup4(it) else null }
            ?: emptyList()

        if (!g1.ok) ui { Session.log("LBC 21 01 → ${Elm327.verdictText(g1.verdict)}") }

        val current = iv.first
        val voltage = iv.second ?: group1?.packVoltage

        ui {
            val old = Session.battery
            Session.battery = old.copy(
                socPercent = soc ?: old.socPercent,
                sohPercent = soh ?: old.sohPercent,
                hxPercent = group1?.hxPercent ?: old.hxPercent,
                gids = gids ?: old.gids,
                ah = group1?.ah ?: old.ah,
                packVoltage = voltage ?: old.packVoltage,
                packCurrent = current ?: old.packCurrent,
                cellsMv = if (cells.isNotEmpty()) cells else old.cellsMv,
                tempsC = if (temps.isNotEmpty()) temps else old.tempsC,
                updatedAt = System.currentTimeMillis()
            )
            Session.vehicle = Session.vehicle.copy(
                odometerKm = odo ?: Session.vehicle.odometerKm,
                updatedAt = System.currentTimeMillis()
            )
            Session.charge = Session.charge.copy(
                charging = current?.let { it > 1.0 },
                powerKw = if (current != null && voltage != null) current * voltage / 1000.0 else null,
                updatedAt = System.currentTimeMillis()
            )
            Session.statusText =
                if (frameCount > 0 || g1.ok) "Дані оновлено" else "Машина не відповідає"
        }
    }

    /** Сирий дамп LBC — щоб було з чим звіряти зсуви декодера. */
    fun dumpLbc() {
        val ctx = appContext ?: return
        if (Session.conn == ConnState.Demo) {
            Session.log("У демо-режимі дамп не має сенсу")
            return
        }
        val e = elm ?: run {
            Session.log("Немає підключення до адаптера")
            return
        }
        submit("Дамп LBC") {
            val lbc = EcuCatalog.modules.first { it.requestId == "79B" }
            val sb = StringBuilder()
            sb.append("Nissan Leaf Diag ").append(APP_VERSION).append(" — дамп LBC\n")
            sb.append("Адаптер: ").append(Session.adapterName ?: "—").append('\n')
            sb.append("Протокол: ").append(Session.protocolText ?: "—").append("\n\n")

            for (group in listOf("2101", "2102", "2103", "2104", "2105", "2106", "2161")) {
                val r = LeafProtocol.request(e, lbc, group, 4500)
                sb.append("=== $group ===\n")
                sb.append(r.raw.ifBlank { "—" }).append('\n')
                sb.append("вирок: ").append(Elm327.verdictText(r.verdict)).append('\n')
                r.pdu?.let { sb.append("PDU: ").append(Hex.toHex(it)).append('\n') }
                sb.append('\n')
                ui { Session.log("Дамп $group → ${Elm327.verdictText(r.verdict)}") }
            }

            for (id in listOf("55B", "5BC", "5B3", "5C5", "1DB", "1F2", "5C0")) {
                val lines = LeafProtocol.sniff(e, id, 700) { alive() }
                sb.append("=== broadcast $id (${lines.size} кадр.) ===\n")
                for (line in lines.take(12)) {
                    sb.append(line.id).append(' ').append(Hex.toHex(line.data)).append('\n')
                }
                sb.append('\n')
            }

            val path = Logger.saveText(ctx, "lbc_dump", sb.toString())
            ui {
                Session.lastSavedFile = path
                Session.log(path?.let { "Дамп збережено: $it" } ?: "Не вдалося зберегти дамп")
            }
        }
    }

    // ---------------------------------------------------------------------- ECU

    fun scanEcus() {
        if (Session.conn == ConnState.Demo) {
            Session.ecus.clear()
            Session.ecus.addAll(DemoData.ecus())
            Session.ecusScanned = true
            return
        }
        val e = elm ?: run {
            Session.log("Немає підключення до адаптера")
            return
        }
        submit("Сканування ECU") {
            for (index in EcuCatalog.modules.indices) {
                val module = EcuCatalog.modules[index]
                ui { Session.busyLabel = "Опитую ${module.name}" }
                val (online, verdict) = EcuCatalog.probe(e, module)
                val info = if (online) EcuCatalog.identify(e, module) else null
                ui {
                    Session.updateEcu(
                        index,
                        EcuStatus(module = module, online = online, info = info, lastVerdict = verdict)
                    )
                    if (online && info?.vin != null) {
                        Session.vehicle = Session.vehicle.copy(vin = info.vin)
                    }
                }
            }
            ui {
                Session.ecusScanned = true
                val found = Session.ecus.count { it.online == true }
                Session.log("Сканування ECU завершено, відповіли: $found з ${EcuCatalog.modules.size}")
            }
        }
    }

    fun identifyOne(index: Int) {
        val status = Session.ecus.getOrNull(index) ?: return
        if (Session.conn == ConnState.Demo) {
            Session.updateEcu(
                index,
                status.copy(online = true, info = DemoData.ecuInfo(status.module), lastVerdict = ElmVerdict.Ok)
            )
            return
        }
        val e = elm ?: run {
            Session.log("Немає підключення до адаптера")
            return
        }
        submit("Ідентифікація ${status.module.name}") {
            val (online, verdict) = EcuCatalog.probe(e, status.module)
            val info = if (online) EcuCatalog.identify(e, status.module) else null
            ui {
                Session.updateEcu(
                    index,
                    status.copy(online = online, info = info, lastVerdict = verdict)
                )
            }
        }
    }

    // ---------------------------------------------------------------------- DTC

    fun scanDtc() {
        if (Session.conn == ConnState.Demo) {
            Session.dtcs.clear()
            Session.dtcs.addAll(DemoData.dtcs())
            Session.dtcScanned = true
            return
        }
        val e = elm ?: run {
            Session.log("Немає підключення до адаптера")
            return
        }
        submit("Зчитування помилок") {
            val found = ArrayList<DtcItem>()
            for (module in EcuCatalog.modules) {
                ui { Session.busyLabel = "Помилки: ${module.name}" }
                val (online, _) = EcuCatalog.probe(e, module)
                if (!online) continue
                val (items, result) = LeafProtocol.readDtc(e, module)
                if (items.isEmpty()) {
                    ui { Session.log("${module.name}: помилок немає (${Elm327.verdictText(result.verdict)})") }
                } else {
                    ui { Session.log("${module.name}: знайдено ${items.size}") }
                }
                found.addAll(items)
            }
            ui {
                Session.dtcs.clear()
                Session.dtcs.addAll(found)
                Session.dtcScanned = true
                Session.log("Зчитування помилок завершено: ${found.size}")
            }
        }
    }

    /** Стирання. Викликається лише після підтвердження у діалозі. */
    fun clearDtc() {
        if (!Session.allowDtcClear) {
            Session.log("Стирання вимкнено в налаштуваннях")
            return
        }
        if (Session.conn == ConnState.Demo) {
            Session.dtcs.clear()
            Session.log("Демо-режим: список помилок очищено")
            return
        }
        val e = elm ?: run {
            Session.log("Немає підключення до адаптера")
            return
        }
        val targets = Session.dtcs.map { it.ecu }.distinct()
        submit("Стирання помилок") {
            for (name in targets) {
                val module = EcuCatalog.modules.firstOrNull { it.name == name } ?: continue
                val r = LeafProtocol.clearDtc(e, module)
                ui { Session.log("Стирання ${module.name} → ${Elm327.verdictText(r.verdict)}") }
            }
            ui { Session.log("Перевірте результат повторним зчитуванням") }
        }
    }

    // ------------------------------------------------------------- CAN монітор

    fun startMonitor(filter: String) {
        if (Session.monitorRunning) return
        Session.frames.clear()
        Session.framesTotal = 0
        Session.monitorRunning = true

        if (Session.conn == ConnState.Demo) {
            Session.log("Демо-монітор: кадри генерує застосунок")
            return
        }
        val e = elm ?: run {
            Session.monitorRunning = false
            Session.log("Немає підключення до адаптера")
            return
        }
        val clean = filter.trim().uppercase()
        submit("CAN монітор", showBusy = false) {
            if (Hex.isHexId(clean)) e.command("ATCRA $clean", 900) else e.command("ATCRA", 900)
            e.forgetTarget()

            val buffer = ArrayList<String>()
            var lastFlush = System.currentTimeMillis()
            var total = 0

            e.monitor(3_600_000, { Session.monitorRunning }) { line ->
                total++
                if (buffer.size < 120) buffer.add(Hex.pretty(line))
                val now = System.currentTimeMillis()
                if (now - lastFlush > 250) {
                    val chunk = ArrayList(buffer)
                    val seen = total
                    buffer.clear()
                    lastFlush = now
                    ui {
                        for (item in chunk) Session.addFrame(item)
                        Session.framesTotal = seen
                    }
                }
            }
            e.forgetTarget()
            ui {
                Session.monitorRunning = false
                Session.log("Монітор зупинено, кадрів: $total")
            }
        }
    }

    fun stopMonitor() {
        Session.monitorRunning = false
    }

    // ------------------------------------------------------------ тест адаптера

    fun runElmTest() {
        if (Session.conn == ConnState.Demo) {
            Session.log("Демо-режим: тест адаптера недоступний")
            return
        }
        val e = elm ?: run {
            Session.log("Спочатку підключіться до ELM327")
            return
        }
        submit("Тест адаптера") {
            val report = ElmTest.run(e) { true }
            ui {
                Session.elmReport = report
                Session.log(report.verdict)
            }
        }
    }

    // ------------------------------------------------------------------ логи

    fun startCsv() {
        val ctx = appContext ?: return
        val path = Logger.startCsv(ctx)
        Session.csvPath = path
        Session.log(path?.let { "Запис CSV: $it" } ?: "Не вдалося створити файл CSV")
    }

    fun stopCsv() {
        val path = Logger.stopCsv()
        Session.csvPath = null
        Session.log(path?.let { "Запис зупинено: $it" } ?: "Запис не вівся")
    }

    fun logFiles(): List<LogFileInfo> {
        val ctx = appContext ?: return emptyList()
        return Logger.list(ctx)
    }

    fun logFolder(): String {
        val ctx = appContext ?: return "—"
        return Logger.folder(ctx)
    }

    fun deleteLog(path: String) {
        Logger.delete(path)
    }

    // ------------------------------------------------------------ налаштування

    private fun loadSettings() {
        val ctx = appContext ?: return
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        Session.allowDtcClear = prefs.getBoolean("allow_dtc_clear", false)
        Session.autoRefresh = prefs.getBoolean("auto_refresh", true)
        Session.useMiles = prefs.getBoolean("use_miles", false)
        Session.refreshSeconds = prefs.getInt("refresh_seconds", 5)
    }

    fun saveSettings() {
        val ctx = appContext ?: return
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("allow_dtc_clear", Session.allowDtcClear)
            .putBoolean("auto_refresh", Session.autoRefresh)
            .putBoolean("use_miles", Session.useMiles)
            .putInt("refresh_seconds", Session.refreshSeconds)
            .apply()
    }

    fun saveTextLog(): String? {
        val ctx = appContext ?: return null
        val text = Session.logLines.joinToString("\n")
        val path = Logger.saveText(ctx, "journal", text)
        Session.lastSavedFile = path
        return path
    }
}
