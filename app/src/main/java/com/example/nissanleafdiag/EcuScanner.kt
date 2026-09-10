
package com.example.nissanleafdiag

data class EcuInfo(
    val name: String,
    val requestId: String,
    val responseId: String,
    val diagnosticVersion: String? = null,
    val supplier: String? = null,
    val softwareNumber: String? = null,
    val softwareVersion: String? = null,
    val vin: String? = null,
    val raw: String = ""
)

object EcuScanner {
    // Nissan Leaf ZE0/AZE0 CAN diagnostic addresses documented by the Leaf CAN project.
    // These are request -> response IDs for active diagnostics.
    val modules = listOf(
        "VCM" to ("797" to "79A"),
        "BCM" to ("745" to "765"),
        "ABS/VDC" to ("740" to "760"),
        "LBC / BMS" to ("79B" to "7BB"),
        "INVERTER / Motor Controller" to ("784" to "78C"),
        "Meter / M&A" to ("743" to "763"),
        "HVAC" to ("744" to "764"),
        "BRAKE" to ("70E" to "70F"),
        "VSP" to ("73F" to "761"),
        "EPS" to ("742" to "762"),
        "TCU" to ("746" to "783"),
        "Multi AV" to ("747" to "767"),
        "IPDM E/R" to ("74D" to "76D"),
        "AIRBAG" to ("752" to "772"),
        "CHARGER" to ("792" to "793"),
        "SHIFT" to ("79D" to "7BD")
    )

    private fun hexBytes(text: String): List<Int> =
        Regex("""(?i)(?<![0-9A-F])([0-9A-F]{2})(?![0-9A-F])""")
            .findAll(text)
            .map { it.groupValues[1].toInt(16) }
            .toList()

    private fun asciiFromHex(bytes: List<Int>): String {
        return bytes.filter { it in 0x20..0x7E }
            .map { it.toChar() }
            .joinToString("")
            .trim()
    }

    private fun extractDid(response: String, didHi: Int, didLo: Int): String? {
        val bytes = hexBytes(response)
        for (i in 0 until bytes.size - 2) {
            if (bytes[i] == 0x62 && bytes[i + 1] == didHi && bytes[i + 2] == didLo) {
                return asciiFromHex(bytes.drop(i + 3))
                    .ifBlank { bytes.drop(i + 3).joinToString(" ") { "%02X".format(it) } }
            }
        }
        return null
    }

    fun identify(elm: Elm327, name: String, requestId: String, responseId: String): EcuInfo {
        val raw = StringBuilder()

        // UDS-style identification used by Nissan/Renault diagnostic tooling:
        // 22 F1 A0 = diagnostic/version information
        // 22 F1 8A = supplier/manufacturer
        // 22 F1 94 = software number
        // 22 F1 95 = software version
        // 22 F1 90 = VIN, where supported.
        fun readDid(did: String): String? {
            elm.command("ATSH $requestId", 800)
            elm.command("ATCRA $responseId", 800)
            val r = elm.command("22 ${did.substring(0, 2)} ${did.substring(2, 4)}", 2200)
            raw.append("\n[$did] $r")
            return extractDid(r, did.substring(0, 2).toInt(16), did.substring(2, 4).toInt(16))
        }

        return EcuInfo(
            name = name,
            requestId = requestId,
            responseId = responseId,
            diagnosticVersion = readDid("F1A0"),
            supplier = readDid("F18A"),
            softwareNumber = readDid("F194"),
            softwareVersion = readDid("F195"),
            vin = readDid("F190"),
            raw = raw.toString()
        )
    }
}
