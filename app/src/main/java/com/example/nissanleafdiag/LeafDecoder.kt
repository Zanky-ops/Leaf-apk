package com.example.nissanleafdiag

/**
 * Розбір даних Leaf.
 *
 * Два джерела, і вони різні за надійністю:
 *
 * 1) Широкомовні кадри (0x55B, 0x5BC, 0x5B3, 0x5C5, 0x1DB) — машина шле їх сама,
 *    застосунок лише слухає. Нічого не пишемо в шину.
 * 2) Службові запити до LBC (21 01 / 21 02 / 21 04) — там багатокадрові відповіді.
 *
 * Кожне значення проходить перевірку діапазону: якщо число не схоже на правду,
 * поле лишається порожнім. Порожньо — чесніше, ніж красиве сміття.
 *
 * Зсуви в групі 01 (Ah, Hx) звірені лише з відкритих джерел і чекають на перевірку
 * на живій машині — саме для цього в застосунку є кнопка «Зняти дамп LBC».
 */
object LeafDecoder {

    private fun signed8(v: Int): Int = if (v > 127) v - 256 else v

    private fun u16(b: List<Int>, i: Int): Int? =
        if (i + 1 < b.size) (b[i] shl 8) or b[i + 1] else null

    private fun u24(b: List<Int>, i: Int): Int? =
        if (i + 2 < b.size) (b[i] shl 16) or (b[i + 1] shl 8) or b[i + 2] else null

    // ---------------------------------------------------------------- широкомовні

    /** 0x55B: перші 10 біт — SOC у десятих відсотка. */
    fun socFrom55B(lines: List<CanLine>): Double? {
        for (line in lines.reversed()) {
            val b = line.data
            if (b.size < 2) continue
            val raw = (b[0] shl 2) or (b[1] shr 6)
            val soc = raw / 10.0
            if (soc in 0.0..100.0 && raw != 1023) return soc
        }
        return null
    }

    /** 0x5BC: перші 10 біт — GIDs. */
    fun gidsFrom5BC(lines: List<CanLine>): Int? {
        for (line in lines.reversed()) {
            val b = line.data
            if (b.size < 2) continue
            val gids = (b[0] shl 2) or (b[1] shr 6)
            if (gids in 1..700) return gids
        }
        return null
    }

    /** 0x5B3: оцінка SOH у цілих відсотках. Потребує звірки на машині. */
    fun sohFrom5B3(lines: List<CanLine>): Double? {
        for (line in lines.reversed()) {
            val b = line.data
            if (b.size < 2) continue
            val soh = b[1] shr 1
            if (soh in 40..100) return soh.toDouble()
        }
        return null
    }

    /** 0x5C5: байти 1..3 — одометр у кілометрах. */
    fun odometerFrom5C5(lines: List<CanLine>): Int? {
        for (line in lines.reversed()) {
            val b = line.data
            if (b.size < 4) continue
            val km = (b[1] shl 16) or (b[2] shl 8) or b[3]
            if (km in 1..999_999) return km
        }
        return null
    }

    /** 0x1DB: струм (11 біт зі знаком, крок 0.5 А) і напруга (10 біт, крок 0.5 В). */
    fun currentAndVoltageFrom1DB(lines: List<CanLine>): Pair<Double?, Double?> {
        for (line in lines.reversed()) {
            val b = line.data
            if (b.size < 4) continue

            var rawCurrent = (b[0] shl 3) or (b[1] shr 5)
            if (rawCurrent > 1023) rawCurrent -= 2048
            val current = rawCurrent / 2.0

            val rawVoltage = (b[2] shl 2) or (b[3] shr 6)
            val voltage = rawVoltage / 2.0

            val currentOk = current > -400.0 && current < 400.0
            val voltageOk = voltage in 150.0..500.0
            if (currentOk || voltageOk) {
                return (if (currentOk) current else null) to (if (voltageOk) voltage else null)
            }
        }
        return null to null
    }

    // ------------------------------------------------------------- службові групи

    /**
     * 21 02: напруги комірок у мілівольтах, підряд по два байти.
     * Точний зсув початку відрізняється між прошивками, тому шукаємо найдовший
     * ланцюжок значень, схожих на напругу комірки.
     */
    fun cellsFromGroup2(pdu: List<Int>): List<Int> {
        if (pdu.size < 40) return emptyList()
        var best: List<Int> = emptyList()
        for (start in 2..12) {
            val run = ArrayList<Int>()
            var i = start
            while (i + 1 < pdu.size) {
                val v = (pdu[i] shl 8) or pdu[i + 1]
                if (v in 1500..4600) run.add(v) else break
                i += 2
            }
            if (run.size > best.size) best = run
        }
        return if (best.size >= 24) best.take(96) else emptyList()
    }

    /** 21 04: температури батареї. Трійки «два байти АЦП + байт градусів». */
    fun tempsFromGroup4(pdu: List<Int>): List<Double> {
        if (pdu.size < 6) return emptyList()
        val d = pdu.subList(2, pdu.size)
        val out = ArrayList<Double>()
        var i = 2
        while (i < d.size && out.size < 4) {
            val t = signed8(d[i])
            if (t in -40..80) out.add(t.toDouble())
            i += 3
        }
        return out
    }

    data class Group1(
        val ah: Double? = null,
        val hxPercent: Double? = null,
        val packVoltage: Double? = null
    )

    /**
     * 21 01: ємність і показники стану. Зсуви — з відкритих джерел, кожне значення
     * перевіряється діапазоном, інакше не показується.
     */
    fun group1(pdu: List<Int>): Group1 {
        if (pdu.size < 36) return Group1()
        val d = pdu.subList(2, pdu.size)

        // Свідомо мало полів. SOC і SOH з цієї ж відповіді за відкритими таблицями
        // лягали на зсуви, які перекриваються між собою, тобто щонайменше одне з них
        // хибне. Обидва беруться з широкомовних кадрів (0x55B і 0x5B3), а сюди
        // повернуться лише після звірки з дампом на живій машині.
        val hx = u16(d, 25)?.let { it / 100.0 }?.takeIf { it in 1.0..150.0 }
        val ah = u24(d, 32)?.let { it / 10000.0 }?.takeIf { it in 5.0..200.0 }
        val voltage = u16(d, 20)?.let { it / 100.0 }?.takeIf { it in 150.0..500.0 }

        return Group1(ah = ah, hxPercent = hx, packVoltage = voltage)
    }

    // Напруга 12 В свідомо не розбирається. Єдиний зсув, що трапляється в
    // відкритих джерелах (0x1F2, байт 3, крок 0.05 В), фізично не здатен показати
    // 13.2 В — максимум 12.75 В, тобто таблиця хибна. Поле лишається порожнім,
    // доки не буде дампу з живої машини.
}
