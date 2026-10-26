package com.shoplist.app.data

import com.shoplist.app.data.local.Branch
import com.shoplist.app.data.local.BranchProductOrder
import com.shoplist.app.data.local.Product
import com.shoplist.app.data.local.ShoppingHistory
import com.shoplist.app.data.local.ShoppingHistoryItem
import com.shoplist.app.data.local.ShoppingItem
import com.shoplist.app.data.local.ShoppingSession
import com.shoplist.app.data.local.ShoppingSessionItem
import com.shoplist.app.data.local.Store
import org.json.JSONArray
import org.json.JSONObject

data class BackupData(
    val products: List<Product>,
    val stores: List<Store>,
    val branches: List<Branch>,
    val shoppingItems: List<ShoppingItem>,
    val branchProductOrders: List<BranchProductOrder>,
    val shoppingSessions: List<ShoppingSession>,
    val shoppingSessionItems: List<ShoppingSessionItem>,
    val shoppingHistory: List<ShoppingHistory>,
    val shoppingHistoryItems: List<ShoppingHistoryItem>,
    val themeMode: String
)

object BackupJson {
    private fun JSONObject.longOrNull(k: String): Long? = if (has(k) && !isNull(k)) getLong(k) else null

    private fun JSONObject.arrayOrEmpty(k: String): JSONArray = optJSONArray(k) ?: JSONArray()

    private fun <T> JSONArray.mapObjects(f: (JSONObject) -> T): List<T> =
        (0 until length()).map { f(getJSONObject(it)) }

    private fun <T> toArray(list: List<T>, f: (T) -> JSONObject): JSONArray {
        val a = JSONArray()
        list.forEach { a.put(f(it)) }
        return a
    }

    fun toJson(d: BackupData): String {
        val o = JSONObject()
        o.put("app", "ShopList")
        o.put("version", 1)
        o.put("products", toArray(d.products) { p ->
            JSONObject().put("id", p.id).put("name", p.name).put("normalizedName", p.normalizedName)
                .put("category", p.category).put("createdAt", p.createdAt)
                .put("purchaseCount", p.purchaseCount).put("lastPurchasedAt", p.lastPurchasedAt)
                .put("averagePurchaseInterval", p.averagePurchaseInterval)
        })
        o.put("stores", toArray(d.stores) { s ->
            JSONObject().put("id", s.id).put("name", s.name).put("createdAt", s.createdAt)
        })
        o.put("branches", toArray(d.branches) { b ->
            JSONObject().put("id", b.id).put("storeId", b.storeId).put("name", b.name)
                .put("createdAt", b.createdAt).put("lastUsedAt", b.lastUsedAt)
        })
        o.put("shoppingItems", toArray(d.shoppingItems) { i ->
            JSONObject().put("id", i.id).put("productId", i.productId).put("quantity", i.quantity)
                .put("isCompleted", i.isCompleted).put("createdAt", i.createdAt)
        })
        o.put("branchProductOrders", toArray(d.branchProductOrders) { r ->
            JSONObject().put("branchId", r.branchId).put("productId", r.productId)
                .put("sortIndex", r.sortIndex).put("lastUpdated", r.lastUpdated)
        })
        o.put("shoppingSessions", toArray(d.shoppingSessions) { s ->
            JSONObject().put("id", s.id).put("storeId", s.storeId).put("branchId", s.branchId)
                .put("startedAt", s.startedAt).put("isActive", s.isActive)
        })
        o.put("shoppingSessionItems", toArray(d.shoppingSessionItems) { i ->
            JSONObject().put("id", i.id).put("sessionId", i.sessionId).put("productId", i.productId)
                .put("quantity", i.quantity).put("isCompleted", i.isCompleted)
                .put("completedAt", i.completedAt)
        })
        o.put("shoppingHistory", toArray(d.shoppingHistory) { h ->
            JSONObject().put("id", h.id).put("storeId", h.storeId).put("branchId", h.branchId)
                .put("createdAt", h.createdAt).put("itemCount", h.itemCount)
        })
        o.put("shoppingHistoryItems", toArray(d.shoppingHistoryItems) { h ->
            JSONObject().put("id", h.id).put("historyId", h.historyId).put("productId", h.productId)
                .put("productNameSnapshot", h.productNameSnapshot).put("quantity", h.quantity)
                .put("completedAt", h.completedAt)
        })
        o.put("settings", JSONObject().put("themeMode", d.themeMode))
        return o.toString(2)
    }

    /** Throws on malformed input; callers must catch. */
    fun fromJson(json: String): BackupData {
        val o = JSONObject(json)
        require(o.optString("app") == "ShopList") { "not a ShopList backup" }
        return BackupData(
            products = o.arrayOrEmpty("products").mapObjects {
                Product(
                    id = it.getLong("id"), name = it.getString("name"),
                    normalizedName = it.getString("normalizedName"), category = it.getString("category"),
                    createdAt = it.getLong("createdAt"), purchaseCount = it.getInt("purchaseCount"),
                    lastPurchasedAt = it.longOrNull("lastPurchasedAt"),
                    averagePurchaseInterval = it.getDouble("averagePurchaseInterval")
                )
            },
            stores = o.arrayOrEmpty("stores").mapObjects {
                Store(it.getLong("id"), it.getString("name"), it.getLong("createdAt"))
            },
            branches = o.arrayOrEmpty("branches").mapObjects {
                Branch(
                    it.getLong("id"), it.getLong("storeId"), it.getString("name"),
                    it.getLong("createdAt"), it.longOrNull("lastUsedAt")
                )
            },
            shoppingItems = o.arrayOrEmpty("shoppingItems").mapObjects {
                ShoppingItem(
                    it.getLong("id"), it.getLong("productId"), it.getInt("quantity"),
                    it.getBoolean("isCompleted"), it.getLong("createdAt")
                )
            },
            branchProductOrders = o.arrayOrEmpty("branchProductOrders").mapObjects {
                BranchProductOrder(
                    it.getLong("branchId"), it.getLong("productId"),
                    it.getInt("sortIndex"), it.getLong("lastUpdated")
                )
            },
            shoppingSessions = o.arrayOrEmpty("shoppingSessions").mapObjects {
                ShoppingSession(
                    it.getLong("id"), it.getLong("storeId"), it.getLong("branchId"),
                    it.getLong("startedAt"), it.getBoolean("isActive")
                )
            },
            shoppingSessionItems = o.arrayOrEmpty("shoppingSessionItems").mapObjects {
                ShoppingSessionItem(
                    it.getLong("id"), it.getLong("sessionId"), it.getLong("productId"),
                    it.getInt("quantity"), it.getBoolean("isCompleted"), it.longOrNull("completedAt")
                )
            },
            shoppingHistory = o.arrayOrEmpty("shoppingHistory").mapObjects {
                ShoppingHistory(
                    it.getLong("id"), it.getLong("storeId"), it.getLong("branchId"),
                    it.getLong("createdAt"), it.getInt("itemCount")
                )
            },
            shoppingHistoryItems = o.arrayOrEmpty("shoppingHistoryItems").mapObjects {
                ShoppingHistoryItem(
                    it.getLong("id"), it.getLong("historyId"), it.getLong("productId"),
                    it.getString("productNameSnapshot"), it.getInt("quantity"), it.getLong("completedAt")
                )
            },
            themeMode = o.optJSONObject("settings")?.optString("themeMode", "system") ?: "system"
        )
    }
}
