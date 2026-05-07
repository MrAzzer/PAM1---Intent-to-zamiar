package com.example.nanyabusiness.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CredentialDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCredential(credential: Credential)

    @Update
    suspend fun updateCredential(credential: Credential)

    @Delete
    suspend fun deleteCredential(credential: Credential)

    @Query("SELECT * FROM credentials ORDER BY id DESC")
    fun getAllCredentialsStream(): Flow<List<Credential>>

    @Query("SELECT * FROM credentials WHERE id = :id LIMIT 1")
    suspend fun getCredentialById(id: Int): Credential?
}
