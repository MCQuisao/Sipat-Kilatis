package com.example.sipatkilatis.data

import com.example.sipatkilatis.data.db.AppDatabase
import com.example.sipatkilatis.data.db.ScanEntity
import com.example.sipatkilatis.data.db.TrustedContactEntity
import com.example.sipatkilatis.model.Feedback
import com.example.sipatkilatis.model.Flag
import com.example.sipatkilatis.model.MessageSource
import com.example.sipatkilatis.model.ScanRecord
import com.example.sipatkilatis.model.ScanResult
import com.example.sipatkilatis.model.Verdict
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.json.JSONArray
import org.json.JSONObject

/** Scan history, feedback, and trusted contacts. */
interface ScanRepository {
    val history: StateFlow<List<ScanRecord>>
    val trustedContacts: StateFlow<List<String>>
    suspend fun addScan(result: ScanResult, source: MessageSource): ScanRecord
    suspend fun find(id: Long): ScanRecord?
    suspend fun setFeedback(id: Long, feedback: Feedback)
    suspend fun withFeedback(): List<ScanRecord>
    suspend fun clearHistory()
    suspend fun deleteScan(id: Long)
    /** Put a deleted scan back (Undo). */
    suspend fun restoreScan(record: ScanRecord)
    suspend fun addTrustedContact(sender: String)
    suspend fun removeTrustedContact(sender: String)
    /** Read straight from the database (not the StateFlow, which may not have loaded yet at cold start). */
    suspend fun trustedNow(): List<String>
}

/**
 * Room-backed repository: history and trusted contacts survive app restarts. Shared by the UI and the
 * background SMS / notification screening. Everything stays in the app's private database file.
 */
class RoomScanRepository(db: AppDatabase, scope: CoroutineScope) : ScanRepository {
    private val dao = db.scanDao()

    // Eagerly started so .value is always current (the detector reads trusted contacts on every scan)
    override val history: StateFlow<List<ScanRecord>> =
        dao.observeAll().map { rows -> rows.map { it.toRecord() } }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override val trustedContacts: StateFlow<List<String>> =
        dao.observeTrusted().map { rows -> rows.map { it.sender } }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override suspend fun addScan(result: ScanResult, source: MessageSource): ScanRecord {
        val entity = result.toEntity(source)
        return entity.copy(id = dao.insert(entity)).toRecord()
    }

    override suspend fun find(id: Long): ScanRecord? = dao.get(id)?.toRecord()

    override suspend fun setFeedback(id: Long, feedback: Feedback) = dao.setFeedback(id, feedback.name)

    override suspend fun withFeedback(): List<ScanRecord> = dao.withFeedback().map { it.toRecord() }

    override suspend fun clearHistory() = dao.clear()

    override suspend fun deleteScan(id: Long) = dao.delete(id)

    override suspend fun restoreScan(record: ScanRecord) {
        val result = record.result ?: return
        dao.restore(result.toEntity(record.source).copy(id = record.id, timestamp = record.timestamp,
            userFeedback = record.feedback.name))
    }

    override suspend fun addTrustedContact(sender: String) {
        val s = sender.trim()
        if (s.isNotEmpty() && s != NO_SENDER) dao.addTrusted(TrustedContactEntity(s, System.currentTimeMillis()))
    }

    override suspend fun removeTrustedContact(sender: String) = dao.removeTrusted(sender)

    override suspend fun trustedNow(): List<String> = dao.trustedList().map { it.sender }

    companion object {
        const val NO_SENDER = "—"   // manual checks have no sender
    }
}

// ---- mapping between the database row and the app's models

private fun ScanResult.toEntity(source: MessageSource) = ScanEntity(
    sender = sender ?: RoomScanRepository.NO_SENDER,
    text = text,
    source = source.name,
    verdict = verdict.name,
    score = score,
    mlScore = mlScore,
    urlScore = urlScore,
    rulesScore = rulesScore,
    flagsJson = JSONArray(flags.map { JSONObject().put("id", it.id).put("en", it.reasonEn).put("fil", it.reasonFil) }).toString(),
    highlightsJson = JSONArray(highlights.map { JSONArray(listOf(it.first, it.last)) }).toString(),
    timestamp = System.currentTimeMillis(),
)

private fun ScanEntity.toRecord(): ScanRecord {
    val flags = JSONArray(flagsJson).let { a ->
        (0 until a.length()).map { i -> a.getJSONObject(i).let { Flag(it.getString("id"), it.getString("en"), it.getString("fil")) } }
    }
    val highlights = JSONArray(highlightsJson).let { a ->
        (0 until a.length()).map { i -> a.getJSONArray(i).let { it.getInt(0)..it.getInt(1) } }
    }
    val verdict = Verdict.valueOf(verdict)
    val result = ScanResult(
        text = text, sender = sender.takeIf { it != RoomScanRepository.NO_SENDER }, verdict = verdict, score = score,
        mlScore = mlScore, urlScore = urlScore, rulesScore = rulesScore, flags = flags, highlights = highlights,
    )
    return ScanRecord(id, sender, text, MessageSource.valueOf(source), verdict, score, timestamp, result,
        Feedback.valueOf(userFeedback))
}
