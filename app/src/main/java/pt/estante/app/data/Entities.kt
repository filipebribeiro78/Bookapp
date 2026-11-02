package pt.estante.app.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class SyncState { LOCAL, QUEUED, SYNCING, SYNCED, ERROR }

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    /** A [pt.estante.core.Condition] name. */
    val condition: String,
    val createdAt: Long,
    /** A [SyncState] name. */
    val syncState: String = SyncState.LOCAL.name,
    val lastError: String? = null,
    val folderUrl: String? = null,
)

@Entity(
    tableName = "photos",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("bookId")],
)
data class PhotoEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    /** A [pt.estante.core.PageType] name. */
    val pageType: String,
    val localPath: String,
    val takenAt: Long,
    val uploaded: Boolean = false,
    val fileName: String? = null,
    val driveUrl: String? = null,
)

data class BookSummary(
    @Embedded val book: BookEntity,
    val photoCount: Int,
    val uploadedCount: Int,
)
