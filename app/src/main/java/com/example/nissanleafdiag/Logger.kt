package com.example.nissanleafdiag

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Запис у файли. Усе лягає в приватну папку застосунку на зовнішньому сховищі:
 * файли видно через провідник, і вони зникають разом із застосунком.
 * Нічого нікуди не надсилається — тільки локальний файл.
 */
object Logger {

    private const val CSV_HEADER = "time;soc_percent;pack_voltage_v;pack_current_a;gids"

    private var csvFile: File? = null

    private fun dir(context: Context): File {
        val base = context.getExternalFilesDir("logs") ?: File(context.filesDir, "logs")
        if (!base.exists()) base.mkdirs()
        return base
    }

    private fun stamp(): String =
        SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())

    fun startCsv(context: Context): String? {
        return try {
            val f = File(dir(context), "leaf_${stamp()}.csv")
            f.writeText(CSV_HEADER + "\n")
            csvFile = f
            f.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    fun appendCsv(sample: Sample) {
        val f = csvFile ?: return
        try {
            val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(sample.timeMs))
            val row = buildString {
                append(time).append(';')
                append(sample.socPercent?.let { "%.2f".format(Locale.US, it) } ?: "").append(';')
                append(sample.packVoltage?.let { "%.2f".format(Locale.US, it) } ?: "").append(';')
                append(sample.packCurrent?.let { "%.2f".format(Locale.US, it) } ?: "").append(';')
                append(sample.gids?.toString() ?: "")
            }
            f.appendText(row + "\n")
        } catch (_: Exception) {
        }
    }

    fun stopCsv(): String? {
        val path = csvFile?.absolutePath
        csvFile = null
        return path
    }

    fun isWriting(): Boolean = csvFile != null

    fun saveText(context: Context, prefix: String, text: String): String? {
        return try {
            val f = File(dir(context), "${prefix}_${stamp()}.txt")
            f.writeText(text)
            f.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    fun list(context: Context): List<LogFileInfo> {
        return try {
            dir(context).listFiles()
                ?.filter { it.isFile }
                ?.sortedByDescending { it.lastModified() }
                ?.map { LogFileInfo(it.name, it.length(), it.absolutePath) }
                ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun delete(path: String): Boolean {
        return try {
            File(path).delete()
        } catch (e: Exception) {
            false
        }
    }

    fun folder(context: Context): String = dir(context).absolutePath
}
