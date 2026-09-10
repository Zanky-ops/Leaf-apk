package com.example.nissanleafdiag

data class LeafSnapshot(
    val soh: Int? = null,
    val gids: Int? = null,
    val storedKwh: Double? = null,
    val odometerKm: Int? = null,
    val soc: Double? = null,
    val raw5b3: String? = null,
    val raw2101: String? = null
)

object LeafDecoder {
    private fun hexBytes(line: String): List<Int> =
        line.trim().split(Regex("\\s+"))
            .drop(1) // drop the 3-hex-digit CAN ID, e.g. 5B3 / 5C5
            .mapNotNull { token ->
                token.takeIf { it.matches(Regex("(?i)[0-9a-f]{2}")) }?.toInt(16)
            }

    fun decode5B3(response: String): LeafSnapshot? {
        val line = response.lines().firstOrNull { it.trim().uppercase().startsWith("5B3 ") }
            ?: response.lines().firstOrNull { it.trim().uppercase().startsWith("5B3") }
            ?: return null
        val b = hexBytes(line)
        // ELM output: 5B3 64 A2 12 CA 30 7B D0 8B
        if (b.size < 8) return null
        val soh = b[0] shr 1
        val gids = b[6]
        return LeafSnapshot(
            soh = soh,
            gids = gids,
            storedKwh = gids * 0.08,
            raw5b3 = line
        )
    }

    fun decodeOdometer(response: String): Int? {
        val line = response.lines().firstOrNull { it.trim().uppercase().startsWith("5C5") } ?: return null
        val b = hexBytes(line)
        if (b.size < 3) return null
        return (b[0] shl 16) or (b[1] shl 8) or b[2]
    }

    fun decode2101(response: String): Double? {
        // Common AZE0 Leaf battery response used by community tools.
        // Search the first multi-line 79B/7BB response and decode the 32-bit
        // value used for SOC. If absent, return null rather than guessing.
        val bytes = response.lines()
            .filter { it.trim().uppercase().startsWith("7BB") }
            .flatMap { hexBytes(it) }
        if (bytes.size < 8) return null

        // Remove CAN id from the beginning; then look for 61 01.
        val idx = bytes.indexOfFirst { it == 0x61 } // positive response to 21
        if (idx < 0 || idx + 5 >= bytes.size || bytes[idx + 1] != 0x01) return null

        val p = idx + 2
        if (p + 3 >= bytes.size) return null
        val raw = (bytes[p].toLong() shl 24) or
                (bytes[p + 1].toLong() shl 16) or
                (bytes[p + 2].toLong() shl 8) or
                bytes[p + 3].toLong()
        return raw / 10000.0
    }
}
