package de.foodiary.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {

    @Query("SELECT * FROM entries ORDER BY recordedAt DESC")
    fun observeAll(): Flow<List<Entry>>

    @Query("SELECT * FROM entries ORDER BY recordedAt ASC")
    suspend fun allAscending(): List<Entry>

    @Insert
    suspend fun insert(entry: Entry): Long

    @Update
    suspend fun update(entry: Entry)

    @Delete
    suspend fun delete(entry: Entry)

    @Query("DELETE FROM entries")
    suspend fun deleteAll()
}
