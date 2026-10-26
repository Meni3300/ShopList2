package com.shoplist.app.sorting

data class SortedItem<T>(val item: T, val isNew: Boolean)

object ShoppingListSorter {
    /**
     * Orders [items] by the branch's personal order ([orders]: productId -> sortIndex).
     * Items without an index go last (in insertion order) and are flagged "new" when the
     * branch already has an order. Without any order at all (first visit) insertion order is used.
     */
    fun <T> sort(
        items: List<T>,
        productId: (T) -> Long,
        createdAt: (T) -> Long,
        orders: Map<Long, Int>
    ): List<SortedItem<T>> {
        val branchHasOrder = orders.isNotEmpty()
        val indexed = items.filter { orders.containsKey(productId(it)) }
            .sortedBy { orders.getValue(productId(it)) }
            .map { SortedItem(it, false) }
        val unindexed = items.filter { !orders.containsKey(productId(it)) }
            .sortedBy(createdAt)
            .map { SortedItem(it, branchHasOrder) }
        return indexed + unindexed
    }
}

object BranchOrderMerger {
    /**
     * [full] = every product currently ordered in the branch (including products that are not on
     * today's list). [displayed] = the new order of today's list after a drag.
     * Products not displayed keep their slots; displayed products are re-assigned into their slots.
     */
    fun merge(full: List<Long>, displayed: List<Long>): List<Long> {
        val displayedSet = displayed.toSet()
        val known = full.toHashSet()
        val extended = full + displayed.filter { it !in known }
        val slots = extended.indices.filter { extended[it] in displayedSet }
        val out = extended.toMutableList()
        slots.forEachIndexed { i, slot -> if (i < displayed.size) out[slot] = displayed[i] }
        return out
    }
}
