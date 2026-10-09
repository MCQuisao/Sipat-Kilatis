package com.example.sipatkilatis.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.sipatkilatis.model.ScanRecord
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * "Export my reports": the scans the user reported as scam or marked as safe, as a CSV with personal details
 * masked. The user picks where to send it from the share sheet; nothing is ever sent automatically.
 */
object ReportExporter {

    private val PHONE = Regex("""(\+?63[\s-]?|\b0)9\d{2}[\s-]?\d{3}[\s-]?\d{4}\b""")
    private val EMAIL = Regex("""\b[\w.+-]+@[\w-]+\.[\w.]+\b""")
    private val LONG_NUMBER = Regex("""\b\d{6,}\b""")                     // account / reference / card numbers
    private val NAME_AFTER_GREETING = Regex(
        // greeting in any case ("Hi", "hi", "HELLO"), then a Capitalized name of 1-3 words
        """\b((?i:hi|hello|dear|good day|ma'?am|sir|mr\.?|ms\.?|mrs\.?|gng\.?|g\.))\s+([A-Z][a-z]+(\s+[A-Z][a-z]+){0,2})"""
    )

    /** Mask phone numbers, emails, long numbers, and a name right after a greeting ("Hi Juan Cruz" -> "Hi [name]"). */
    fun mask(text: String): String = text
        .replace(EMAIL, "[email]")
        .replace(PHONE, "[number]")
        .replace(LONG_NUMBER, "[number]")
        .replace(NAME_AFTER_GREETING) { m -> "${m.groupValues[1]} [name]" }
        .replace("<REAL NAME>", "[name]")

    fun toCsv(records: List<ScanRecord>): String {
        val date = DateTimeFormatter.ISO_LOCAL_DATE_TIME
        val sb = StringBuilder("date,source,verdict,score,user_feedback,sender_type,message\n")
        for (r in records) {
            val senderType = when {
                r.sender == RoomScanRepository.NO_SENDER -> "none"
                r.sender.any { it.isDigit() } -> "number"
                else -> "name"   // names are not exported, only that there was one
            }
            sb.append(Instant.ofEpochMilli(r.timestamp).atZone(ZoneId.systemDefault()).format(date)).append(',')
                .append(r.source.name).append(',')
                .append(r.verdict.name).append(',')
                .append("%.2f".format(r.score)).append(',')
                .append(r.feedback.name).append(',')
                .append(senderType).append(',')
                .append(csvField(mask(r.text))).append('\n')
        }
        return sb.toString()
    }

    /** Writes the CSV into the app's cache and returns a share-sheet intent for it. */
    fun shareIntent(context: Context, records: List<ScanRecord>): Intent {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "sipat_kilatis_reports.csv")
        file.writeText(toCsv(records), Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/csv")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, null)
    }

    private fun csvField(s: String) = "\"" + s.replace("\"", "\"\"").replace("\n", " ") + "\""
}
