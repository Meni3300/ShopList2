package com.shoplist.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        Product::class,
        Store::class,
        Branch::class,
        ShoppingItem::class,
        BranchProductOrder::class,
        ShoppingSession::class,
        ShoppingSessionItem::class,
        ShoppingHistory::class,
        ShoppingHistoryItem::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): ShopDao

    companion object {
        // Version 1: no migrations yet. Future schema changes must add Migration objects here
        // (never use destructive fallback - it would wipe the user's data).
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "shoplist.db")
                .build()
    }
}
