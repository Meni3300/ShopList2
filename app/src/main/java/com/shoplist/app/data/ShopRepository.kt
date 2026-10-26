package com.shoplist.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.withTransaction
import com.shoplist.app.data.local.AppDatabase
import com.shoplist.app.data.local.Branch
import com.shoplist.app.data.local.BranchInfo
import com.shoplist.app.data.local.BranchProductOrder
import com.shoplist.app.data.local.HistoryRow
import com.shoplist.app.data.local.ListEntry
import com.shoplist.app.data.local.OrderEntry
import com.shoplist.app.data.local.Product
import com.shoplist.app.data.local.SessionEntry
import com.shoplist.app.data.local.SessionInfo
import com.shoplist.app.data.local.ShoppingHistory
import com.shoplist.app.data.local.ShoppingHistoryItem
import com.shoplist.app.data.local.ShoppingItem
import com.shoplist.app.data.local.ShoppingSession
import com.shoplist.app.data.local.ShoppingSessionItem
import com.shoplist.app.data.local.Store
import com.shoplist.app.data.local.StoreRow
import com.shoplist.app.parser.QuantityMerger
import com.shoplist.app.parser.ShoppingInputParser
import com.shoplist.app.sorting.BranchOrderMerger
import com.shoplist.app.sorting.PurchaseStats
import com.shoplist.app.sorting.ShoppingListSorter
import com.shoplist.app.sorting.ShoppingSuggestionEngine
import com.shoplist.app.sorting.Suggestion
import com.shoplist.app.sync.HouseholdSync
import com.shoplist.app.utils.KeyIds
import com.shoplist.app.utils.Categories
import com.shoplist.app.utils.TextNormalizer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * Single access point to all data. Every mutating call persists immediately (no "save" step).
 * All Room calls are suspend / Flow based, so none runs on the main thread.
 */
