package com.example.nissanleafdiag

/**
 * Карта діагностичних адрес Leaf ZE0/AZE0: запит → відповідь.
 * Джерело те саме, що й у README: відкрита документація Leaf CAN/OBD.
 */
object EcuCatalog {

    val modules: List<EcuModule> = listOf(
        EcuModule("VCM", "797", "79A", "Vehicle Control Module"),
        EcuModule("LBC / BMS", "79B", "7BB", "Li-ion Battery Controller"),
        EcuModule("BCM", "745", "765", "Body Control Module"),
        EcuModule("ABS / VDC", "740", "760", "гальма, стабілізація"),
        EcuModule("Inverter (MCU)", "784", "78C", "інвертор і мотор"),
        EcuModule("Charger / PDM", "792", "793", "бортовий зарядний пристрій"),
        EcuModule("HVAC", "744", "764", "клімат"),
        EcuModule("Meter / M&A", "743", "763", "щиток приладів"),
        EcuModule("EPS", "742", "762", "електропідсилювач керма"),
        EcuModule("TCU", "746", "783", "телематика"),
        EcuModule("IPDM E/R", "74D", "76D", "силовий розподіл"),
        EcuModule("Airbag", "752", "772", "подушки безпеки"),
        EcuModule("BRAKE", "70E", "70F", "електрогідравлічні гальма"),
        EcuModule("VSP", "73F", "761", "звук для пішоходів"),
        EcuModule("Multi AV", "747", "767", "мультимедіа"),
        EcuModule("Shift", "79D", "7BD", "селектор передач")
    )

    /**
     * Ідентифікаційні DID, які читає застосунок.
     * Порядок важливий: перший використовується як «проба» — якщо блок не
     * відповів навіть на нього, решту не питаємо і не тратимо хвилини на мовчання.
     */
    private val dids: List<Pair<String, String>> = listOf(
        "F18A" to "supplier",
        "F187" to "part",
        "F191" to "hardware",
        "F194" to "software",
        "F195" to "softwareVersion",
        "F1A0" to "diag",
        "F190" to "vin"
    )

    /**
     * Проба блока найдешевшим читанням. Повертає вирок адаптера — за ним видно,
     * чи це «блок мовчить» (NO DATA), чи «шини взагалі немає» (CAN ERROR).
     */
    fun probe(elm: Elm327, module: EcuModule): Pair<Boolean, ElmVerdict> {
        val r = LeafProtocol.request(elm, module, "22F18A", 2200)
        if (r.ok && IsoTp.isPositive(r.pdu, 0x22)) return true to r.verdict
        if (r.pdu != null && r.pdu.isNotEmpty()) {
            // Навіть негативна відповідь (7F 22 xx) означає, що блок живий.
            if (r.pdu[0] == 0x7F) return true to r.verdict
        }

        val legacy = LeafProtocol.request(elm, module, "1A8A", 2200)
        if (legacy.ok && legacy.pdu != null && legacy.pdu.isNotEmpty()) {
            if (legacy.pdu[0] == 0x5A || legacy.pdu[0] == 0x7F) return true to legacy.verdict
        }
        return false to r.verdict
    }

    fun identify(elm: Elm327, module: EcuModule): EcuInfo {
        val raw = StringBuilder()
        val values = HashMap<String, String>()

        for ((did, key) in dids) {
            val (value, result) = LeafProtocol.readDid(elm, module, did)
            raw.append("[$did] ").append(result.raw.ifBlank { "—" }).append('\n')
            if (value != null) values[key] = value
        }

        return EcuInfo(
            partNumber = values["part"],
            supplier = values["supplier"],
            hardware = values["hardware"],
            software = values["software"],
            firmware = values["softwareVersion"],
            diagVersion = values["diag"],
            vin = values["vin"],
            raw = raw.toString().trim()
        )
    }
}
