package com.example.nissanleafdiag

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.UUID

class Elm327(private val input: java.io.InputStream, private val output: OutputStream) {
    companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private val reader = BufferedReader(InputStreamReader(input, Charset.forName("US-ASCII")))

    @Synchronized
    fun command(command: String, timeoutMs: Long = 2500): String {
        output.write((command.trim() + "\r").toByteArray(Charsets.US_ASCII))
        output.flush()

        val end = System.currentTimeMillis() + timeoutMs
        val sb = StringBuilder()
        while (System.currentTimeMillis() < end) {
            if (reader.ready()) {
                val ch = reader.read()
                if (ch < 0) break
                sb.append(ch.toChar())
                if (ch.toChar() == '>') break
            } else {
                Thread.sleep(10)
            }
        }
        return sb.toString()
            .replace("\r", "\n")
            .replace("\u0000", "")
            .trim()
    }

    fun writeRaw(s: String) {
        output.write(s.toByteArray(Charsets.US_ASCII))
        output.flush()
    }
}
