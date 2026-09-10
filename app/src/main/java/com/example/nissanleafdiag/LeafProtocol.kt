package com.example.nissanleafdiag

/** Результат одного службового запиту: сирий текст, зібраний PDU і вирок адаптера. */
data class ProtoResult(
    val request: String,
    val raw: String,
    val pdu: List<Int>?,
    val verdict: ElmVerdict
) {
    val ok: Boolean get() = verdict == ElmVerdict.Ok && pdu != null
}

object LeafProtocol {

    /**
     * Ініціалізація адаптера під Leaf.
     * ATS0 — прибрати пробіли: рядок стає «7BB103561…», його однозначно ріже регулярка.
     * ATH1 — залишити заголовки: без них не видно, від кого прийшов кадр,
     * і не збереш багатокадрову відповідь.
     */
    val initCommands: List<Pair<String, Long>> = listOf(
        "ATZ" to 2500L,
        "ATE0" to 1200L,
        "ATL0" to 900L,
        "ATS0" to 900L,
        "ATH1" to 900L,
        "ATAL" to 900L,
        "ATCAF1" to 900L,
        "ATST64" to 900L,
        "ATSP6" to 1500L
    )

    fun initAdapter(elm: Elm327, onLog: (String) -> Unit): String {
        elm.forgetTarget()
        for ((cmd, timeout) in initCommands) {
            val r = elm.command(cmd, timeout)
            onLog("$cmd → ${r.ifBlank { "—" }}")
        }
        val dp = elm.command("ATDP", 1500)
        onLog("ATDP → ${dp.ifBlank { "—" }}")
        return dp
    }

    /** Службовий запит до конкретного блоку. [pdu] — наприклад «2101» або «22F190». */
    fun request(elm: Elm327, module: EcuModule, pdu: String, timeoutMs: Long = 3000): ProtoResult {
        elm.setTarget(module.requestId, module.responseId)
        val raw = elm.command(pdu, timeoutMs)
        val verdict = Elm327.classify(raw)
        val assembled = if (verdict == ElmVerdict.Ok) IsoTp.assemble(raw, module.responseId) else null
        return ProtoResult(pdu, raw, assembled, verdict)
    }

    /** Читання ідентифікаційного DID (сервіс 0x22). Повертає текст або null. */
    fun readDid(elm: Elm327, module: EcuModule, did: String): Pair<String?, ProtoResult> {
        val r = request(elm, module, "22$did", 2500)
        val pdu = r.pdu ?: return null to r
        if (!IsoTp.isPositive(pdu, 0x22)) return null to r

        val hi = did.substring(0, 2).toIntOrNull(16)
        val lo = did.substring(2, 4).toIntOrNull(16)
        if (hi == null || lo == null) return null to r
        if (pdu.size < 4 || pdu[1] != hi || pdu[2] != lo) return null to r

        val payload = pdu.subList(3, pdu.size)
        val text = Hex.ascii(payload)
        val value = if (text.length >= 3) text else Hex.toHex(payload)
        return (if (value.isBlank()) null else value) to r
    }

    /**
     * Пасивне прослуховування одного широкомовного ID.
     * Нічого не пише в шину, крім самої команди фільтра.
     */
    fun sniff(elm: Elm327, canId: String, durationMs: Long, isActive: () -> Boolean): List<CanLine> {
        val lines = ArrayList<CanLine>()
        elm.command("ATCRA $canId", 900)
        elm.monitor(durationMs, isActive) { raw ->
            val clean = raw.trim().uppercase().replace(" ", "")
            if (clean.length > 3 && clean.startsWith(canId.uppercase())) {
                lines.add(CanLine(canId.uppercase(), Hex.bytesOf(clean.substring(3))))
            }
        }
        elm.forgetTarget()
        return lines
    }

