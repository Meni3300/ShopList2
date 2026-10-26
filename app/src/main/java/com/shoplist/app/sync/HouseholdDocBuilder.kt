package com.shoplist.app.sync

import com.shoplist.app.data.BackupData
import com.shoplist.app.data.local.Branch
import com.shoplist.app.data.local.Product
import com.shoplist.app.data.local.Store
import com.shoplist.app.utils.KeyIds
import com.shoplist.app.utils.TextNormalizer

data class RemoteDoc(val collection: String, val id: String, val fields: Map<String, Any?>)

/** Pure functions that turn local data into Firestore documents (unit-tested). */
object HouseholdDocBuilder {

    fun productFields(p: Product): Map<String, Any?> = mapOf(
        "name" to p.name,
        "normalizedName" to p.normalizedName,
        "category" to p.category,
        "createdAt" to p.createdAt,
        "purchaseCount" to p.purchaseCount,
        "lastPurchasedAt" to p.lastPurchasedAt,
        "averagePurchaseInterval" to p.averagePurchaseInterval
    )

    fun storeFields(s: Store): Map<String, Any?> = mapOf("name" to s.name, "createdAt" to s.createdAt)

    fun branchFields(b: Branch): Map<String, Any?> = mapOf(
        "storeId" to b.storeId,
        "name" to b.name,
        "createdAt" to b.createdAt,
        "lastUsedAt" to b.lastUsedAt
    )

    /**
     * Local rows created before joining a household have arbitrary auto-increment ids.
     * Documents always use ids derived from names, so all ids are translated here.
     */
    fun build(d: BackupData): List<RemoteDoc> {
        val out = ArrayList<RemoteDoc>()

        val productIds = HashMap<Long, Long>()
        for (p in d.products) {
            val nid = KeyIds.hash(p.normalizedName)
            productIds[p.id] = nid
            out.add(RemoteDoc("products", nid.toString(), productFields(p)))
        }

        val storeIds = HashMap<Long, Long>()
        for (s in d.stores) {
            val nid = KeyIds.hash(TextNormalizer.normalize(s.name))
            storeIds[s.id] = nid
            out.add(RemoteDoc("stores", nid.toString(), storeFields(s)))
        }

        val branchIds = HashMap<Long, Long>()
        for (b in d.branches) {
            val newStore = storeIds[b.storeId] ?: continue
            val nid = KeyIds.branchId(newStore, TextNormalizer.normalize(b.name))
            branchIds[b.id] = nid
            out.add(RemoteDoc("branches", nid.toString(), branchFields(b.copy(storeId = newStore))))
        }

        for (i in d.shoppingItems) {
            val pid = productIds[i.productId] ?: continue
            out.add(
                RemoteDoc(
                    "items", pid.toString(),
                    mapOf("productId" to pid, "createdAt" to i.createdAt, "quantity" to i.quantity)
                )
            )
        }

        for ((branchId, rows) in d.branchProductOrders.groupBy { it.branchId }) {
            val nb = branchIds[branchId] ?: continue
            val ids = rows.sortedBy { it.sortIndex }.mapNotNull { productIds[it.productId] }
            out.add(RemoteDoc("orders", nb.toString(), mapOf("productIds" to ids)))
        }

        for (h in d.shoppingHistory) {
            val nb = branchIds[h.branchId] ?: continue
            val ns = storeIds[h.storeId] ?: continue
            val hid = KeyIds.hash("hist|${h.createdAt}|$nb")
            val items = d.shoppingHistoryItems.filter { it.historyId == h.id }.map {
                mapOf(
                    "productId" to (productIds[it.productId]
                        ?: KeyIds.hash(TextNormalizer.normalize(it.productNameSnapshot))),
                    "name" to it.productNameSnapshot,
                    "quantity" to it.quantity,
                    "completedAt" to it.completedAt
                )
            }
            out.add(
                RemoteDoc(
                    "history", hid.toString(),
                    mapOf(
                        "storeId" to ns, "branchId" to nb, "createdAt" to h.createdAt,
                        "itemCount" to h.itemCount, "items" to items
                    )
                )
            )
        }
        return out
    }
}
