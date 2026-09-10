package com.example.nissanleafdiag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Тести на розбір протоколу.
 *
 * Це єдина частина застосунку, яку можна перевірити без машини: кадри збираються
 * вручну з відомими значеннями, і декодер зобов'язаний повернути рівно їх.
 */
class HexTest {

    @Test
    fun bytesOfIgnoresJunk() {
        assertEquals(listOf(0x7B, 0xB1, 0x03), Hex.bytesOf("7B B1 03"))
        assertEquals(listOf(0x62, 0xF1, 0x8A), Hex.bytesOf("62F18A"))
    }

    @Test
    fun asciiKeepsPrintableOnly() {
        assertEquals("NIS", Hex.ascii(listOf(0x4E, 0x49, 0x53, 0x00, 0xFF)))
    }

    @Test
    fun prettySplitsIdAndBytes() {
        assertEquals("7BB 10 35 61 01", Hex.pretty("7BB103561 01"))
    }

    @Test
    fun isHexIdAcceptsThreeHexDigits() {
        assertTrue(Hex.isHexId("7BB"))
        assertTrue(Hex.isHexId("55b"))
        assertTrue(!Hex.isHexId("7BBB"))
        assertTrue(!Hex.isHexId("ZZZ"))
    }
}

class IsoTpTest {

    @Test
    fun singleFrameIsUnwrapped() {
        // 7BB, PCI 06, дані 62 F1 8A 4E 49 53
        val pdu = IsoTp.assemble("7BB0662F18A4E4953", "7BB")
        assertEquals(listOf(0x62, 0xF1, 0x8A, 0x4E, 0x49, 0x53), pdu)
    }

    @Test
    fun multiFrameIsAssembledAndTruncatedToDeclaredLength() {
        val response = listOf(
            "7BB1014610100112233",              // перший кадр: довжина 0x014 = 20, 6 байтів даних
            "7BB2144556677889900",              // продовження: 7 байтів
            "7BB22AABBCCDDEEFF01"               // продовження: ще 7 байтів
        ).joinToString("\n")

        val pdu = IsoTp.assemble(response, "7BB")!!
        assertEquals(20, pdu.size)
        assertEquals(0x61, pdu[0])
        assertEquals(0x01, pdu[1])
        assertEquals(0x44, pdu[6])
        assertEquals(0xAA, pdu[13])
    }

    @Test
    fun framesOfOtherEcuAreIgnored() {
        val response = "79A0662F18A414243\n7BB0662F18A4E4953"
        assertEquals(listOf(0x62, 0xF1, 0x8A, 0x4E, 0x49, 0x53), IsoTp.assemble(response, "7BB"))
    }

    @Test
    fun textAnswersAreNotFrames() {
        assertNull(IsoTp.assemble("NO DATA", "7BB"))
        assertNull(IsoTp.assemble("SEARCHING...", "7BB"))
        assertNull(IsoTp.assemble("", "7BB"))
    }

    @Test
    fun positiveAndNegativeAnswers() {
        assertTrue(IsoTp.isPositive(listOf(0x61, 0x01), 0x21))
        assertTrue(!IsoTp.isPositive(listOf(0x7F, 0x21, 0x12), 0x21))
        assertEquals(0x12, IsoTp.negativeCode(listOf(0x7F, 0x21, 0x12)))
        assertNull(IsoTp.negativeCode(listOf(0x61, 0x01)))
    }
}

class BroadcastDecoderTest {

    private fun line(id: String, vararg bytes: Int) = listOf(CanLine(id, bytes.toList()))

    @Test
    fun socFrom55B() {
        // 78.4 % → сирі 784 → перші 10 біт: 0xC4 і 0b00
        assertEquals(78.4, LeafDecoder.socFrom55B(line("55B", 0xC4, 0x00, 0, 0))!!, 0.001)
    }

    @Test
    fun socRejectsServiceValue() {
        // 1023 — службове «немає даних»
        assertNull(LeafDecoder.socFrom55B(line("55B", 0xFF, 0xC0, 0, 0)))
    }

    @Test
    fun gidsFrom5BC() {
        // 281 GID → 0x46 і два молодші біти в старших бітах наступного байта
        assertEquals(281, LeafDecoder.gidsFrom5BC(line("5BC", 0x46, 0x40, 0, 0)))
    }

    @Test
    fun odometerFrom5C5() {
        // 96431 км = 0x0178AF, байти 1..3
        assertEquals(96431, LeafDecoder.odometerFrom5C5(line("5C5", 0x00, 0x01, 0x78, 0xAF)))
    }