    /**
     * Читання кодів несправностей.
     * Спершу UDS 19 02 FF (сучасні блоки), потім Nissan/KWP 18 00 FF 00 (ZE0/AZE0),
     * наприкінці — стандартний OBD 03. Це три різні мови, якими розмовляють різні блоки Leaf.
     */
    fun readDtc(elm: Elm327, module: EcuModule): Pair<List<DtcItem>, ProtoResult> {
        val uds = request(elm, module, "1902FF", 3500)
        if (uds.ok && IsoTp.isPositive(uds.pdu, 0x19)) {
            val items = parseUdsDtc(uds.pdu!!, module.name)
            if (items.isNotEmpty()) return items to uds
        }

        val kwp = request(elm, module, "1800FF00", 3500)
        if (kwp.ok && IsoTp.isPositive(kwp.pdu, 0x18)) {
            val items = parseKwpDtc(kwp.pdu!!, module.name)
            if (items.isNotEmpty()) return items to kwp
        }

        val obd = request(elm, module, "03", 3000)
        if (obd.ok && IsoTp.isPositive(obd.pdu, 0x03)) {
            val items = parseObdDtc(obd.pdu!!, module.name)
            if (items.isNotEmpty()) return items to obd
        }

        return emptyList<DtcItem>() to uds
    }

    /** 59 02 <маска> [3 байти коду + 1 байт статусу] × N */
    internal fun parseUdsDtc(pdu: List<Int>, ecu: String): List<DtcItem> {
        if (pdu.size < 7) return emptyList()
        val body = pdu.subList(3, pdu.size)
        val out = ArrayList<DtcItem>()
        var i = 0
        while (i + 3 < body.size) {
            val b0 = body[i]
            val b1 = body[i + 1]
            val b2 = body[i + 2]
            val status = body[i + 3]
            i += 4
            if (b0 == 0 && b1 == 0 && b2 == 0) continue
            val code = DtcCatalog.codeFromUds(b0, b1, b2)
            out.add(
                DtcItem(
                    code = code,
                    description = DtcCatalog.describe(code),
                    // біт 0 — тест провалено зараз, біт 3 — підтверджено й збережено
                    active = (status and 0x01) != 0,
                    ecu = ecu,
                    raw = "%02X %02X %02X status %02X".format(b0, b1, b2, status)
                )
            )
        }
        return out
    }

    /** 58 <кількість> [2 байти коду + 1 байт статусу] × N */
    internal fun parseKwpDtc(pdu: List<Int>, ecu: String): List<DtcItem> {
        if (pdu.size < 5) return emptyList()
        val body = pdu.subList(2, pdu.size)
        val out = ArrayList<DtcItem>()
        var i = 0
        while (i + 2 < body.size) {
            val b0 = body[i]
            val b1 = body[i + 1]
            val status = body[i + 2]
            i += 3
            if (b0 == 0 && b1 == 0) continue
            val code = DtcCatalog.codeFromUds(b0, b1, 0)
            out.add(
                DtcItem(
                    code = code,
                    description = DtcCatalog.describe(code),
                    active = (status and 0x01) != 0 || (status and 0x60) != 0,
                    ecu = ecu,
                    raw = "%02X %02X status %02X".format(b0, b1, status)
                )
            )
        }
        return out
    }

    /** 43 <2 байти коду> × N */
    internal fun parseObdDtc(pdu: List<Int>, ecu: String): List<DtcItem> {
        val body = pdu.subList(1, pdu.size)
        val out = ArrayList<DtcItem>()
        var i = 0
        while (i + 1 < body.size) {
            val b0 = body[i]
            val b1 = body[i + 1]
            i += 2
            if (b0 == 0 && b1 == 0) continue
            val code = DtcCatalog.codeFromUds(b0, b1, 0)
            out.add(
                DtcItem(
                    code = code,
                    description = DtcCatalog.describe(code),
                    active = true,
                    ecu = ecu,
                    raw = "%02X %02X".format(b0, b1)
                )
            )
        }
        return out
    }

    /** Стирання кодів. Запис у блок — лише за явним підтвердженням користувача. */
    fun clearDtc(elm: Elm327, module: EcuModule): ProtoResult {
        val uds = request(elm, module, "14FFFFFF", 4000)
        if (uds.ok && IsoTp.isPositive(uds.pdu, 0x14)) return uds
        return request(elm, module, "04", 3000)
    }
}