class ShopRepository(
    private val db: AppDatabase,
    private val dataStore: DataStore<Preferences>,
    private val sync: HouseholdSync?,
    private val onListChanged: () -> Unit
) {
    private val dao = db.dao()
    private val themeKey = stringPreferencesKey("theme_mode")

    // ---------------- settings ----------------
    val themeMode: Flow<String> = dataStore.data.map { it[themeKey] ?: "system" }

    suspend fun setThemeMode(mode: String) {
        dataStore.edit { it[themeKey] = mode }
    }

    private suspend fun currentTheme(): String = dataStore.data.first()[themeKey] ?: "system"

    // ---------------- observation ----------------
    fun observeList(): Flow<List<ListEntry>> = dao.observeList()
    suspend fun listSnapshot(): List<ListEntry> = dao.listSnapshot()
    fun observeProducts(): Flow<List<Product>> = dao.observeProducts()
    fun observeStores(): Flow<List<StoreRow>> = dao.observeStores()
    fun observeBranches(storeId: Long): Flow<List<Branch>> = dao.observeBranches(storeId)
    fun observeBranchInfo(branchId: Long): Flow<BranchInfo?> = dao.observeBranchInfo(branchId)
    fun observeLastBranch(): Flow<BranchInfo?> = dao.observeLastBranch()
    fun observeOrders(branchId: Long): Flow<List<BranchProductOrder>> = dao.observeOrders(branchId)
    fun observeBranchOrder(branchId: Long): Flow<List<OrderEntry>> = dao.observeBranchOrder(branchId)
    fun observeActiveSession(): Flow<SessionInfo?> = dao.observeActiveSession()
    fun observeSessionItems(sessionId: Long): Flow<List<SessionEntry>> = dao.observeSessionItems(sessionId)
    fun observeHistory(): Flow<List<HistoryRow>> = dao.observeHistory()
    fun observeHistoryItems(id: Long): Flow<List<ShoppingHistoryItem>> = dao.observeHistoryItems(id)

    // ---------------- shopping list ----------------
    /** Parses "2 חלב" / "חלב 2" / "חלב", merges duplicates. Returns false for blank input. */
    suspend fun addFromText(text: String): Boolean {
        val input = ShoppingInputParser.parse(text) ?: return false
        val result = db.withTransaction { addInternal(input.name, input.quantity) }
        if (result != null) sync?.addToList(result.first, result.second, input.quantity)
        onListChanged()
        return true
    }

    private suspend fun addInternal(name: String, quantity: Int): Pair<Product, ShoppingItem>? {
        val norm = TextNormalizer.normalize(name)
        if (norm.isEmpty()) return null
        val now = System.currentTimeMillis()
        var product = dao.productByNormalized(norm)
        if (product == null) {
            var id = KeyIds.hash(norm)
            if (dao.productById(id) != null) id = KeyIds.hash("$norm#$now") // id taken by a renamed product
            val created = Product(
                id = id,
                name = TextNormalizer.display(name),
                normalizedName = norm,
                category = Categories.guess(name),
                createdAt = now
            )
            dao.insertProduct(created)
            product = created
        }
        val existing = dao.itemByProduct(product.id)
        val item = if (existing == null) {
            val created = ShoppingItem(id = product.id, productId = product.id, quantity = quantity, createdAt = now)
            dao.insertItem(created)
            created
        } else {
            val merged = QuantityMerger.merge(existing.quantity, quantity)
            dao.setItemQuantity(existing.id, merged)
            existing.copy(quantity = merged)
        }
        return product to item
    }

    /** One-tap add from suggestions: never duplicates and never bumps an existing quantity. */
    suspend fun addProductById(productId: Long) {
        val added = db.withTransaction {
            if (dao.productById(productId) != null && dao.itemByProduct(productId) == null) {
                val item = ShoppingItem(
                    id = productId, productId = productId, quantity = 1, createdAt = System.currentTimeMillis()
                )
                dao.insertItem(item)
                item
            } else {
                null
            }
        }
        if (added != null) sync?.putItem(added)
        onListChanged()
    }

    suspend fun setQuantity(itemId: Long, quantity: Int) {
        val item = dao.itemById(itemId)
        if (quantity < 1) {
            dao.deleteItem(itemId)
            if (item != null) sync?.deleteItem(item.productId)
        } else {
            val q = quantity.coerceAtMost(9999)
            dao.setItemQuantity(itemId, q)
            if (item != null) sync?.setItemQuantity(item.productId, q)
        }
        onListChanged()
    }

    suspend fun removeItem(itemId: Long) {
        val item = dao.itemById(itemId)
        dao.deleteItem(itemId)
        if (item != null) sync?.deleteItem(item.productId)
        onListChanged()
    }

    // ---------------- products ----------------
    suspend fun updateProduct(id: Long, name: String, category: String): Boolean {
        val display = TextNormalizer.display(name)
        val norm = TextNormalizer.normalize(name)
        if (norm.isEmpty()) return false
        val ok = db.withTransaction {
            val p = dao.productById(id) ?: return@withTransaction false
            val other = dao.productByNormalized(norm)
            if (other != null && other.id != id) return@withTransaction false
            dao.updateProduct(p.copy(name = display, normalizedName = norm, category = category))
            true
        }
        if (ok) dao.productById(id)?.let { sync?.upsertProduct(it) }
        onListChanged()
        return ok
    }

    suspend fun deleteProduct(id: Long) {
        val orderBranches = db.withTransaction {
            val branches = dao.allOrders().filter { it.productId == id }.map { it.branchId }
            dao.deleteItemsOfProduct(id)
            dao.deleteOrdersOfProduct(id)
            dao.deleteProduct(id)
            branches
        }
        sync?.deleteProduct(id, orderBranches)
        onListChanged()
    }

    // ---------------- stores & branches ----------------
    suspend fun createStore(name: String): Boolean {
        val display = TextNormalizer.display(name)
        val norm = TextNormalizer.normalize(name)
        if (norm.isEmpty()) return false
        val store = db.withTransaction {
            if (dao.allStores().any { TextNormalizer.normalize(it.name) == norm }) {
                null
            } else {
                val s = Store(id = KeyIds.hash(norm), name = display, createdAt = System.currentTimeMillis())
                dao.insertStore(s)
                s
            }
        }
        if (store != null) sync?.upsertStore(store)
        return store != null
    }

    suspend fun createBranch(storeId: Long, name: String): Boolean {
        val display = TextNormalizer.display(name)
        val norm = TextNormalizer.normalize(name)
        if (norm.isEmpty()) return false
        val branch = db.withTransaction {
            if (dao.branchesOfStore(storeId).any { TextNormalizer.normalize(it.name) == norm }) {
                null
            } else {
                val b = Branch(
                    id = KeyIds.branchId(storeId, norm),
                    storeId = storeId,
                    name = display,
                    createdAt = System.currentTimeMillis()
                )
                dao.insertBranch(b)
                b
            }
        }
        if (branch != null) sync?.upsertBranch(branch)
        return branch != null
    }

    private suspend fun deleteBranchInternal(branchId: Long) {
        dao.deleteOrdersOfBranch(branchId)
        dao.deactivateSessionsOfBranch(branchId)
        dao.deleteBranch(branchId)
    }

    suspend fun deleteBranch(branchId: Long) {
        db.withTransaction { deleteBranchInternal(branchId) }
        sync?.deleteBranch(branchId)
    }

    suspend fun deleteStore(storeId: Long) {
        val branchIds = db.withTransaction {
            val ids = dao.branchesOfStore(storeId).map { it.id }
            ids.forEach { deleteBranchInternal(it) }
            dao.deleteStore(storeId)
            ids
        }
        sync?.deleteStore(storeId, branchIds)
    }

    // ---------------- per-branch order ----------------
    /** [displayedProductIds] = today's list in the order the user just arranged it. */
    suspend fun updateBranchOrder(branchId: Long, displayedProductIds: List<Long>) {
        val merged = db.withTransaction {
            val full = dao.getOrders(branchId).sortedBy { it.sortIndex }.map { it.productId }
            val result = BranchOrderMerger.merge(full, displayedProductIds.distinct())
            val now = System.currentTimeMillis()
            dao.upsertOrders(result.mapIndexed { i, pid -> BranchProductOrder(branchId, pid, i, now) })
            result
        }
        sync?.setOrder(branchId, merged)
    }

    /** Deletes only the order rows; products stay untouched. */
    suspend fun resetBranchOrder(branchId: Long) {
        dao.deleteOrdersOfBranch(branchId)
        sync?.deleteOrder(branchId)
    }

    // ---------------- shopping sessions ----------------
    /** Returns the session id, the already-active session's id, or -1 when the list is empty. */
    suspend fun startSession(storeId: Long, branchId: Long): Long {
        val id = db.withTransaction {
            val active = dao.activeSession()
            if (active != null) return@withTransaction active.id
            val items = dao.listSnapshot()
            if (items.isEmpty()) return@withTransaction -1L
            val now = System.currentTimeMillis()
            val orders = dao.getOrders(branchId).associate { it.productId to it.sortIndex }
            val sorted = ShoppingListSorter.sort(items, { it.productId }, { it.createdAt }, orders)
            val sessionId = dao.insertSession(
                ShoppingSession(storeId = storeId, branchId = branchId, startedAt = now, isActive = true)
            )
            dao.insertSessionItems(
                sorted.map {
                    ShoppingSessionItem(
                        sessionId = sessionId,
                        productId = it.item.productId,
                        quantity = it.item.quantity
                    )
                }
            )
            dao.setBranchLastUsed(branchId, now)
            sessionId
        }
        if (id > 0) sync?.setBranchLastUsed(branchId, System.currentTimeMillis())
        return id
    }

    suspend fun toggleSessionItem(sessionItemId: Long) {
        dao.toggleSessionItem(sessionItemId, System.currentTimeMillis())
    }

    suspend fun cancelSession(sessionId: Long) {
        dao.deactivateSession(sessionId)
    }

    /** Writes history, updates purchase statistics, removes bought items from the list. */
    suspend fun finishSession(sessionId: Long) {
        val result = db.withTransaction {
            val session = dao.sessionById(sessionId) ?: return@withTransaction null
            val bought = dao.sessionItemsRaw(sessionId).filter { it.isCompleted }
            val now = System.currentTimeMillis()
            var history: ShoppingHistory? = null
            val historyItems = ArrayList<ShoppingHistoryItem>()
            val updatedProducts = ArrayList<Product>()
            if (bought.isNotEmpty()) {
                val historyId = KeyIds.hash("hist|$now|${session.branchId}|${UUID.randomUUID()}")
                val h = ShoppingHistory(
                    id = historyId,
                    storeId = session.storeId,
                    branchId = session.branchId,
                    createdAt = now,
                    itemCount = bought.size
                )
                dao.insertHistory(h)
                history = h
                for (si in bought) {
                    val product = dao.productById(si.productId)
                    historyItems.add(
                        ShoppingHistoryItem(
                            historyId = historyId,
                            productId = si.productId,
                            productNameSnapshot = product?.name ?: "?",
                            quantity = si.quantity,
                            completedAt = si.completedAt ?: now
                        )
                    )
                    if (product != null) {
                        val s = PurchaseStats.update(
                            product.purchaseCount, product.lastPurchasedAt, product.averagePurchaseInterval, now
                        )
                        val updated = product.copy(
                            purchaseCount = s.count,
                            lastPurchasedAt = s.last,
                            averagePurchaseInterval = s.avgIntervalDays
                        )
                        dao.updateProduct(updated)
                        updatedProducts.add(updated)
                    }
                    dao.deleteItemsOfProduct(si.productId)
                }
                dao.insertHistoryItems(historyItems)
            }
            dao.setBranchLastUsed(session.branchId, now)
            dao.deactivateSession(sessionId)
            FinishResult(session.branchId, now, history, historyItems, updatedProducts, bought.map { it.productId })
        }
        if (result != null) {
            sync?.pushFinish(
                result.history, result.historyItems, result.updatedProducts,
                result.boughtProductIds, result.branchId, result.time
            )
        }
        onListChanged()
    }

    private class FinishResult(
        val branchId: Long,
        val time: Long,
        val history: ShoppingHistory?,
        val historyItems: List<ShoppingHistoryItem>,
        val updatedProducts: List<Product>,
        val boughtProductIds: List<Long>
    )

    // ---------------- suggestions ----------------
    suspend fun suggest(products: List<Product>, inList: Set<Long>, branchId: Long?): List<Suggestion> {
        val atBranch = if (branchId != null) dao.productIdsBoughtAtBranch(branchId).toSet() else emptySet()
        return ShoppingSuggestionEngine.suggest(products, inList, atBranch, System.currentTimeMillis())
    }

    // ---------------- export / import ----------------
    suspend fun exportJson(): String {
        val data = BackupData(
            products = dao.allProducts(),
            stores = dao.allStores(),
            branches = dao.allBranches(),
            shoppingItems = dao.allItems(),
            branchProductOrders = dao.allOrders(),
            shoppingSessions = dao.allSessions(),
            shoppingSessionItems = dao.allSessionItems(),
            shoppingHistory = dao.allHistory(),
            shoppingHistoryItems = dao.allHistoryItems(),
            themeMode = currentTheme()
        )
        return BackupJson.toJson(data)
    }

    /** Replaces all existing data. Returns false (and changes nothing) if the file is invalid. */
    suspend fun importJson(json: String): Boolean {
        if (sync?.isJoined() == true) return false // the household's data would overwrite the import
        val data = try {
            BackupJson.fromJson(json)
        } catch (e: Exception) {
            return false
        }
        db.withTransaction {
            dao.clearHistoryItems()
            dao.clearHistory()
            dao.clearSessionItems()
            dao.clearSessions()
            dao.clearOrders()
            dao.clearItems()
            dao.clearBranches()
            dao.clearStores()
            dao.clearProducts()
            dao.insertProductsAll(data.products)
            dao.insertStoresAll(data.stores)
            dao.insertBranchesAll(data.branches)
            dao.insertItemsAll(data.shoppingItems)
            dao.upsertOrders(data.branchProductOrders)
            dao.insertSessionsAll(data.shoppingSessions)
            dao.insertSessionItems(data.shoppingSessionItems)
            dao.insertHistoryAll(data.shoppingHistory)
            dao.insertHistoryItemsAll(data.shoppingHistoryItems)
        }
        setThemeMode(data.themeMode)
        onListChanged()
        return true
    }
}
