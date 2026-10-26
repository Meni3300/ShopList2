package com.shoplist.app

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.shoplist.app.data.ShopRepository
import com.shoplist.app.data.local.AppDatabase
import com.shoplist.app.sync.HouseholdSync
import com.shoplist.app.widgets.ListWidgetProvider

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class ShopListApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.build(this) }

    val sync: HouseholdSync by lazy {
        HouseholdSync(this, database, settingsStore) { ListWidgetProvider.requestUpdate(this@ShopListApp) }
    }

    val repo: ShopRepository by lazy {
        ShopRepository(database, settingsStore, sync) { ListWidgetProvider.requestUpdate(this@ShopListApp) }
    }

    override fun onCreate() {
        super.onCreate()
        sync.start()
    }

    companion object {
        fun from(context: Context): ShopListApp = context.applicationContext as ShopListApp
    }
}
