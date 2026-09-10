package com.example.nissanleafdiag

/** Один кадр, як його надрукував ELM327 з увімкненими заголовками (ATH1, ATS0). */
data class CanLine(val id: String, val data: List<Int>)

object Hex {

    fun bytesOf(hex: String): List<Int> {
        val clean = hex.filter { it.isDigit() || (it.uppercaseChar() in 'A'..'F') }
        val out = ArrayList<Int>(clean.length / 2)
        var i = 0
        while (i + 1 < clean.length) {
            val v = clean.substring(i, i + 2).toIntOrNull(16)
            if (v != null) out.add(v)
            i += 2
        }
        return out
    }

    fun toHex(bytes: List<Int>): String = bytes.joinToString(" ") { "%02X".format(it) }

    fun ascii(bytes: List<Int>): String =
        bytes.filter { it in 0x20..0x7E }.map { it.toChar() }.joinToString("").trim()

    /** «7BB103561…» → «7BB 10 35 61 …» для читабельного виводу. */
    fun pretty(line: String): String {
        val clean = line.trim().uppercase().replace(" ", "")
        if (clean.length <= 3) return clean
        val id = clean.substring(0, 3)
        val rest = clean.substring(3)
        val sb = StringBuilder(id)
        var i = 0
        while (i < rest.length) {
            val end = minOf(i + 2, rest.length)
            sb.append(' ').append(rest, i, end)
            i = end
        }
        return sb.toString()
    }

    fun isHexId(s: String): Boolean =
        s.length == 3 && s.all { it.isDigit() || it.uppercaseChar() in 'A'..'F' }
}

object IsoTp {

    private val lineRegex = Regex("^([0-9A-F]{3})([0-9A-F]{2,16})$")

    fun parseLines(response: String): List<CanLine> {
        val out = ArrayList<CanLine>()
        for (rawLine in response.lines()) {
            val line = rawLine.trim().uppercase().replace(" ", "")
            val m = lineRegex.matchEntire(line) ?: continue
            out.add(CanLine(m.groupValues[1], Hex.bytesOf(m.groupValues[2])))
        }
        return out
    }

    /**
     * Збирає ISO-TP повідомлення з кадрів. Повертає повний PDU, тобто перший байт —
     * код сервісу (наприклад 0x61 для позитивної відповіді на 0x21).
     * null означає, що жодного придатного кадру не було.
     */
    fun assemble(response: String, responseId: String?): List<Int>? {
        val lines = parseLines(response)
            .filter { responseId == null || it.id.equals(responseId, ignoreCase = true) }
        if (lines.isEmpty()) return null

        val out = ArrayList<Int>()
        var declaredLength = -1

        for (line in lines) {
            val d = line.data
            if (d.isEmpty()) continue
            val pci = d[0]
            when (pci shr 4) {
                0x0 -> {
                    val len = pci and 0x0F
                    val end = minOf(1 + len, d.size)
                    if (end > 1) out.addAll(d.subList(1, end))
                    if (declaredLength < 0) declaredLength = len
                }
                0x1 -> {
                    if (d.size < 2) continue
                    declaredLength = ((pci and 0x0F) shl 8) or d[1]
                    if (d.size > 2) out.addAll(d.subList(2, d.size))
                }
                0x2 -> {
                    if (d.size > 1) out.addAll(d.subList(1, d.size))
                }
                else -> {
                    // 0x3 — кадр керування потоком, його шле адаптер; дані не несе.
                }
            }
        }

        if (out.isEmpty()) return null
        if (declaredLength in 1 until out.size) return out.subList(0, declaredLength)
        return out
    }

    /** Перевіряє, що це позитивна відповідь на [requestService] (0x21 → 0x61). */
    fun isPositive(pdu: List<Int>?, requestService: Int): Boolean =
        pdu != null && pdu.isNotEmpty() && pdu[0] == requestService + 0x40

    /** 0x7F <сервіс> <код> — негативна відповідь. */
    fun negativeCode(pdu: List<Int>?): Int? =
        if (pdu != null && pdu.size >= 3 && pdu[0] == 0x7F) pdu[2] else null
}
