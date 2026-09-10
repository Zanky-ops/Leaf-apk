package com.example.nissanleafdiag

import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * Тонкий шар над послідовним каналом ELM327.
 *
 * Клас нічого не знає про Leaf: він шле команду, чекає на символ запрошення '>'
 * і повертає сирий текст. Розбір відповіді — вище, у [LeafProtocol].
 */
class Elm327(private val input: InputStream, private val output: OutputStream) {

    companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        /**
         * Головна різниця, яку треба бачити користувачу:
         * NO DATA — шина жива, але блок не відповів;
         * CAN ERROR — адаптер навіть не зміг передати кадр (немає ACK на шині).
         */
        fun classify(response: String): ElmVerdict {
            val u = response.uppercase()
            return when {
                response.isBlank() -> ElmVerdict.Empty
                u.contains("CAN ERROR") -> ElmVerdict.CanError
                u.contains("UNABLE TO CONNECT") -> ElmVerdict.UnableToConnect
                u.contains("BUS ERROR") || u.contains("BUS INIT") || u.contains("BUS BUSY") -> ElmVerdict.BusError
                u.contains("BUFFER FULL") -> ElmVerdict.BufferFull
                u.contains("STOPPED") -> ElmVerdict.Stopped
                u.contains("NO DATA") -> ElmVerdict.NoData
                u.contains("ERROR") -> ElmVerdict.Unknown
                u.trim() == "?" -> ElmVerdict.Unknown
                else -> ElmVerdict.Ok
            }
        }

        fun verdictText(v: ElmVerdict): String = when (v) {
            ElmVerdict.Ok -> "відповідь отримано"
            ElmVerdict.NoData -> "NO DATA — шина відповідає, але блок мовчить"
            ElmVerdict.CanError -> "CAN ERROR — кадр не пройшов у шину (немає ACK)"
            ElmVerdict.BusError -> "BUS ERROR — збій на шині"
            ElmVerdict.BufferFull -> "BUFFER FULL — адаптер не встигає"
            ElmVerdict.Stopped -> "STOPPED — операцію перервано"
            ElmVerdict.UnableToConnect -> "UNABLE TO CONNECT — протокол не встановлено"
            ElmVerdict.Unknown -> "адаптер повернув помилку"
            ElmVerdict.Empty -> "порожня відповідь"
        }
    }

    /** Останній виставлений заголовок/фільтр — щоб не гнати ATSH перед кожним запитом. */
    private var currentHeader: String? = null
    private var currentFilter: String? = null
    private var currentFcHeader: String? = null

    @Volatile
    private var monitoring = false

    private fun drain() {
        try {
            while (input.available() > 0) {
                if (input.read() < 0) break
            }
        } catch (_: Exception) {
        }
    }

    private fun readUntilPrompt(timeoutMs: Long): String {
        val deadline = System.currentTimeMillis() + timeoutMs
        val sb = StringBuilder()
        while (System.currentTimeMillis() < deadline) {
            if (input.available() > 0) {
                val ch = input.read()
                if (ch < 0) break
                val c = ch.toChar()
                if (c == '>') break
                sb.append(c)
            } else {
                Thread.sleep(4)
            }
        }
        return sb.toString()
            .replace('\r', '\n')
            .trim()
    }

    @Synchronized
    fun command(command: String, timeoutMs: Long = 2500): String {
        drain()
        output.write((command.trim() + "\r").toByteArray(Charsets.US_ASCII))
        output.flush()
        return readUntilPrompt(timeoutMs)
    }

    fun writeRaw(s: String) {
        output.write(s.toByteArray(Charsets.US_ASCII))
        output.flush()
    }

    /**
     * Виставляє адресу запиту, фільтр приймання та заголовок кадру керування потоком.
     * Потрібно рівно один раз на блок — далі можна слати службові PDU підряд.
     */
    @Synchronized
    fun setTarget(requestId: String, responseId: String) {
        if (currentHeader != requestId) {
            command("ATSH $requestId", 900)
            currentHeader = requestId
        }
        if (currentFilter != responseId) {
            command("ATCRA $responseId", 900)
            currentFilter = responseId
        }
        if (currentFcHeader != requestId) {
            // Кадр керування потоком мусить піти з тим самим заголовком, що й запит,
            // інакше багатокадрова відповідь обривається на першому кадрі.
            command("ATFCSH $requestId", 900)
            command("ATFCSD 30 00 00", 900)
            command("ATFCSM 1", 900)
            currentFcHeader = requestId
        }
    }

    @Synchronized
    fun clearFilter() {
        command("ATCRA", 900)
        currentFilter = null
    }

    /** Скидає кеш заголовків — після ATZ/ATD усе виставляється наново. */
    fun forgetTarget() {
        currentHeader = null
        currentFilter = null
        currentFcHeader = null
    }

    /**
     * Пасивний перегляд шини (ATMA). Читає рядки, доки [isActive] повертає true
     * або доки не вичерпано [timeoutMs].
     */
    @Synchronized
    fun monitor(timeoutMs: Long, isActive: () -> Boolean, onLine: (String) -> Unit) {
        drain()
        monitoring = true
        try {
            output.write("ATMA\r".toByteArray(Charsets.US_ASCII))
            output.flush()

            val deadline = System.currentTimeMillis() + timeoutMs
            val sb = StringBuilder()
            while (System.currentTimeMillis() < deadline && isActive()) {
                if (input.available() > 0) {
                    val ch = input.read()
                    if (ch < 0) break
                    val c = ch.toChar()
                    if (c == '\r' || c == '\n') {
                        val line = sb.toString().trim()
                        sb.setLength(0)
                        if (line.isNotEmpty() && line != ">") onLine(line)
                    } else if (c != '>') {
                        sb.append(c)
                    }
                } else {
                    Thread.sleep(4)
                }
            }
        } finally {
            // Будь-який символ зупиняє ATMA.
            try {
                writeRaw("\r")
                Thread.sleep(120)
                drain()
            } catch (_: Exception) {
            }
            monitoring = false
        }
    }

    fun isMonitoring(): Boolean = monitoring
}