    @Test
    fun currentAndVoltageFrom1DB() {
        // струм -12.5 А (11 біт зі знаком, крок 0.5), напруга 384.5 В (10 біт, крок 0.5)
        val (current, voltage) = LeafDecoder.currentAndVoltageFrom1DB(
            line("1DB", 0xFC, 0xE0, 0xC0, 0x40)
        )
        assertEquals(-12.5, current!!, 0.001)
        assertEquals(384.5, voltage!!, 0.001)
    }

    @Test
    fun shortFrameGivesNothing() {
        assertNull(LeafDecoder.socFrom55B(line("55B", 0x10)))
        assertNull(LeafDecoder.odometerFrom5C5(line("5C5", 0x00, 0x01)))
        assertNull(LeafDecoder.gidsFrom5BC(emptyList()))
    }

}

class GroupDecoderTest {

    private fun cellsPdu(count: Int, firstMv: Int): List<Int> {
        val pdu = ArrayList<Int>()
        pdu.add(0x61)
        pdu.add(0x02)
        pdu.add(0xFF)                      // службові байти, які декодер має пропустити
        pdu.add(0xFF)
        for (i in 0 until count) {
            val mv = firstMv + (i % 5)
            pdu.add((mv shr 8) and 0xFF)
            pdu.add(mv and 0xFF)
        }
        return pdu
    }

    @Test
    fun cellsAreFoundRegardlessOfLeadingBytes() {
        val cells = LeafDecoder.cellsFromGroup2(cellsPdu(96, 4008))
        assertEquals(96, cells.size)
        assertEquals(4008, cells[0])
        assertEquals(4012, cells[4])
    }

    @Test
    fun tooShortAnswerGivesNoCells() {
        assertEquals(emptyList<Int>(), LeafDecoder.cellsFromGroup2(listOf(0x61, 0x02, 0x0F, 0xA8)))
    }

    @Test
    fun garbageIsNotMistakenForCells() {
        val pdu = ArrayList<Int>()
        pdu.add(0x61)
        pdu.add(0x02)
        repeat(60) { pdu.add(0xFF) }
        assertEquals(emptyList<Int>(), LeafDecoder.cellsFromGroup2(pdu))
    }

    @Test
    fun tempsAreReadAsTriplets() {
        // 61 04, далі трійки «два байти АЦП + градуси»
        val pdu = listOf(
            0x61, 0x04,
            0x03, 0xE8, 18,
            0x03, 0xE0, 19,
            0x03, 0xD8, 20
        )
        assertEquals(listOf(18.0, 19.0, 20.0), LeafDecoder.tempsFromGroup4(pdu))
    }

    @Test
    fun impossibleTemperaturesAreDropped() {
        val pdu = listOf(0x61, 0x04, 0x03, 0xE8, 200, 0x03, 0xE0, 19)
        assertEquals(listOf(19.0), LeafDecoder.tempsFromGroup4(pdu))
    }

    @Test
    fun emptyGroup1GivesNothing() {
        val pdu = ArrayList<Int>()
        pdu.add(0x61)
        pdu.add(0x01)
        repeat(40) { pdu.add(0x00) }
        val values = LeafDecoder.group1(pdu)
        assertNull(values.ah)
        assertNull(values.hxPercent)
        assertNull(values.packVoltage)
    }

    @Test
    fun group1ReadsAhAndHx() {
        val d = IntArray(40)
        // Hx 72.30 % → 7230 = 0x1C3E за зсувом 25
        d[25] = 0x1C
        d[26] = 0x3E
        // Ah 57.4000 → 574000 = 0x08C230 за зсувом 32
        d[32] = 0x08
        d[33] = 0xC2
        d[34] = 0x30
        // напруга 384.50 В → 38450 = 0x9632 за зсувом 20
        d[20] = 0x96
        d[21] = 0x32

        val pdu = listOf(0x61, 0x01) + d.toList()
        val values = LeafDecoder.group1(pdu)
        assertEquals(72.30, values.hxPercent!!, 0.001)
        assertEquals(57.4, values.ah!!, 0.001)
        assertEquals(384.5, values.packVoltage!!, 0.001)
    }
}

class DtcTest {

    @Test
    fun codeLettersAndDigits() {
        assertEquals("P0A7A", DtcCatalog.codeFromUds(0x0A, 0x7A, 0x00))
        assertEquals("U1000", DtcCatalog.codeFromUds(0xD0, 0x00, 0x00))
        assertEquals("C0030", DtcCatalog.codeFromUds(0x40, 0x30, 0x00))
        assertEquals("B1234", DtcCatalog.codeFromUds(0x92, 0x34, 0x00))
    }

