package pt.estante.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query(
        """
        SELECT b.*,
            (SELECT COUNT(*) FROM photos p WHERE p.bookId = b.id) AS photoCount,
            (SELECT COUNT(*) FROM photos p WHERE p.bookId = b.id AND p.uploaded = 1) AS uploadedCount
        FROM books b
        ORDER BY b.createdAt DESC
        """,
    )
    fun observeSummaries(): Flow<List<BookSummary>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun get(id: String): BookEntity?

    @Query("SELECT * FROM books WHERE id = :id")
    fun observe(id: String): Flow<BookEntity?>

    @Insert
    suspend fun insert(book: BookEntity)

    @Query("UPDATE books SET syncState = :state, lastError = :error WHERE id = :id")
    suspend fun setSyncState(id: String, state: String, error: String?)

    @Query("UPDATE books SET folderUrl = :url WHERE id = :id")
    suspend fun setFolderUrl(id: String, url: String)
}

@Dao
interface PhotoDao {
    @Query("SELECT * FROM photos WHERE bookId = :bookId ORDER BY takenAt")
    fun observeForBook(bookId: String): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE bookId = :bookId ORDER BY takenAt")
    suspend fun forBook(bookId: String): List<PhotoEntity>

    @Query("SELECT DISTINCT bookId FROM photos WHERE uploaded = 0")
    suspend fun bookIdsWithPendingPhotos(): List<String>

    @Insert
    suspend fun insert(photo: PhotoEntity)

    @Delete
    suspend fun delete(photo: PhotoEntity)

    @Query("UPDATE photos SET uploaded = 1, fileName = :fileName, driveUrl = :driveUrl WHERE id = :id")
    suspend fun markUploaded(id: String, fileName: String, driveUrl: String)
}
