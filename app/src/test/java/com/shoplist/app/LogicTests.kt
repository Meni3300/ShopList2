package com.shoplist.app

import com.shoplist.app.data.BackupData
import com.shoplist.app.data.BackupJson
import com.shoplist.app.data.local.Branch
import com.shoplist.app.data.local.BranchProductOrder
import com.shoplist.app.data.local.ListEntry
import com.shoplist.app.data.local.Product
import com.shoplist.app.data.local.ShoppingHistory
import com.shoplist.app.data.local.ShoppingHistoryItem
import com.shoplist.app.data.local.ShoppingItem
import com.shoplist.app.data.local.ShoppingSession
import com.shoplist.app.data.local.ShoppingSessionItem
import com.shoplist.app.data.local.Store
import com.shoplist.app.parser.QuantityMerger
import com.shoplist.app.parser.ShoppingInputParser
import com.shoplist.app.sorting.BranchOrderMerger
import com.shoplist.app.sorting.PurchaseStats
import com.shoplist.app.sorting.ShoppingListSorter
import com.shoplist.app.sorting.ShoppingSuggestionEngine
import com.shoplist.app.utils.TextNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NormalizationTests {
    @Test fun trimsAndCollapsesSpaces() {
        assertEquals("חלב", TextNormalizer.normalize("  חלב  "))
        assertEquals("חלב 3%", TextNormalizer.normalize("חלב    3%"))
    }

    @Test fun removesNiqqud() {
        assertEquals("חלב", TextNormalizer.normalize("חָלָב"))
        assertEquals("חלב", TextNormalizer.display("חָלָב"))
    }

    @Test fun lowercasesForComparison() {
        assertEquals("coca cola", TextNormalizer.normalize("Coca  COLA"))
        assertEquals("Coca Cola", TextNormalizer.display(" Coca  Cola "))
    }

    @Test fun duplicateDetectionUsesNormalizedName() {
        val a = TextNormalizer.normalize("חלב")
        assertEquals(a, TextNormalizer.normalize(" חלב "))
        assertEquals(a, TextNormalizer.normalize("חָלָב"))
    }
}

class ParserTests {
    @Test fun nameOnlyIsOne() {
        val p = ShoppingInputParser.parse("חלב")!!
        assertEquals("חלב", p.name); assertEquals(1, p.quantity)
    }

    @Test fun quantityFirst() {
        val p = ShoppingInputParser.parse("2 חלב")!!
        assertEquals("חלב", p.name); assertEquals(2, p.quantity)
    }

    @Test fun quantityLast() {
        val p = ShoppingInputParser.parse("ביצים 12")!!
        assertEquals("ביצים", p.name); assertEquals(12, p.quantity)
        val q = ShoppingInputParser.parse("12 ביצים")!!
        assertEquals("ביצים", q.name); assertEquals(12, q.quantity)
    }

    @Test fun multiWordName() {
        val p = ShoppingInputParser.parse("נייר טואלט 3")!!
        assertEquals("נייר טואלט", p.name); assertEquals(3, p.quantity)
    }

    @Test fun decimalStaysInName() {
        val p = ShoppingInputParser.parse("קולה 1.5")!!
        assertEquals("קולה 1.5", p.name); assertEquals(1, p.quantity)
    }

    @Test fun blankIsNull() {
        assertNull(ShoppingInputParser.parse("   "))
        assertNull(ShoppingInputParser.parse(""))
    }

    @Test fun quantityMerging() {
        assertEquals(3, QuantityMerger.merge(2, 1))
        assertEquals(2, QuantityMerger.merge(1, 1))
    }
}

class SortingTests {
    private fun e(pid: Long, name: String, created: Long) =
        ListEntry(itemId = pid, productId = pid, name = name, category = "אחר", quantity = 1, createdAt = created)

    private val milk = e(1, "חלב", 1)
    private val bread = e(2, "לחם", 2)
    private val coffee = e(3, "קפה", 3)
    private val choc = e(4, "שוקולד", 4)

    private fun sort(items: List<ListEntry>, orders: Map<Long, Int>) =
        ShoppingListSorter.sort(items, { it.productId }, { it.createdAt }, orders)

    @Test fun firstVisitUsesInsertionOrderAndNothingIsNew() {
        val r = sort(listOf(coffee, milk, bread), emptyMap())
        assertEquals(listOf("חלב", "לחם", "קפה"), r.map { it.item.name })
        assertTrue(r.none { it.isNew })
    }

    @Test fun branchOrderIsApplied() {
        val r = sort(listOf(coffee, milk, bread), mapOf(2L to 0, 1L to 1, 3L to 2))
        assertEquals(listOf("לחם", "חלב", "קפה"), r.map { it.item.name })
    }

