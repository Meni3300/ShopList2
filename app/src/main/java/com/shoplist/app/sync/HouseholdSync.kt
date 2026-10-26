package com.shoplist.app.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.withTransaction
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.shoplist.app.data.BackupData
import com.shoplist.app.data.local.AppDatabase
import com.shoplist.app.data.local.Branch
import com.shoplist.app.data.local.BranchProductOrder
import com.shoplist.app.data.local.Product
import com.shoplist.app.data.local.ShoppingHistory
import com.shoplist.app.data.local.ShoppingHistoryItem
import com.shoplist.app.data.local.ShoppingItem
import com.shoplist.app.data.local.Store
import com.shoplist.app.utils.TextNormalizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.security.SecureRandom

/**
 * Shares the data between phones that know the same household code.
 *
 * - Local writes: the repository changes Room as before and then mirrors the same change to
 *   Firestore through the small fire-and-forget functions below (they never block, so the app
 *   keeps working offline and Firestore sends the change when the network returns).
 * - Remote changes: Firestore snapshot listeners replace the matching Room tables.
 *
 * Document ids are numbers derived from names (see KeyIds), so two phones that add "חלב"
 * independently end up with the very same document.
 */
class HouseholdSync(
    private val context: Context,
    private val db: AppDatabase,
    private val dataStore: DataStore<Preferences>,
    private val onRemoteApplied: () -> Unit
) {
    private val dao = db.dao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val codeKey = stringPreferencesKey("household_code")
    private val listeners = mutableListOf<ListenerRegistration>()

    val code = MutableStateFlow<String?>(null)

    /** False when the app was built without google-services.json. */
    val available: Boolean get() = FirebaseApp.getApps(context).isNotEmpty()

    fun isJoined(): Boolean = code.value != null

    private fun firestore(): FirebaseFirestore? = if (available) FirebaseFirestore.getInstance() else null

    private fun root(): DocumentReference? {
        val c = code.value ?: return null
        return firestore()?.collection("households")?.document(c)
    }

    /** Called once when the app starts. */
    fun start() {
        scope.launch {
            val saved = dataStore.data.first()[codeKey]
            if (saved != null && available) {
                code.value = saved
                listen(saved)
            }
        }
    }

    // ------------------------------------------------------------------
    // create / join / leave
    // ------------------------------------------------------------------
    suspend fun createHousehold(): String? {
        val fs = firestore() ?: return null
        val newCode = generateCode()
        val ok = try {
            withTimeout(30_000) {
                val r = fs.collection("households").document(newCode)
                r.set(mapOf("createdAt" to System.currentTimeMillis())).await()
                uploadLocal(r)
            }
            true
        } catch (e: Exception) {
            false
        }
        if (!ok) return null
        dao.deactivateAllSessions()
        saveCode(newCode)
        listen(newCode)
        return newCode
    }

    /** Local data of this phone is replaced by the household's data. */
    suspend fun joinHousehold(raw: String): Boolean {
        val fs = firestore() ?: return false
        val c = cleanCode(raw)
        if (c.length < 6) return false
        val exists = try {
            withTimeout(20_000) {
                fs.collection("households").document(c).get(Source.SERVER).await().exists()
            }
        } catch (e: Exception) {
            false
        }
        if (!exists) return false
        dao.deactivateAllSessions()
        saveCode(c)
        listen(c)
        return true
    }

    suspend fun leave() {
        stopListening()
        code.value = null
        dataStore.edit { it.remove(codeKey) }
    }

    private suspend fun saveCode(c: String) {
        dataStore.edit { it[codeKey] = c }
        code.value = c
    }

    private suspend fun uploadLocal(r: DocumentReference) {
        val data = BackupData(
            products = dao.allProducts(),
            stores = dao.allStores(),
            branches = dao.allBranches(),
            shoppingItems = dao.allItems(),
            branchProductOrders = dao.allOrders(),
            shoppingSessions = emptyList(),
            shoppingSessionItems = emptyList(),
            shoppingHistory = dao.allHistory(),
            shoppingHistoryItems = dao.allHistoryItems(),
            themeMode = "system"
        )
        val docs = HouseholdDocBuilder.build(data)
        for (chunk in docs.chunked(400)) {
            val batch = r.firestore.batch()
            for (d in chunk) batch.set(r.collection(d.collection).document(d.id), d.fields)
            batch.commit().await()
        }
    }

    private fun generateCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val rnd = SecureRandom()
        return (1..10).map { alphabet[rnd.nextInt(alphabet.length)] }.joinToString("")
    }

    fun cleanCode(raw: String): String =
        raw.uppercase().filter { it.isLetterOrDigit() }

    // ------------------------------------------------------------------
    // local change -> Firestore (fire and forget)
    // ------------------------------------------------------------------
    fun addToList(p: Product, item: ShoppingItem, added: Int) {
        val r = root() ?: return
        val b = r.firestore.batch()
        b.set(r.collection("products").document(p.id.toString()), HouseholdDocBuilder.productFields(p), SetOptions.merge())
        b.set(
            r.collection("items").document(item.productId.toString()),
            mapOf(
                "productId" to item.productId,
                "createdAt" to item.createdAt,
                "quantity" to FieldValue.increment(added.toLong())
            ),
            SetOptions.merge()
        )
        b.commit()
    }

    fun putItem(item: ShoppingItem) {
        val r = root() ?: return
        r.collection("items").document(item.productId.toString()).set(
            mapOf("productId" to item.productId, "createdAt" to item.createdAt, "quantity" to item.quantity)
        )
    }

    fun setItemQuantity(productId: Long, quantity: Int) {
        val r = root() ?: return
        r.collection("items").document(productId.toString())
            .set(mapOf("quantity" to quantity), SetOptions.merge())
    }

    fun deleteItem(productId: Long) {
        val r = root() ?: return
        r.collection("items").document(productId.toString()).delete()
    }

    fun upsertProduct(p: Product) {
        val r = root() ?: return
        r.collection("products").document(p.id.toString())
            .set(HouseholdDocBuilder.productFields(p), SetOptions.merge())
    }

    fun deleteProduct(productId: Long, orderBranchIds: List<Long>) {
        val r = root() ?: return
        val b = r.firestore.batch()
        b.delete(r.collection("products").document(productId.toString()))
        b.delete(r.collection("items").document(productId.toString()))
        for (branchId in orderBranchIds) {
            b.set(
                r.collection("orders").document(branchId.toString()),
                mapOf("productIds" to FieldValue.arrayRemove(productId)),
                SetOptions.merge()
            )
        }
        b.commit()
    }

    fun upsertStore(s: Store) {
        val r = root() ?: return
        r.collection("stores").document(s.id.toString()).set(HouseholdDocBuilder.storeFields(s), SetOptions.merge())
    }

    fun upsertBranch(br: Branch) {
        val r = root() ?: return
        r.collection("branches").document(br.id.toString())
            .set(HouseholdDocBuilder.branchFields(br), SetOptions.merge())
    }

    fun deleteBranch(branchId: Long) {
        val r = root() ?: return
        val b = r.firestore.batch()
        b.delete(r.collection("branches").document(branchId.toString()))
        b.delete(r.collection("orders").document(branchId.toString()))
        b.commit()
    }

    fun deleteStore(storeId: Long, branchIds: List<Long>) {
        val r = root() ?: return
        val b = r.firestore.batch()
        b.delete(r.collection("stores").document(storeId.toString()))
        for (id in branchIds) {
            b.delete(r.collection("branches").document(id.toString()))
            b.delete(r.collection("orders").document(id.toString()))
        }
        b.commit()
    }

    fun setOrder(branchId: Long, productIds: List<Long>) {
        val r = root() ?: return
        r.collection("orders").document(branchId.toString()).set(mapOf("productIds" to productIds))
    }

    fun deleteOrder(branchId: Long) {
        val r = root() ?: return
        r.collection("orders").document(branchId.toString()).delete()
    }

    fun setBranchLastUsed(branchId: Long, time: Long) {
        val r = root() ?: return
        r.collection("branches").document(branchId.toString())
            .set(mapOf("lastUsedAt" to time), SetOptions.merge())
    }

    fun pushFinish(
        history: ShoppingHistory?,
        items: List<ShoppingHistoryItem>,
        updatedProducts: List<Product>,
        boughtProductIds: List<Long>,
        branchId: Long,
        time: Long
    ) {
        val r = root() ?: return
        val b = r.firestore.batch()
        if (history != null) {
            b.set(
                r.collection("history").document(history.id.toString()),
                mapOf(
                    "storeId" to history.storeId,
                    "branchId" to history.branchId,
                    "createdAt" to history.createdAt,
                    "itemCount" to history.itemCount,
                    "items" to items.map {
                        mapOf(
                            "productId" to it.productId,
                            "name" to it.productNameSnapshot,
                            "quantity" to it.quantity,
                            "completedAt" to it.completedAt
                        )
                    }
                )
            )
        }
        for (p in updatedProducts) {
            b.set(
                r.collection("products").document(p.id.toString()),
                HouseholdDocBuilder.productFields(p),
                SetOptions.merge()
            )
        }
        for (id in boughtProductIds) b.delete(r.collection("items").document(id.toString()))
        b.set(
            r.collection("branches").document(branchId.toString()),
            mapOf("lastUsedAt" to time),
            SetOptions.merge()
        )
        b.commit()
    }

    // ------------------------------------------------------------------
    // Firestore -> Room
    // ------------------------------------------------------------------
    private fun stopListening() {
        listeners.forEach { it.remove() }
        listeners.clear()
    }

    private fun listen(c: String) {
        stopListening()
        val fs = firestore() ?: return
        val r = fs.collection("households").document(c)
        watch(r, "products") { docs -> applyProducts(docs) }
        watch(r, "stores") { docs -> applyStores(docs) }
        watch(r, "branches") { docs -> applyBranches(docs) }
        watch(r, "items") { docs -> applyItems(docs) }
        watch(r, "orders") { docs -> applyOrders(docs) }
        watch(r, "history") { docs -> applyHistory(docs) }
    }

    private fun watch(r: DocumentReference, name: String, apply: suspend (List<DocumentSnapshot>) -> Unit) {
        val reg = r.collection(name).addSnapshotListener { snap, err ->
            if (err != null || snap == null) return@addSnapshotListener
            // An empty answer from the local cache only means "nothing downloaded yet".
            if (snap.metadata.isFromCache && snap.isEmpty) return@addSnapshotListener
            val docs = snap.documents
            scope.launch {
                mutex.withLock {
                    try {
                        apply(docs)
                    } catch (e: Exception) {
                        // ignore malformed remote data
                    }
                }
                onRemoteApplied()
            }
        }
        listeners.add(reg)
    }

    private suspend fun applyProducts(docs: List<DocumentSnapshot>) {
        val list = docs.mapNotNull { d ->
            val id = d.id.toLongOrNull() ?: return@mapNotNull null
            val name = d.getString("name") ?: return@mapNotNull null
            Product(
                id = id,
                name = name,
                normalizedName = d.getString("normalizedName") ?: TextNormalizer.normalize(name),
                category = d.getString("category") ?: "אחר",
                createdAt = d.getLong("createdAt") ?: 0L,
                purchaseCount = (d.getLong("purchaseCount") ?: 0L).toInt(),
                lastPurchasedAt = d.getLong("lastPurchasedAt"),
                averagePurchaseInterval = d.getDouble("averagePurchaseInterval") ?: 0.0
            )
        }
        db.withTransaction {
            dao.clearProducts()
            dao.insertProductsAll(list)
        }
    }

    private suspend fun applyStores(docs: List<DocumentSnapshot>) {
        val list = docs.mapNotNull { d ->
            val id = d.id.toLongOrNull() ?: return@mapNotNull null
            Store(id, d.getString("name") ?: return@mapNotNull null, d.getLong("createdAt") ?: 0L)
        }
        db.withTransaction {
            dao.clearStores()
            dao.insertStoresAll(list)
            dao.deactivateOrphanSessions()
        }
    }

    private suspend fun applyBranches(docs: List<DocumentSnapshot>) {
        val list = docs.mapNotNull { d ->
            val id = d.id.toLongOrNull() ?: return@mapNotNull null
            Branch(
                id = id,
                storeId = d.getLong("storeId") ?: return@mapNotNull null,
                name = d.getString("name") ?: return@mapNotNull null,
                createdAt = d.getLong("createdAt") ?: 0L,
                lastUsedAt = d.getLong("lastUsedAt")
            )
        }
        db.withTransaction {
            dao.clearBranches()
            dao.insertBranchesAll(list)
            dao.deactivateOrphanSessions()
        }
    }

    private suspend fun applyItems(docs: List<DocumentSnapshot>) {
        val list = docs.mapNotNull { d ->
            val id = d.id.toLongOrNull() ?: return@mapNotNull null
            ShoppingItem(
                id = id,
                productId = d.getLong("productId") ?: id,
                quantity = (d.getLong("quantity") ?: 1L).toInt().coerceAtLeast(1),
                isCompleted = false,
                createdAt = d.getLong("createdAt") ?: 0L
            )
        }
        db.withTransaction {
            dao.clearItems()
            dao.insertItemsAll(list)
        }
    }

    private suspend fun applyOrders(docs: List<DocumentSnapshot>) {
        val now = System.currentTimeMillis()
        val rows = ArrayList<BranchProductOrder>()
        for (d in docs) {
            val branchId = d.id.toLongOrNull() ?: continue
            val ids = (d.get("productIds") as? List<*>)?.mapNotNull { (it as? Number)?.toLong() } ?: continue
            ids.forEachIndexed { i, pid -> rows.add(BranchProductOrder(branchId, pid, i, now)) }
        }
        db.withTransaction {
            dao.clearOrders()
            dao.upsertOrders(rows)
        }
    }

    private suspend fun applyHistory(docs: List<DocumentSnapshot>) {
        val histories = ArrayList<ShoppingHistory>()
        val items = ArrayList<ShoppingHistoryItem>()
        for (d in docs) {
            val id = d.id.toLongOrNull() ?: continue
            val createdAt = d.getLong("createdAt") ?: 0L
            histories.add(
                ShoppingHistory(
                    id = id,
                    storeId = d.getLong("storeId") ?: 0L,
                    branchId = d.getLong("branchId") ?: 0L,
                    createdAt = createdAt,
                    itemCount = (d.getLong("itemCount") ?: 0L).toInt()
                )
            )
            val raw = d.get("items") as? List<*> ?: continue
            for (m in raw) {
                val map = m as? Map<*, *> ?: continue
                items.add(
                    ShoppingHistoryItem(
                        historyId = id,
                        productId = (map["productId"] as? Number)?.toLong() ?: 0L,
                        productNameSnapshot = map["name"] as? String ?: "?",
                        quantity = (map["quantity"] as? Number)?.toInt() ?: 1,
                        completedAt = (map["completedAt"] as? Number)?.toLong() ?: createdAt
                    )
                )
            }
        }
        db.withTransaction {
            dao.clearHistoryItems()
            dao.clearHistory()
            dao.insertHistoryAll(histories)
            dao.insertHistoryItemsAll(items)
        }
    }
}
