package com.shoplist.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shoplist.app.ShopListApp
import com.shoplist.app.data.local.Branch
import com.shoplist.app.data.local.BranchInfo
import com.shoplist.app.data.local.BranchProductOrder
import com.shoplist.app.data.local.HistoryRow
import com.shoplist.app.data.local.ListEntry
import com.shoplist.app.data.local.OrderEntry
import com.shoplist.app.data.local.Product
import com.shoplist.app.data.local.SessionEntry
import com.shoplist.app.data.local.SessionInfo
import com.shoplist.app.data.local.ShoppingHistoryItem
import com.shoplist.app.data.local.StoreRow
import com.shoplist.app.sorting.Suggestion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One shared ViewModel for the whole app; all state lives in Room, so nothing is lost on process death. */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ShopListApp.from(app).repo
    private val sync = ShopListApp.from(app).sync

    private fun <T> Flow<T>.state(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    val themeMode: StateFlow<String> = repo.themeMode.state("system")
    val list: StateFlow<List<ListEntry>> = repo.observeList().state(emptyList())
    val products: StateFlow<List<Product>> = repo.observeProducts().state(emptyList())
    val stores: StateFlow<List<StoreRow>> = repo.observeStores().state(emptyList())
    val activeSession: StateFlow<SessionInfo?> = repo.observeActiveSession().state(null)
    val lastBranch: StateFlow<BranchInfo?> = repo.observeLastBranch().state(null)
    val history: StateFlow<List<HistoryRow>> = repo.observeHistory().state(emptyList())

    val suggestions: StateFlow<List<Suggestion>> =
        combine(repo.observeProducts(), repo.observeList(), repo.observeLastBranch()) { p, l, b ->
            Triple(p, l, b)
        }.map { (p, l, b) ->
            repo.suggest(p, l.map { it.productId }.toSet(), b?.branchId)
        }.state(emptyList())

    // Store / branch chosen on the "קנייה" tab
    val selectedStoreId = MutableStateFlow<Long?>(null)
    val selectedBranchId = MutableStateFlow<Long?>(null)

    fun selectStore(storeId: Long) {
        selectedStoreId.value = storeId
        selectedBranchId.value = null
    }

    fun selectBranch(storeId: Long, branchId: Long) {
        selectedStoreId.value = storeId
        selectedBranchId.value = branchId
    }

    fun branches(storeId: Long): Flow<List<Branch>> = repo.observeBranches(storeId)
    fun orders(branchId: Long): Flow<List<BranchProductOrder>> = repo.observeOrders(branchId)
    fun branchOrder(branchId: Long): Flow<List<OrderEntry>> = repo.observeBranchOrder(branchId)
    fun branchInfo(branchId: Long): Flow<BranchInfo?> = repo.observeBranchInfo(branchId)
    fun sessionItems(sessionId: Long): Flow<List<SessionEntry>> = repo.observeSessionItems(sessionId)
    fun historyItems(id: Long): Flow<List<ShoppingHistoryItem>> = repo.observeHistoryItems(id)

    // ----- household sync -----
    val householdCode: StateFlow<String?> = sync.code
    val syncAvailable: Boolean get() = sync.available

    fun createHousehold(onResult: (String?) -> Unit) {
        viewModelScope.launch { onResult(sync.createHousehold()) }
    }
    fun joinHousehold(code: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(sync.joinHousehold(code)) }
    }
    fun leaveHousehold() { viewModelScope.launch { sync.leave() } }

    // ----- actions -----
    fun add(text: String) { viewModelScope.launch { repo.addFromText(text) } }
    fun addProduct(productId: Long) { viewModelScope.launch { repo.addProductById(productId) } }
    fun setQuantity(itemId: Long, q: Int) { viewModelScope.launch { repo.setQuantity(itemId, q) } }
    fun remove(itemId: Long) { viewModelScope.launch { repo.removeItem(itemId) } }

    fun updateProduct(id: Long, name: String, category: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(repo.updateProduct(id, name, category)) }
    }
    fun deleteProduct(id: Long) { viewModelScope.launch { repo.deleteProduct(id) } }

    fun createStore(name: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(repo.createStore(name)) }
    }
    fun createBranch(storeId: Long, name: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(repo.createBranch(storeId, name)) }
    }
    fun deleteStore(id: Long) { viewModelScope.launch { repo.deleteStore(id) } }
    fun deleteBranch(id: Long) { viewModelScope.launch { repo.deleteBranch(id) } }

    fun reorder(branchId: Long, productIds: List<Long>) {
        viewModelScope.launch { repo.updateBranchOrder(branchId, productIds) }
    }
    fun resetOrder(branchId: Long) { viewModelScope.launch { repo.resetBranchOrder(branchId) } }

    fun startSession(storeId: Long, branchId: Long, onStarted: () -> Unit = {}) {
        viewModelScope.launch {
            if (repo.startSession(storeId, branchId) > 0) onStarted()
        }
    }
    fun toggle(sessionItemId: Long) { viewModelScope.launch { repo.toggleSessionItem(sessionItemId) } }
    fun finishSession(id: Long) { viewModelScope.launch { repo.finishSession(id) } }
    fun cancelSession(id: Long) { viewModelScope.launch { repo.cancelSession(id) } }

    fun setTheme(mode: String) { viewModelScope.launch { repo.setThemeMode(mode) } }
    suspend fun exportJson(): String = repo.exportJson()
    suspend fun importJson(json: String): Boolean = repo.importJson(json)
}
