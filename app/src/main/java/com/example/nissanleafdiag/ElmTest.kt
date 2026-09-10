
package com.example.nissanleafdiag

data class ElmTestResult(
    val command: String,
    val response: String,
    val ok: Boolean,
    val note: String
)

object ElmTest {
    fun run(elm: Elm327): List<ElmTestResult> {
        val tests = mutableListOf<ElmTestResult>()

        fun test(command: String, note: String, timeout: Long = 1800) {
            val response = elm.command(command, timeout)
            val upper = response.uppercase()
            val ok = response.isNotBlank() &&
                    !upper.contains("ERROR") &&
                    !upper.contains("UNABLE") &&
                    !upper.contains("NO DATA") &&
                    !upper.contains("STOPPED")
            tests += ElmTestResult(command, response.ifBlank { "—" }, ok, note)
        }

        // Adapter identification and basic AT command support.
        test("ATI", "Версія / ідентифікатор ELM327")
        test("AT@1", "Назва виробника / адаптера")
        test("ATSP?", "Поточний протокол")
        test("ATDP", "Опис поточного протоколу")

        // Prepare a clean configuration for Leaf Car-CAN.
        test("ATE0", "Echo OFF")
        test("ATL0", "Linefeeds OFF")
        test("ATH1", "CAN headers ON")
        test("ATS1", "Spaces ON")
        test("ATAL", "Allow long messages")
        test("ATSP6", "ISO 15765-4 CAN 11-bit / 500 kbit/s")
        test("ATDP", "Перевірка встановленого протоколу")

        // Passive-independent functional CAN/OBD request.
        // No ECU write/service command is used here.
        test("0100", "Тест відповіді OBD/CAN; якщо Leaf не відповідає — це не обов'язково несправність", 2500)

        return tests
    }
}
