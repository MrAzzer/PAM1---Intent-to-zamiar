package com.example.nanyabusiness.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Credential::class, SshUser::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun credentialDao(): CredentialDao
    abstract fun sshUserDao(): SshUserDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "credential_database_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
