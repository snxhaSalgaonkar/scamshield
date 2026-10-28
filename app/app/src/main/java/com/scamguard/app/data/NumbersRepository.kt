package com.scamguard.app.data

import com.scamguard.app.data.db.ReputationEntity
import com.scamguard.app.data.db.TrustedNumberDao
import com.scamguard.app.data.db.TrustedNumberEntity
import com.scamguard.app.reputation.LocalReputation
import com.scamguard.app.util.PhoneNumbers
import kotlinx.coroutines.flow.Flow

/** Where a trusted number came from. */
enum class TrustSource { USER, THIS_WAS_ME }

/** The user's trusted and reported numbers. Numbers are hashed before they are stored. */
class NumbersRepository(
    private val trustedDao: TrustedNumberDao,
    private val reputation: LocalReputation,
    private val reportedFlow: Flow<List<ReputationEntity>>,
) {
    val trusted: Flow<List<TrustedNumberEntity>> = trustedDao.observeAll()
    val reported: Flow<List<ReputationEntity>> = reportedFlow

    suspend fun isTrusted(hash: String?): Boolean = hash != null && trustedDao.isTrusted(hash)

    suspend fun trust(hash: String, prefix: String, mask: String, source: TrustSource, now: Long = System.currentTimeMillis()) {
        trustedDao.upsert(TrustedNumberEntity(hash, prefix, mask, source.name, now))
        reputation.removeReport(hash)
    }

    suspend fun report(hash: String, prefix: String, mask: String) {
        trustedDao.delete(hash)
        reputation.report(hash, prefix, mask)
    }

    /** Adds a typed number; returns false if it cannot be parsed. The raw text is not kept. */
    suspend fun addTyped(raw: String, asTrusted: Boolean): Boolean {
        val parsed = PhoneNumbers.parse(raw) ?: return false
        if (asTrusted) trust(parsed.hash, parsed.prefix, parsed.mask, TrustSource.USER)
        else report(parsed.hash, parsed.prefix, parsed.mask)
        return true
    }

    suspend fun removeTrusted(hash: String) = trustedDao.delete(hash)
    suspend fun removeReport(hash: String) = reputation.removeReport(hash)
}