    @Test fun newProductGoesLastAndIsFlagged() {
        val r = sort(listOf(choc, milk, bread, coffee), mapOf(2L to 0, 1L to 1, 3L to 2))
        assertEquals(listOf("לחם", "חלב", "קפה", "שוקולד"), r.map { it.item.name })
        assertTrue(r.last().isNew)
        assertFalse(r.first().isNew)
    }

    @Test fun branchesAreIndependent() {
        val netivot = mapOf(2L to 0, 1L to 1)      // bread, milk
        val tiberias = mapOf(1L to 0, 2L to 1)     // milk, bread
        val items = listOf(milk, bread)
        assertEquals(listOf("לחם", "חלב"), sort(items, netivot).map { it.item.name })
        assertEquals(listOf("חלב", "לחם"), sort(items, tiberias).map { it.item.name })
    }

    @Test fun productsNotOnTheListKeepTheirSlots() {
        // full order: bread(2), milk(1), cheese(9), coffee(3); today: milk, coffee, bread
        val full = listOf(2L, 1L, 9L, 3L)
        // user drags so that today's list becomes: coffee, bread, milk
        val merged = BranchOrderMerger.merge(full, listOf(3L, 2L, 1L))
        assertEquals(listOf(3L, 2L, 9L, 1L), merged) // cheese (9) stays in slot 2
    }

    @Test fun draggingANewProductIntoTheMiddle() {
        val full = listOf(2L, 1L, 3L)
        val merged = BranchOrderMerger.merge(full, listOf(2L, 4L, 1L, 3L))
        assertEquals(listOf(2L, 4L, 1L, 3L), merged)
    }

    @Test fun firstDragCreatesFullOrder() {
        assertEquals(listOf(3L, 1L, 2L), BranchOrderMerger.merge(emptyList(), listOf(3L, 1L, 2L)))
    }

    @Test fun learningScenarioFromSpec() {
        // user arranged: bread, milk, eggs, coffee ; next time items come as coffee, eggs, milk, bread
        val orders = mapOf(2L to 0, 1L to 1, 5L to 2, 3L to 3)
        val eggs = e(5, "ביצים", 9)
        val r = sort(listOf(coffee, eggs, milk, bread), orders)
        assertEquals(listOf("לחם", "חלב", "ביצים", "קפה"), r.map { it.item.name })
    }
}

class SuggestionTests {
    private val day = 86_400_000L
    private val now = 1_000 * day

    @Test fun regularProductDueAgainScoresHigh() {
        val s = ShoppingSuggestionEngine.score(10, now - 8 * day, 7.0, now)
        assertTrue("score=$s", s >= ShoppingSuggestionEngine.MIN_SCORE)
    }

    @Test fun oneOffPurchaseLongAgoScoresLow() {
        val s = ShoppingSuggestionEngine.score(1, now - 180 * day, 0.0, now)
        assertTrue("score=$s", s < ShoppingSuggestionEngine.MIN_SCORE)
    }

    @Test fun suggestionsExcludeItemsAlreadyOnTheList() {
        val milk = Product(1, "חלב", "חלב", "מוצרי חלב", 0, 10, now - 8 * day, 7.0)
        val eggs = Product(2, "ביצים", "ביצים", "מוצרי חלב", 0, 10, now - 8 * day, 7.0)
        val r = ShoppingSuggestionEngine.suggest(listOf(milk, eggs), setOf(1L), emptySet(), now)
        assertEquals(listOf(2L), r.map { it.product.id })
    }

    @Test fun neverSuggestsRarelyBoughtProducts() {
        val p = Product(1, "x", "x", "אחר", 0, 1, now - 7 * day, 7.0)
        assertTrue(ShoppingSuggestionEngine.suggest(listOf(p), emptySet(), emptySet(), now).isEmpty())
    }

    @Test fun branchHistoryBoostsScore() {
        val p = Product(1, "x", "x", "אחר", 0, 4, now - 6 * day, 7.0)
        val base = ShoppingSuggestionEngine.suggest(listOf(p), emptySet(), emptySet(), now)
        val boosted = ShoppingSuggestionEngine.suggest(listOf(p), emptySet(), setOf(1L), now)
        val a = base.firstOrNull()?.score ?: 0.0
        val b = boosted.firstOrNull()?.score ?: 0.0
        assertTrue(b > a)
    }

    @Test fun purchaseStatsTrackAverageInterval() {
        val first = PurchaseStats.update(0, null, 0.0, 0L)
        assertEquals(1, first.count)
        val second = PurchaseStats.update(first.count, first.last, first.avgIntervalDays, 7 * day)
        assertEquals(2, second.count); assertEquals(7.0, second.avgIntervalDays, 0.001)
        val third = PurchaseStats.update(second.count, second.last, second.avgIntervalDays, 21 * day)
        assertEquals(10.5, third.avgIntervalDays, 0.001)
    }
}

