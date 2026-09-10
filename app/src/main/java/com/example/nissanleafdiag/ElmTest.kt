package com.example.nissanleafdiag

data class ElmTestReport(
    val results: List<ElmTestResult>,
    val framesSeen: Int,
    val adapterVoltage: String?,
    val busAlive: Boolean,
    val verdict: String,
    val advice: List<String>
)

/**
 * Тест адаптера і шини.
 *
 * Головне тут — не список «✓/✗», а відповідь на одне питання: чи є на шині життя.
 * Її дає пасивний перегляд: якщо за півтори секунди не прийшло жодного кадру,
 * а запит повернув CAN ERROR — проблема не в застосунку і не в Leaf, а в тому,
 * що адаптер фізично нікого не чує.
 */
object ElmTest {

    fun run(elm: Elm327, isActive: () -> Boolean): ElmTestReport {
        val results = ArrayList<ElmTestResult>()

        fun test(command: String, note: String, timeout: Long = 1800): String {
            val response = elm.command(command, timeout)
            val verdict = Elm327.classify(response)
            results.add(
                ElmTestResult(
                    command = command,
                    response = response.ifBlank { "—" },
                    ok = verdict == ElmVerdict.Ok,
                    note = note
                )
            )
            return response
        }

        test("ATI", "Ідентифікатор адаптера")
        test("AT@1", "Опис пристрою")
        val voltage = test("ATRV", "Напруга на роз'ємі OBD")
        test("ATE0", "Ехо вимкнено")
        test("ATH1", "Заголовки кадрів увімкнено")
        test("ATS0", "Пробіли вимкнено")
        test("ATSP6", "ISO 15765-4 CAN 11 біт / 500 кбіт/с")
        val protocol = test("ATDP", "Встановлений протокол")

        // Пасивний перегляд: жодного запису в шину, лише слухаємо.
        elm.command("ATCRA", 900)
        var frames = 0
        elm.monitor(1600, isActive) { line ->
            val clean = line.trim().replace(" ", "")
            if (clean.length > 3 && Hex.isHexId(clean.substring(0, 3))) frames++
        }
        elm.forgetTarget()
        results.add(
            ElmTestResult(
                command = "ATMA (1.6 с)",
                response = "прийнято кадрів: $frames",
                ok = frames > 0,
                note = "Пасивний перегляд шини — головний тест наявності CAN"
            )
        )

        test("0100", "Стандартний запит OBD; Leaf на нього здебільшого не відповідає", 2500)

        val vcm = EcuCatalog.modules.first { it.name == "VCM" }
        val vcmProbe = LeafProtocol.request(elm, vcm, "22F18A", 2500)
        results.add(
            ElmTestResult(
                command = "22F18A → VCM (797/79A)",
                response = vcmProbe.raw.ifBlank { "—" },
                ok = vcmProbe.ok,
                note = Elm327.verdictText(vcmProbe.verdict)
            )
        )

        val busAlive = frames > 0 || vcmProbe.ok
        val voltageValue = voltage.uppercase().removeSuffix("V").trim().toDoubleOrNull()

        val advice = ArrayList<String>()
        val verdict: String

        if (busAlive) {
            verdict = "Шина CAN жива: прийнято $frames кадр(ів) за 1.6 с."
            advice.add("Відповідь CAN ERROR саме на 0100 — не поломка: Leaf не підтримує стандартні OBD-режими.")
            if (!vcmProbe.ok) {
                advice.add("VCM не відповів на ідентифікацію — переконайтеся, що машина в режимі READY, а не просто ACC.")
            }
        } else {
            verdict = "Шини CAN не видно: жодного кадру за 1.6 с і жодної відповіді від блоків."
            advice.add("Увімкніть машину в READY (нога на гальмі + кнопка). У режимі ACC більшість блоків спить.")
            advice.add("Перевірте, що адаптер вставлений у роз'єм OBD машини до клацання.")
            if (voltageValue != null && voltageValue < 11.0) {
                advice.add("Адаптер бачить лише ${voltage.trim()} на роз'ємі — це схоже на живлення без машини або поганий контакт.")
            }
            advice.add("Дешеві клони ELM327 часто мають несправний приймач CAN: перевірте адаптер на іншій машині.")
            advice.add("Якщо тест робиться на столі, без машини — CAN ERROR це нормальна і очікувана відповідь.")
        }

        if (protocol.isNotBlank()) {
            advice.add("Протокол за словами адаптера: ${protocol.trim()}")
        }

        return ElmTestReport(
            results = results,
            framesSeen = frames,
            adapterVoltage = voltage.ifBlank { null },
            busAlive = busAlive,
            verdict = verdict,
            advice = advice
        )
    }
}
