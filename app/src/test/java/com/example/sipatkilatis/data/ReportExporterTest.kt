package com.example.sipatkilatis.data

import com.example.sipatkilatis.model.Feedback
import com.example.sipatkilatis.model.MessageSource
import com.example.sipatkilatis.model.ScanRecord
import com.example.sipatkilatis.model.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exported reports must not contain phone numbers, emails, account numbers, names, or senders. */
class ReportExporterTest {

    @Test
    fun masksPersonalDetails() {
        assertEquals("Hi [name], call [number] now", ReportExporter.mask("Hi Juan Dela Cruz, call 09171234567 now"))
        assertEquals("send to [number]", ReportExporter.mask("send to +63 917 123 4567"))
        assertEquals("Ref. No. [number]", ReportExporter.mask("Ref. No. 156419820"))
        assertEquals("email [email] po", ReportExporter.mask("email juan.cruz@gmail.com po"))
        assertEquals("Good day [name]! Your account", ReportExporter.mask("Good day Maria! Your account"))
        assertEquals("[name], claim now", ReportExporter.mask("<REAL NAME>, claim now"))
        // Scam evidence stays: links, amounts, wording
        assertEquals("Nanalo ka ng P50,000! I-claim sa bit.ly/x", ReportExporter.mask("Nanalo ka ng P50,000! I-claim sa bit.ly/x"))
    }

    @Test
    fun csvHasNoSenderOrNumbers() {
        val records = listOf(
            ScanRecord(1, "Mama Cruz", "Hi Ana, eto number ko 09171234567", MessageSource.SMS, Verdict.SAFE, 0.1f, 0L,
                feedback = Feedback.MARKED_SAFE),
            ScanRecord(2, "09998887777", "GCash: Naka-lock. I-verify sa \"gcash-verify.xyz\"", MessageSource.SMS,
                Verdict.SCAM, 0.9f, 0L, feedback = Feedback.REPORTED),
        )
        val csv = ReportExporter.toCsv(records)
        assertFalse(csv.contains("Mama Cruz"))
        assertFalse(csv.contains("09171234567"))
        assertFalse(csv.contains("09998887777"))
        assertFalse(csv.contains("Ana"))
        assertTrue(csv.contains(",name,") && csv.contains(",number,"))
        assertTrue(csv.contains("\"\"gcash-verify.xyz\"\""))   // quotes escaped for CSV
        assertEquals(3, csv.trim().lines().size)                  // header + 2 rows
    }
}
