package com.example.sipatkilatis.data

import com.example.sipatkilatis.model.MessageSource
import com.example.sipatkilatis.model.ScanRecord
import com.example.sipatkilatis.model.ScanResult
import com.example.sipatkilatis.model.Verdict
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Scan history and trusted contacts. */
interface ScanRepository {
    val history: StateFlow<List<ScanRecord>>
    val trustedContacts: StateFlow<List<String>>
    fun addScan(result: ScanResult, source: MessageSource): ScanRecord
    fun find(id: Long): ScanRecord?
    fun addTrustedContact(sender: String)
    fun removeTrustedContact(sender: String)
}

/**
 * In-memory placeholder with sample data so the screens have something to show.
 * Shared by the UI and the background SMS / notification screening (one instance per app process).
 * Replaced by a Room-backed repository in phase 7 (history currently resets when the app process restarts).
 */
class FakeScanRepository : ScanRepository {
    private val now = System.currentTimeMillis()
    private val hour = 60 * 60 * 1000L

    private val _history = MutableStateFlow(
        listOf(
            ScanRecord(6, "GCash", "Your GCash account is locked. Verify within 24 hours: gcash-verify.xyz/login",
                MessageSource.SMS, Verdict.SCAM, 0.94f, now - 1 * hour),
            ScanRecord(5, "Mama", "Anak, uuwi ka ba mamaya? May ulam pa dito.",
                MessageSource.SMS, Verdict.SAFE, 0.03f, now - 3 * hour),
            ScanRecord(4, "+63 917 555 0142", "Congrats! Nanalo ka ng P50,000. I-claim agad sa bit.ly/claim-prize",
                MessageSource.SMS, Verdict.SCAM, 0.97f, now - 20 * hour),
            ScanRecord(3, "Messenger", "Hi! We're hiring. Earn P1,500 daily just liking videos. Message us now.",
                MessageSource.NOTIFICATION, Verdict.SUSPICIOUS, 0.58f, now - 26 * hour),
            ScanRecord(2, "LBC", "Your parcel is out for delivery today.",
                MessageSource.SMS, Verdict.SAFE, 0.12f, now - 48 * hour),
            ScanRecord(1, "J&T Express", "Parcel on hold. Pay delivery fee of PHP 150 at jnt-ph.top/pay",
                MessageSource.MANUAL, Verdict.SCAM, 0.88f, now - 72 * hour),
        )
    )
    override val history = _history.asStateFlow()

    private val _trusted = MutableStateFlow(listOf("Mama", "Papa"))
    override val trustedContacts = _trusted.asStateFlow()

    override fun addScan(result: ScanResult, source: MessageSource): ScanRecord {
        lateinit var record: ScanRecord
        _history.update { list ->
            record = ScanRecord(
                id = (list.maxOfOrNull { it.id } ?: 0) + 1,
                sender = result.sender ?: "—",
                text = result.text,
                source = source,
                verdict = result.verdict,
                score = result.score,
                timestamp = System.currentTimeMillis(),
                result = result,
            )
            listOf(record) + list
        }
        return record
    }

    override fun find(id: Long): ScanRecord? = _history.value.firstOrNull { it.id == id }

    override fun addTrustedContact(sender: String) {
        val s = sender.trim()
        if (s.isNotEmpty()) _trusted.update { if (s in it) it else it + s }
    }

    override fun removeTrustedContact(sender: String) {
        _trusted.update { it - sender }
    }
}
