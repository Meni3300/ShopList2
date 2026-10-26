package com.shoplist.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ShopDao {

    // ---------------- products ----------------
    @Query("SELECT * FROM products ORDER BY name COLLATE NOCASE")
    fun observeProducts(): Flow<List<Product>>

    @Query("SELECT * FROM products")
    suspend fun allProducts(): List<Product>

    @Query("SELECT * FROM products WHERE normalizedName = :n LIMIT 1")
    suspend fun productByNormalized(n: String): Product?

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun productById(id: Long): Product?

    @Insert
    suspend fun insertProduct(p: Product): Long

    @Update
    suspend fun updateProduct(p: Product)

    @Query("DELETE FROM products WHERE id = :id")
    suspend fun deleteProduct(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProductsAll(list: List<Product>)

    @Query("DELETE FROM products")
    suspend fun clearProducts()

    // ---------------- current shopping list ----------------
    @Query(
        "SELECT i.id AS itemId, i.productId AS productId, p.name AS name, p.category AS category, " +
            "i.quantity AS quantity, i.createdAt AS createdAt " +
            "FROM shopping_items i JOIN products p ON p.id = i.productId ORDER BY i.createdAt, i.id"
    )
    fun observeList(): Flow<List<ListEntry>>

    @Query(
        "SELECT i.id AS itemId, i.productId AS productId, p.name AS name, p.category AS category, " +
            "i.quantity AS quantity, i.createdAt AS createdAt " +
            "FROM shopping_items i JOIN products p ON p.id = i.productId ORDER BY i.createdAt, i.id"
    )
    suspend fun listSnapshot(): List<ListEntry>

    @Query("SELECT * FROM shopping_items WHERE productId = :pid LIMIT 1")
    suspend fun itemByProduct(pid: Long): ShoppingItem?

    @Insert
    suspend fun insertItem(i: ShoppingItem): Long

    @Query("SELECT * FROM shopping_items WHERE id = :id")
    suspend fun itemById(id: Long): ShoppingItem?

    @Query("UPDATE shopping_items SET quantity = :q WHERE id = :id")
    suspend fun setItemQuantity(id: Long, q: Int)

    @Query("DELETE FROM shopping_items WHERE id = :id")
    suspend fun deleteItem(id: Long)

    @Query("DELETE FROM shopping_items WHERE productId = :pid")
    suspend fun deleteItemsOfProduct(pid: Long)

    @Query("SELECT * FROM shopping_items")
    suspend fun allItems(): List<ShoppingItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItemsAll(list: List<ShoppingItem>)

    @Query("DELETE FROM shopping_items")
    suspend fun clearItems()

    // ---------------- stores & branches ----------------
    @Query(
        "SELECT s.id AS id, s.name AS name, " +
            "(SELECT COUNT(*) FROM branches b WHERE b.storeId = s.id) AS branchCount " +
            "FROM stores s ORDER BY s.name COLLATE NOCASE"
    )
    fun observeStores(): Flow<List<StoreRow>>

    @Query("SELECT * FROM stores")
    suspend fun allStores(): List<Store>

    @Insert
    suspend fun insertStore(s: Store): Long

    @Query("DELETE FROM stores WHERE id = :id")
    suspend fun deleteStore(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStoresAll(list: List<Store>)

    @Query("DELETE FROM stores")
    suspend fun clearStores()

    @Query("SELECT * FROM branches WHERE storeId = :sid ORDER BY name COLLATE NOCASE")
    fun observeBranches(sid: Long): Flow<List<Branch>>

    @Query("SELECT * FROM branches WHERE storeId = :sid")
    suspend fun branchesOfStore(sid: Long): List<Branch>

    @Query("SELECT * FROM branches")
    suspend fun allBranches(): List<Branch>

    @Insert
    suspend fun insertBranch(b: Branch): Long

    @Query("DELETE FROM branches WHERE id = :id")
    suspend fun deleteBranch(id: Long)

    @Query("UPDATE branches SET lastUsedAt = :t WHERE id = :id")
    suspend fun setBranchLastUsed(id: Long, t: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBranchesAll(list: List<Branch>)

    @Query("DELETE FROM branches")
    suspend fun clearBranches()

    @Query(
        "SELECT b.id AS branchId, b.name AS branchName, s.id AS storeId, s.name AS storeName " +
            "FROM branches b JOIN stores s ON s.id = b.storeId WHERE b.id = :id"
    )
    fun observeBranchInfo(id: Long): Flow<BranchInfo?>

    @Query(
        "SELECT b.id AS branchId, b.name AS branchName, s.id AS storeId, s.name AS storeName " +
            "FROM branches b JOIN stores s ON s.id = b.storeId " +
            "WHERE b.lastUsedAt IS NOT NULL ORDER BY b.lastUsedAt DESC LIMIT 1"
    )
    fun observeLastBranch(): Flow<BranchInfo?>

    // ---------------- per-branch product order ----------------
    @Query("SELECT * FROM branch_product_orders WHERE branchId = :b")
    fun observeOrders(b: Long): Flow<List<BranchProductOrder>>

    @Query("SELECT * FROM branch_product_orders WHERE branchId = :b")
    suspend fun getOrders(b: Long): List<BranchProductOrder>

    @Query(
        "SELECT o.productId AS productId, p.name AS name, o.sortIndex AS sortIndex " +
            "FROM branch_product_orders o JOIN products p ON p.id = o.productId " +
            "WHERE o.branchId = :b ORDER BY o.sortIndex"
    )
    fun observeBranchOrder(b: Long): Flow<List<OrderEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOrders(list: List<BranchProductOrder>)

    @Query("DELETE FROM branch_product_orders WHERE branchId = :b")
    suspend fun deleteOrdersOfBranch(b: Long)

    @Query("DELETE FROM branch_product_orders WHERE productId = :pid")
    suspend fun deleteOrdersOfProduct(pid: Long)

    @Query("SELECT * FROM branch_product_orders")
    suspend fun allOrders(): List<BranchProductOrder>

    @Query("DELETE FROM branch_product_orders")
    suspend fun clearOrders()

    // ---------------- sessions ----------------
    @Query("SELECT * FROM shopping_sessions WHERE isActive = 1 LIMIT 1")
    suspend fun activeSession(): ShoppingSession?

    @Query("SELECT * FROM shopping_sessions WHERE id = :id")
    suspend fun sessionById(id: Long): ShoppingSession?

    @Query(
        "SELECT ss.id AS id, ss.storeId AS storeId, ss.branchId AS branchId, " +
            "s.name AS storeName, b.name AS branchName " +
            "FROM shopping_sessions ss JOIN stores s ON s.id = ss.storeId " +
            "JOIN branches b ON b.id = ss.branchId WHERE ss.isActive = 1 LIMIT 1"
    )
    fun observeActiveSession(): Flow<SessionInfo?>

    @Insert
    suspend fun insertSession(s: ShoppingSession): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessionsAll(list: List<ShoppingSession>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessionItems(list: List<ShoppingSessionItem>)

    @Query(
        "SELECT si.id AS id, si.productId AS productId, p.name AS name, p.category AS category, " +
            "si.quantity AS quantity, si.isCompleted AS isCompleted " +
            "FROM shopping_session_items si JOIN products p ON p.id = si.productId " +
            "WHERE si.sessionId = :sid ORDER BY si.id"
    )
    fun observeSessionItems(sid: Long): Flow<List<SessionEntry>>

    @Query("SELECT * FROM shopping_session_items WHERE sessionId = :sid")
    suspend fun sessionItemsRaw(sid: Long): List<ShoppingSessionItem>

    @Query(
        "UPDATE shopping_session_items SET isCompleted = 1 - isCompleted, " +
            "completedAt = CASE WHEN isCompleted = 0 THEN :now ELSE NULL END WHERE id = :id"
    )
    suspend fun toggleSessionItem(id: Long, now: Long)

    @Query("UPDATE shopping_sessions SET isActive = 0 WHERE id = :id")
    suspend fun deactivateSession(id: Long)

    @Query("UPDATE shopping_sessions SET isActive = 0 WHERE branchId = :b")
    suspend fun deactivateSessionsOfBranch(b: Long)

    @Query("UPDATE shopping_sessions SET isActive = 0")
    suspend fun deactivateAllSessions()

    @Query(
        "UPDATE shopping_sessions SET isActive = 0 WHERE isActive = 1 AND " +
            "(branchId NOT IN (SELECT id FROM branches) OR storeId NOT IN (SELECT id FROM stores))"
    )
    suspend fun deactivateOrphanSessions()

    @Query("SELECT * FROM shopping_sessions")
    suspend fun allSessions(): List<ShoppingSession>

    @Query("SELECT * FROM shopping_session_items")
    suspend fun allSessionItems(): List<ShoppingSessionItem>

    @Query("DELETE FROM shopping_sessions")
    suspend fun clearSessions()

    @Query("DELETE FROM shopping_session_items")
    suspend fun clearSessionItems()

    // ---------------- history ----------------
    @Insert
    suspend fun insertHistory(h: ShoppingHistory): Long

    @Insert
    suspend fun insertHistoryItems(list: List<ShoppingHistoryItem>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistoryAll(list: List<ShoppingHistory>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistoryItemsAll(list: List<ShoppingHistoryItem>)

    @Query(
        "SELECT h.id AS id, h.createdAt AS createdAt, h.itemCount AS itemCount, " +
            "COALESCE(s.name, '?') AS storeName, COALESCE(b.name, '?') AS branchName " +
            "FROM shopping_history h LEFT JOIN stores s ON s.id = h.storeId " +
            "LEFT JOIN branches b ON b.id = h.branchId ORDER BY h.createdAt DESC"
    )
    fun observeHistory(): Flow<List<HistoryRow>>

    @Query("SELECT * FROM shopping_history_items WHERE historyId = :id")
    fun observeHistoryItems(id: Long): Flow<List<ShoppingHistoryItem>>

    @Query(
        "SELECT DISTINCT hi.productId FROM shopping_history_items hi " +
            "JOIN shopping_history h ON h.id = hi.historyId WHERE h.branchId = :b"
    )
    suspend fun productIdsBoughtAtBranch(b: Long): List<Long>

    @Query("SELECT * FROM shopping_history")
    suspend fun allHistory(): List<ShoppingHistory>

    @Query("SELECT * FROM shopping_history_items")
    suspend fun allHistoryItems(): List<ShoppingHistoryItem>

    @Query("DELETE FROM shopping_history")
    suspend fun clearHistory()

    @Query("DELETE FROM shopping_history_items")
    suspend fun clearHistoryItems()
}
