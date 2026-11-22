package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        RecitationSessionEntity::class,
        RecitationErrorEntity::class,
        AyahReviewScheduleEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class TasmeeDatabase : RoomDatabase() {
    abstract fun tasmeeDao(): TasmeeDao

    companion object {
        @Volatile
        private var INSTANCE: TasmeeDatabase? = null

        fun getInstance(context: Context): TasmeeDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    TasmeeDatabase::class.java,
                    "tasmee_qaloon_local.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
