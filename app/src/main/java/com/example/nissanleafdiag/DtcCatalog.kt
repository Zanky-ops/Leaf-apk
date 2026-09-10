package com.example.nissanleafdiag

/**
 * Довідник кодів. Свідомо маленький: сюди входять лише ті коди, значення яких
 * підтверджене документацією. Для решти застосунок каже «немає в довіднику»
 * і показує сімейство коду — це чесніше, ніж вигаданий опис.
 */
object DtcCatalog {

    private val known: Map<String, String> = mapOf(
        "P0A7A" to "Знос високовольтної батареї (Hybrid/EV Battery Pack Deterioration)",
        "P0A80" to "Потрібна заміна високовольтної батареї",
        "P0A0D" to "Коло блокування високовольтної системи — високий рівень",
        "P0A0C" to "Коло блокування високовольтної системи — низький рівень",
        "P0AA6" to "Втрата ізоляції високовольтної системи",
        "P0A09" to "Коло керування перетворювачем DC/DC",
        "P0A94" to "Несправність перетворювача DC/DC",
        "P0AFA" to "Низький рівень заряду високовольтної батареї",
        "P0B22" to "Датчик температури високовольтної батареї",
        "P3102" to "Помилка зв'язку/живлення в системі EV",
        "U1000" to "Несправність шини CAN",
        "U1001" to "Несправність шини CAN (втрата зв'язку)",
        "U110E" to "Втрата зв'язку з блоком керування батареєю",
        "U1108" to "Втрата зв'язку з інвертором",
        "C1A60" to "Попередження системи EV",
        "B2601" to "Коло сигналу вимикача запалювання"
    )

    /**
     * Стандартне перетворення двох байтів у код: старші 2 біти — літера,
     * наступні 2 — перша цифра, далі три шістнадцяткові цифри.
     */
    fun codeFromUds(b0: Int, b1: Int, b2: Int): String {
        val letter = when ((b0 shr 6) and 0x03) {
            0 -> "P"
            1 -> "C"
            2 -> "B"
            else -> "U"
        }
        val digit1 = (b0 shr 4) and 0x03
        val rest = "%01X%02X".format(b0 and 0x0F, b1)
        // Третій байт (тип відмови) не входить у код — він лишається в сирих даних.
        return "$letter$digit1$rest"
    }

    fun describe(code: String): String {
        known[code.uppercase()]?.let { return it }
        val family = when {
            code.startsWith("P0A", true) || code.startsWith("P0B", true) ->
                "високовольтна/гібридна система"
            code.startsWith("P3", true) -> "система керування приводом"
            code.startsWith("P", true) -> "силова установка"
            code.startsWith("C", true) -> "шасі, гальма, кермо"
            code.startsWith("B", true) -> "кузовна електроніка"
            code.startsWith("U", true) -> "зв'язок між блоками, шина CAN"
            else -> "невідоме сімейство"
        }
        return "Немає в довіднику застосунку. Сімейство: $family"
    }

    fun isKnown(code: String): Boolean = known.containsKey(code.uppercase())
}
