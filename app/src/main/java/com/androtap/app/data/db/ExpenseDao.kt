package com.androtap.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.androtap.app.data.model.Expense
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(expense: Expense): Long

    @Delete
    suspend fun delete(expense: Expense)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM expenses ORDER BY timestamp DESC")
    fun getAllExpenses(): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE timestamp >= :sinceTimestamp ORDER BY timestamp DESC")
    fun getExpensesSince(sinceTimestamp: Long): Flow<List<Expense>>

    @Query("SELECT SUM(amount) FROM expenses WHERE timestamp >= :sinceTimestamp")
    fun getTotalSpentSince(sinceTimestamp: Long): Flow<Double?>

    @Query("SELECT SUM(amount) FROM expenses")
    fun getAllTimeTotal(): Flow<Double?>

    @Query("DELETE FROM expenses")
    suspend fun clearAll()
}
