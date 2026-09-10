package com.example.nissanleafdiag

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private val executor = Executors.newSingleThreadExecutor()
    private val btAdapter by lazy { BluetoothAdapter.getDefaultAdapter() }

    private var socket: BluetoothSocket? = null
    private var elm: Elm327? = null

    private lateinit var deviceSpinner: Spinner
    private lateinit var status: TextView
    private lateinit var soh: TextView
    private lateinit var gids: TextView
    private lateinit var energy: TextView
    private lateinit var odo: TextView
    private lateinit var soc: TextView
    private lateinit var log: TextView

    private val devices = mutableListOf<BluetoothDevice>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        requestBluetoothPermissions()
        refreshPairedDevices()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 18, 20, 18)
        }

        status = TextView(this).apply { text = "Статус: не підключено"; textSize = 16f }
        root.addView(status)

        deviceSpinner = Spinner(this)
        root.addView(deviceSpinner)

        val connect = Button(this).apply { text = "Підключитися до ELM327" }
        connect.setOnClickListener { connectSelected() }
        root.addView(connect)

        val read = Button(this).apply { text = "Зчитати Leaf" }
        read.setOnClickListener { readLeaf() }
        root.addView(read)

        val scan = Button(this).apply { text = "Сканувати CAN / DTC" }
        scan.setOnClickListener { scanEcus() }
        root.addView(scan)

        val identify = Button(this).apply { text = "Зчитати моделі та прошивки ECU" }
        identify.setOnClickListener { identifyEcus() }
        root.addView(identify)

        val elmTest = Button(this).apply { text = "ELM327 Test" }
        elmTest.setOnClickListener { runElmTest() }
        root.addView(elmTest)

        fun card(title: String, value: TextView): LinearLayout {
            return LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(TextView(this@MainActivity).apply {
                    text = title
                    textSize = 17f
                }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(value)
            }
        }

        soh = valueView()
        gids = valueView()
        energy = valueView()
        odo = valueView()
        soc = valueView()

        root.addView(card("SOH", soh))
        root.addView(card("GIDs", gids))
        root.addView(card("Енергія", energy))
        root.addView(card("Одометр", odo))
        root.addView(card("SOC", soc))

        log = TextView(this).apply {
            textSize = 12f
            setTextIsSelectable(true)
        }
        root.addView(ScrollView(this).apply { addView(log) },
            LinearLayout.LayoutParams(-1, 0, 1f))

        setContentView(root)
    }

    private fun valueView() = TextView(this).apply {
        text = "—"
        textSize = 18f
        setPadding(8, 8, 8, 8)
    }

    private fun requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= 31) {
            val needed = arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            ).filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            if (needed.isNotEmpty()) ActivityCompat.requestPermissions(this, needed.toTypedArray(), 42)
        }
    }

    private fun refreshPairedDevices() {
        if (Build.VERSION.SDK_INT >= 31 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) return

        devices.clear()
        devices.addAll(btAdapter.bondedDevices.sortedBy { it.name ?: it.address })
        deviceSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            devices.map { "${it.name ?: "Без назви"} — ${it.address}" }
        )
        appendLog("Знайдено спарених Bluetooth-пристроїв: ${devices.size}")
    }

    private fun connectSelected() {
        if (devices.isEmpty()) {
            appendLog("Спочатку спаруйте ELM327 у системних налаштуваннях Android.")
            return
        }
        val device = devices[deviceSpinner.selectedItemPosition]
        executor.execute {
            try {
                runOnUiThread { status.text = "Статус: підключення…" }
                socket?.close()
                socket = device.createRfcommSocketToServiceRecord(Elm327.SPP_UUID)
                socket!!.connect()
                elm = Elm327(socket!!.inputStream, socket!!.outputStream)

                val init = listOf("ATZ", "ATE0", "ATL1", "ATH1", "ATS1", "ATAL", "ATSP6")
                    .joinToString("\n") { elm!!.command(it, 1500) }

                runOnUiThread {
                    status.text = "Статус: ELM327 підключено"
                    appendLog(init)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Статус: помилка"
                    appendLog("Bluetooth/ELM помилка: ${e.message}")
                }
            }
        }
    }

    private fun readLeaf() {
        val e = elm ?: run {
            appendLog("Немає підключення.")
            return
        }
        executor.execute {
            try {
                // Car-CAN: 5B3 and 5C5 are periodic frames. We briefly monitor them.
                e.command("ATCRA 5B3", 1000)
                val r5 = e.command("ATMA", 1600)
                e.writeRaw("\r")
                Thread.sleep(150)

                e.command("ATCRA 5C5", 1000)
                val rO = e.command("ATMA", 1600)
                e.writeRaw("\r")
                Thread.sleep(150)

                // EV-CAN battery query. 79B -> 7BB is used by community Leaf tooling.
                e.command("ATSH 79B", 1000)
                e.command("ATCRA 7BB", 1000)
                val r21 = e.command("21 01", 2500)

                val s5 = LeafDecoder.decode5B3(r5)
                val od = LeafDecoder.decodeOdometer(rO)
                val s = LeafDecoder.decode2101(r21)

                runOnUiThread {
                    s5?.soh?.let { soh.text = "$it %" }
                    s5?.gids?.let { gids.text = "$it" }
                    s5?.storedKwh?.let { energy.text = "%.2f kWh".format(it) }
                    od?.let { odo.text = "$it km" }
                    s?.let { soc.text = "%.2f %%".format(it) }

                    appendLog("5B3: ${s5?.raw5b3 ?: "не знайдено"}")
                    appendLog("5C5: ${od ?: "не знайдено"}")
                    appendLog("21 01 / 79B→7BB: $r21")
                }
            } catch (e: Exception) {
                runOnUiThread { appendLog("Зчитування помилка: ${e.message}") }
            }
        }
    }

    private fun scanEcus() {
        val e = elm ?: run {
            appendLog("Немає підключення.")
            return
        }
        executor.execute {
            try {
                // Passive monitor on Car-CAN. This does not write diagnostic data.
                e.command("ATCRA", 1000)
                val raw = e.command("ATMA", 3000)
                e.writeRaw("\r")
                runOnUiThread {
                    appendLog("=== CAN monitor ===")
                    appendLog(raw.takeLast(12000))
                    appendLog("Примітка: для повного ECU/DTC сканера потрібні окремі UDS/ISO-TP профілі для кожного ECU.")
                }
            } catch (e2: Exception) {
                runOnUiThread { appendLog("Сканер: ${e2.message}") }
            }
        }
    }

    private fun identifyEcus() {
        val e = elm ?: run {
            appendLog("Немає підключення.")
            return
        }

        executor.execute {
            runOnUiThread {
                status.text = "Статус: зчитування ідентифікації ECU…"
                appendLog("=== ECU Identification ===")
                appendLog("Для кожного блоку читаються DID F1A0/F18A/F194/F195/F190.")
            }

            var found = 0
            for ((name, ids) in EcuScanner.modules) {
                try {
                    val info = EcuScanner.identify(e, name, ids.first, ids.second)
                    val hasData = listOf(
                        info.diagnosticVersion,
                        info.supplier,
                        info.softwareNumber,
                        info.softwareVersion,
                        info.vin
                    ).any { !it.isNullOrBlank() }

                    if (hasData) {
                        found++
                        runOnUiThread {
                            appendLog(
                                buildString {
                                    append("\n$name [${info.requestId} → ${info.responseId}]")
                                    append("\n  Diagnostic/version: ${info.diagnosticVersion ?: "—"}")
                                    append("\n  Supplier:            ${info.supplier ?: "—"}")
                                    append("\n  Software number:     ${info.softwareNumber ?: "—"}")
                                    append("\n  Software version:    ${info.softwareVersion ?: "—"}")
                                    append("\n  VIN:                 ${info.vin ?: "—"}")
                                }
                            )
                        }
                    } else {
                        runOnUiThread { appendLog("$name: немає відповіді на ідентифікаційні DID") }
                    }
                } catch (ex: Exception) {
                    runOnUiThread { appendLog("$name: ${ex.message}") }
                }
            }

            runOnUiThread {
                status.text = "Статус: ідентифікацію завершено, ECU з даними: $found"
                appendLog("\n=== Готово ===")
                appendLog("Увага: відсутність відповіді не означає несправність ECU — модуль може бути відсутній, спати або не підтримувати цей DID.")
            }
        }
    }


    private fun runElmTest() {
        val e = elm ?: run {
            appendLog("Немає підключення до ELM327.")
            return
        }

        executor.execute {
            runOnUiThread {
                status.text = "Статус: ELM327 Test…"
                appendLog("\n==============================")
                appendLog("ELM327 TEST")
                appendLog("==============================")
            }

            try {
                val results = ElmTest.run(e)
                val passed = results.count { it.ok }

                runOnUiThread {
                    results.forEach { r ->
                        val mark = if (r.ok) "✓" else "✗"
                        appendLog("$mark ${r.command}")
                        appendLog("  ${r.response}")
                        appendLog("  ${r.note}")
                    }

                    appendLog("------------------------------")
                    appendLog("Результат: $passed / ${results.size} команд без явної помилки.")
                    appendLog("Це тест адаптера та каналу CAN; він не підтверджує справність усіх ECU.")
                    status.text = "Статус: ELM327 Test завершено"
                }
            } catch (ex: Exception) {
                runOnUiThread {
                    status.text = "Статус: ELM327 Test — помилка"
                    appendLog("Помилка тесту: ${ex.message}")
                }
            }
        }
    }


    private fun appendLog(s: String) {
        log.append("\n$s")
    }

    override fun onDestroy() {
        try { socket?.close() } catch (_: Exception) {}
        executor.shutdownNow()
        super.onDestroy()
    }
}
