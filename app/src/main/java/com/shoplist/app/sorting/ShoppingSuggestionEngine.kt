package com.shoplist.app.sorting

import com.shoplist.app.data.local.Product
import kotlin.math.max
import kotlin.math.min

data class Suggestion(val product: Product, val score: Double)

data class PurchaseStatsResult(val count: Int, val last: Long, val avgIntervalDays: Double)

object PurchaseStats {
    const val DAY_MS = 86_400_000.0

    /** Updates purchase statistics after one more purchase at [now]. */
    fun update(count: Int, last: Long?, avgIntervalDays: Double, now: Long): PurchaseStatsResult {
        val newAvg = if (last != null && count >= 1) {
            val d = max(0.0, (now - last) / DAY_MS)
            (avgIntervalDays * (count - 1) + d) / count
        } else {
            avgIntervalDays
        }
        return PurchaseStatsResult(count + 1, now, newAvg)
    }
}

object ShoppingSuggestionEngine {
    const val MIN_SCORE = 0.55
    const val MIN_PURCHASES = 2
    const val BRANCH_BONUS = 0.1

    fun score(count: Int, last: Long?, avgIntervalDays: Double, now: Long): Double {
        if (count <= 0 || last == null) return 0.0
        val days = max(0.0, (now - last) / PurchaseStats.DAY_MS)
        val frequency = min(count, 10) / 10.0
        val recency = 1.0 / (1.0 + days / 60.0)
        val interval = if (avgIntervalDays > 0) {
            val r = days / avgIntervalDays
            if (r <= 1.5) min(1.0, r) else 1.5 / r
        } else {
            0.3
        }
        return 0.35 * frequency + 0.2 * recency + 0.45 * interval
    }

    fun suggest(
        products: List<Product>,
        inList: Set<Long>,
        boughtAtBranch: Set<Long>,
        now: Long,
        limit: Int = 5
    ): List<Suggestion> =
        products.asSequence()
            .filter { it.purchaseCount >= MIN_PURCHASES && it.id !in inList }
            .map {
                val bonus = if (it.id in boughtAtBranch) BRANCH_BONUS else 0.0
                Suggestion(it, score(it.purchaseCount, it.lastPurchasedAt, it.averagePurchaseInterval, now) + bonus)
            }
            .filter { it.score >= MIN_SCORE }
            .sortedByDescending { it.score }
            .take(limit)
            .toList()
}