class BackupTests {
    @Test fun exportImportRoundTrip() {
        val data = BackupData(
            products = listOf(Product(1, "חלב", "חלב", "מוצרי חלב", 5, 3, 99L, 6.5), Product(2, "לחם", "לחם", "מאפים", 6)),
            stores = listOf(Store(1, "ויקטורי", 1)),
            branches = listOf(Branch(1, 1, "נתיבות", 2, null), Branch(2, 1, "טבריה", 3, 77L)),
            shoppingItems = listOf(ShoppingItem(1, 1, 2, false, 10)),
            branchProductOrders = listOf(BranchProductOrder(1, 1, 0, 11), BranchProductOrder(1, 2, 1, 11)),
            shoppingSessions = listOf(ShoppingSession(1, 1, 1, 12, true)),
            shoppingSessionItems = listOf(ShoppingSessionItem(1, 1, 1, 2, true, 13), ShoppingSessionItem(2, 1, 2, 1, false, null)),
            shoppingHistory = listOf(ShoppingHistory(1, 1, 1, 14, 1)),
            shoppingHistoryItems = listOf(ShoppingHistoryItem(1, 1, 1, "חלב", 2, 14)),
            themeMode = "dark"
        )
        val back = BackupJson.fromJson(BackupJson.toJson(data))
        assertEquals(data, back)
    }

    @Test(expected = Exception::class) fun rejectsForeignJson() {
        BackupJson.fromJson("{\"hello\": 1}")
    }

    @Test(expected = Exception::class) fun rejectsGarbage() {
        BackupJson.fromJson("not json at all")
    }
}

class SyncTests {
    @Test fun keyIdsAreStableAndPositive() {
        val a = com.shoplist.app.utils.KeyIds.hash("חלב")
        assertEquals(a, com.shoplist.app.utils.KeyIds.hash("חלב"))
        assertTrue(a > 0)
        assertTrue(a != com.shoplist.app.utils.KeyIds.hash("לחם"))
    }

    @Test fun legacyIdsAreTranslatedToNameBasedIds() {
        val h = com.shoplist.app.utils.KeyIds::hash
        val data = BackupData(
            products = listOf(Product(7, "חלב", "חלב", "אחר", 1), Product(8, "לחם", "לחם", "אחר", 2)),
            stores = listOf(Store(3, "ויקטורי", 1)),
            branches = listOf(Branch(5, 3, "נתיבות", 1)),
            shoppingItems = listOf(ShoppingItem(1, 7, 2, false, 5)),
            branchProductOrders = listOf(BranchProductOrder(5, 8, 0, 1), BranchProductOrder(5, 7, 1, 1)),
            shoppingSessions = emptyList(),
            shoppingSessionItems = emptyList(),
            shoppingHistory = listOf(ShoppingHistory(1, 3, 5, 100, 1)),
            shoppingHistoryItems = listOf(ShoppingHistoryItem(1, 1, 7, "חלב", 2, 100)),
            themeMode = "system"
        )
        val docs = com.shoplist.app.sync.HouseholdDocBuilder.build(data)
        val milk = h("חלב"); val bread = h("לחם")
        val storeId = h("ויקטורי")
        val branchId = com.shoplist.app.utils.KeyIds.branchId(storeId, "נתיבות")

        assertTrue(docs.any { it.collection == "products" && it.id == milk.toString() })
        val item = docs.first { it.collection == "items" }
        assertEquals(milk.toString(), item.id)
        assertEquals(milk, item.fields["productId"])
        val order = docs.first { it.collection == "orders" }
        assertEquals(branchId.toString(), order.id)
        assertEquals(listOf(bread, milk), order.fields["productIds"])
        val hist = docs.first { it.collection == "history" }
        assertEquals(branchId, hist.fields["branchId"])
        assertEquals(storeId, hist.fields["storeId"])
    }

    @Test fun orphanRowsAreSkipped() {
        val data = BackupData(
            products = listOf(Product(7, "חלב", "חלב", "אחר", 1)),
            stores = emptyList(),
            branches = listOf(Branch(5, 3, "נתיבות", 1)),
            shoppingItems = listOf(ShoppingItem(1, 99, 2, false, 5)),
            branchProductOrders = listOf(BranchProductOrder(5, 7, 0, 1)),
            shoppingSessions = emptyList(), shoppingSessionItems = emptyList(),
            shoppingHistory = emptyList(), shoppingHistoryItems = emptyList(), themeMode = "system"
        )
        val docs = com.shoplist.app.sync.HouseholdDocBuilder.build(data)
        assertTrue(docs.none { it.collection == "branches" || it.collection == "items" || it.collection == "orders" })
    }
}
