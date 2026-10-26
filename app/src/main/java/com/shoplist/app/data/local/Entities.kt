package com.shoplist.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "products", indices = [Index(value = ["normalizedName"], unique = true)])
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val normalizedName: String,
    val category: String = "אחר",
    val createdAt: Long,
    val purchaseCount: Int = 0,
    val lastPurchasedAt: Long? = null,
    val averagePurchaseInterval: Double = 0.0
)

@Entity(tableName = "stores", indices = [Index(value = ["name"], unique = true)])
data class Store(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long
)

@Entity(tableName = "branches", indices = [Index(value = ["storeId", "name"], unique = true)])
data class Branch(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val storeId: Long,
    val name: String,
    val createdAt: Long,
    val lastUsedAt: Long? = null
)

@Entity(tableName = "shopping_items", indices = [Index(value = ["productId"], unique = true)])
data class ShoppingItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val quantity: Int,
    val isCompleted: Boolean = false,
    val createdAt: Long
)

/** UNIQUE(branchId, productId) is guaranteed by the composite primary key. */
@Entity(tableName = "branch_product_orders", primaryKeys = ["branchId", "productId"])
data class BranchProductOrder(
    val branchId: Long,
    val productId: Long,
    val sortIndex: Int,
    val lastUpdated: Long
)

@Entity(tableName = "shopping_sessions")
data class ShoppingSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val storeId: Long,
    val branchId: Long,
    val startedAt: Long,
    val isActive: Boolean
)

@Entity(tableName = "shopping_session_items", indices = [Index("sessionId")])
data class ShoppingSessionItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val productId: Long,
    val quantity: Int,
    val isCompleted: Boolean = false,
    val completedAt: Long? = null
)

@Entity(tableName = "shopping_history")
data class ShoppingHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val storeId: Long,
    val branchId: Long,
    val createdAt: Long,
    val itemCount: Int
)

@Entity(tableName = "shopping_history_items", indices = [Index("historyId")])
data class ShoppingHistoryItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val historyId: Long,
    val productId: Long,
    val productNameSnapshot: String,
    val quantity: Int,
    val completedAt: Long
)

// ---------- Query result rows (not tables) ----------

data class ListEntry(
    val itemId: Long,
    val productId: Long,
    val name: String,
    val category: String,
    val quantity: Int,
    val createdAt: Long
)

data class SessionEntry(
    val id: Long,
    val productId: Long,
    val name: String,
    val category: String,
    val quantity: Int,
    val isCompleted: Boolean
)

data class StoreRow(val id: Long, val name: String, val branchCount: Int)

data class BranchInfo(
    val branchId: Long,
    val branchName: String,
    val storeId: Long,
    val storeName: String
)

data class SessionInfo(
    val id: Long,
    val storeId: Long,
    val branchId: Long,
    val storeName: String,
    val branchName: String
)

data class OrderEntry(val productId: Long, val name: String, val sortIndex: Int)

data class HistoryRow(
    val id: Long,
    val createdAt: Long,
    val itemCount: Int,
    val storeName: String,
    val branchName: String
)