    @Test
    fun knownCodeHasDescription() {
        assertTrue(DtcCatalog.isKnown("P0A7A"))
        assertTrue(DtcCatalog.describe("P0A7A").contains("батаре"))
    }

    @Test
    fun unknownCodeSaysSo() {
        val text = DtcCatalog.describe("P1234")
        assertTrue(text.contains("Немає в довіднику"))
        assertTrue(text.contains("силова установка"))
    }

    @Test
    fun udsAnswerIsParsed() {
        // 59 02 <маска> + два коди по 4 байти
        val pdu = listOf(
            0x59, 0x02, 0xFF,
            0x0A, 0xA6, 0x00, 0x09,
            0x0A, 0x7A, 0x00, 0x08
        )
        val items = LeafProtocol.parseUdsDtc(pdu, "LBC")
        assertEquals(2, items.size)
        assertEquals("P0AA6", items[0].code)
        assertTrue(items[0].active)
        assertEquals("P0A7A", items[1].code)
        assertTrue(!items[1].active)
        assertEquals("LBC", items[1].ecu)
    }

    @Test
    fun emptySlotsAreSkipped() {
        val pdu = listOf(0x59, 0x02, 0xFF, 0x00, 0x00, 0x00, 0x00, 0x0A, 0xA6, 0x00, 0x09)
        val items = LeafProtocol.parseUdsDtc(pdu, "LBC")
        assertEquals(1, items.size)
        assertEquals("P0AA6", items[0].code)
    }

    @Test
    fun kwpAnswerIsParsed() {
        val pdu = listOf(0x58, 0x01, 0xD0, 0x00, 0x60)
        val items = LeafProtocol.parseKwpDtc(pdu, "VCM")
        assertEquals(1, items.size)
        assertEquals("U1000", items[0].code)
        assertTrue(items[0].active)
    }
}

class ElmVerdictTest {

    @Test
    fun busSilenceIsNotTheSameAsSilentEcu() {
        assertEquals(ElmVerdict.CanError, Elm327.classify("0100\nCAN ERROR"))
        assertEquals(ElmVerdict.NoData, Elm327.classify("NO DATA"))
        assertEquals(ElmVerdict.Ok, Elm327.classify("7BB0662F18A4E4953"))
        assertEquals(ElmVerdict.Empty, Elm327.classify("   "))
        assertEquals(ElmVerdict.UnableToConnect, Elm327.classify("UNABLE TO CONNECT"))
        assertEquals(ElmVerdict.BufferFull, Elm327.classify("BUFFER FULL"))
    }

    @Test
    fun everyVerdictHasHumanText() {
        for (verdict in ElmVerdict.entries) {
            assertTrue(Elm327.verdictText(verdict).length > 5)
        }
    }
}

class CatalogTest {

    @Test
    fun ecuAddressesAreUniqueAndWellFormed() {
        val requests = EcuCatalog.modules.map { it.requestId }
        val responses = EcuCatalog.modules.map { it.responseId }
        assertEquals(requests.size, requests.toSet().size)
        assertEquals(responses.size, responses.toSet().size)
        for (module in EcuCatalog.modules) {
            assertTrue(module.name, Hex.isHexId(module.requestId))
            assertTrue(module.name, Hex.isHexId(module.responseId))
        }
    }

    @Test
    fun batteryControllerIsPresent() {
        val lbc = EcuCatalog.modules.first { it.requestId == "79B" }
        assertEquals("7BB", lbc.responseId)
    }
}

class DemoDataTest {

    @Test
    fun demoBatteryIsConsistent() {
        val battery = DemoData.battery(7)
        assertEquals(96, battery.cellsMv.size)
        assertTrue(battery.socPercent!! in 0.0..100.0)
        assertTrue(battery.cellMinMv!! > 3000)
        assertTrue(battery.cellDeltaMv!! < 100)
        // напруга пакета має збігатися з сумою комірок у межах відсотка
        val sum = battery.cellsMv.sum() / 1000.0
        assertTrue("сума комірок $sum проти ${battery.packVoltage}", kotlin.math.abs(sum - battery.packVoltage!!) < 6.0)
    }

    @Test
    fun demoPowerFollowsCurrentSign() {
        val battery = DemoData.battery(0)
        assertTrue(battery.powerKw!! < 0.0)
        assertEquals(battery.packVoltage!! * battery.packCurrent!! / 1000.0, battery.powerKw!!, 0.001)
    }

    @Test
    fun energyIsDerivedFromGids() {
        val battery = DemoData.battery(3)
        assertEquals(battery.gids!! * WH_PER_GID / 1000.0, battery.energyKwh!!, 0.001)
    }
}
