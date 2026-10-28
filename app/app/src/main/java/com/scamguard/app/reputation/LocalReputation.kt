package com.scamguard.app.reputation

import android.content.Context
import com.scamguard.app.data.db.ReputationDao
import com.scamguard.app.data.db.ReputationEntity
import org.json.JSONArray
import org.json.JSONObject

/** Reputation status of a number. */
enum class ReputationStatus { SCAM, TRUSTED_ORG }

/** Where a reputation entry came from. */
enum class ReputationSource { SEED, LOCAL }

/**
 * Local reputation cache backed by Room and seeded from assets/reputation_seed.json
 * (known bank numbers and demo scam numbers, all as hashes). User reports add LOCAL rows.
 */
class LocalReputation(private val context: Context, private val dao: ReputationDao) {

    /** Imports the bundled seed list; existing rows (e.g. local reports) are kept. */
    suspend fun importSeed(now: Long = System.currentTimeMillis()) {
        val json = JSONObject(context.assets.open("reputation_seed.json").bufferedReader().use { it.readText() })
        val entries = json.getJSONArray("knownBankNumbers").toEntries(ReputationStatus.TRUSTED_ORG, now) +
            json.getJSONArray("scamNumbers").toEntries(ReputationStatus.SCAM, now)
        dao.insertIgnore(entries)
        dao.deleteExpired(now)
    }

    suspend fun lookup(hash: String?): ReputationEntity? = hash?.let { dao.get(it) }

    suspend fun isReported(hash: String?): Boolean {
        val entry = lookup(hash) ?: return false
        return entry.status == ReputationStatus.SCAM.name || entry.localReports > 0
    }

    suspend fun isKnownBank(hash: String?): Boolean =
        lookup(hash)?.status == ReputationStatus.TRUSTED_ORG.name

    /** Records a local scam report for a number. */
    suspend fun report(hash: String, prefix: String, mask: String, now: Long = System.currentTimeMillis()) {
        val existing = dao.get(hash)
        dao.upsert(
            ReputationEntity(
                numberHash = hash,
                prefix = prefix,
                mask = existing?.mask ?: mask,
                localReports = (existing?.localReports ?: 0) + 1,
                status = ReputationStatus.SCAM.name,
                source = ReputationSource.LOCAL.name,
                expiresAt = null,
                updatedAt = now,
            )
        )
    }

    suspend fun removeReport(hash: String) = dao.deleteLocal(hash)

    private fun JSONArray.toEntries(status: ReputationStatus, now: Long) = (0 until length()).map {
        val o = getJSONObject(it)
        ReputationEntity(
            numberHash = o.getString("hash"),
            prefix = o.getString("prefix"),
            mask = o.optString("mask").ifEmpty { null },
            localReports = 0,
            status = status.name,
            source = ReputationSource.SEED.name,
            expiresAt = null,
            updatedAt = now,
        )
    }
}
