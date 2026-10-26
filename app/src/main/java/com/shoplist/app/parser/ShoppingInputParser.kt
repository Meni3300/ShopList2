package com.shoplist.app.parser

import com.shoplist.app.utils.TextNormalizer

data class ProductInput(val name: String, val quantity: Int)

object ShoppingInputParser {
    private fun isQuantity(token: String): Boolean =
        token.isNotEmpty() && token.length <= 4 && token.all { it.isDigit() }

    /** "2 חלב" / "חלב 2" / "חלב" -> ProductInput. Returns null for blank input. */
    fun parse(raw: String): ProductInput? {
        val clean = TextNormalizer.display(raw)
        if (clean.isEmpty()) return null
        val tokens = clean.split(" ")
        if (tokens.size > 1 && isQuantity(tokens.first())) {
            val q = tokens.first().toIntOrNull() ?: 1
            return ProductInput(tokens.drop(1).joinToString(" "), q.coerceAtLeast(1))
        }
        if (tokens.size > 1 && isQuantity(tokens.last())) {
            val q = tokens.last().toIntOrNull() ?: 1
            return ProductInput(tokens.dropLast(1).joinToString(" "), q.coerceAtLeast(1))
        }
        return ProductInput(clean, 1)
    }
}

object QuantityMerger {
    fun merge(existing: Int, added: Int): Int = (existing + added).coerceIn(1, 9999)
}
