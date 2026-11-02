package pt.estante.app.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import pt.estante.app.net.BookPayload
import pt.estante.app.net.PhotoPayload
import pt.estante.app.net.SheetApi
import pt.estante.app.util.ImageUtils
import pt.estante.app.work.UploadWorker
import pt.estante.core.Condition
import pt.estante.core.FileNaming
import pt.estante.core.PageType
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.Base64
import java.util.UUID

class MissingSettingsException : Exception("Connect your Google Sheet in Settings first.")

class BookRepository(
    private val context: Context,
    database: AppDatabase,
    private val settings: SettingsStore,
    private val api: SheetApi,
) {
    private val books = database.bookDao()
    private val photos = database.photoDao()

    fun observeSummaries(): Flow<List<BookSummary>> = books.observeSummaries()

    fun observeBook(id: String): Flow<BookEntity?> = books.observe(id)

    fun observePhotos(bookId: String): Flow<List<PhotoEntity>> = photos.observeForBook(bookId)

    suspend fun photosFor(bookId: String): List<PhotoEntity> = photos.forBook(bookId)

    suspend fun createBook(title: String, condition: Condition): String {
        val id = UUID.randomUUID().toString()
        books.insert(
            BookEntity(id = id, title = title.trim(), condition = condition.name, createdAt = System.currentTimeMillis()),
        )
        return id
    }

    fun newPhotoFile(bookId: String, photoId: String): File {
        val folder = File(context.filesDir, "photos/$bookId").apply { mkdirs() }
        return File(folder, "$photoId.jpg")
    }

    suspend fun addPhoto(bookId: String, photoId: String, pageType: PageType, file: File) {
        photos.insert(
            PhotoEntity(
                id = photoId,
                bookId = bookId,
                pageType = pageType.name,
                localPath = file.absolutePath,
                takenAt = System.currentTimeMillis(),
            ),
        )
    }

    /** Only photos that haven't reached the Sheet yet can be deleted from the app. */
    suspend fun deletePhoto(photo: PhotoEntity) {
        if (photo.uploaded) return
        photos.delete(photo)
        File(photo.localPath).delete()
    }

    suspend fun queueUpload(bookId: String) {
        books.setSyncState(bookId, SyncState.QUEUED.name, null)
        UploadWorker.enqueue(context, bookId)
    }

    suspend fun queueIfPending(bookId: String) {
        if (photos.forBook(bookId).any { !it.uploaded }) queueUpload(bookId)
    }

    suspend fun queueAllPending() {
        photos.bookIdsWithPendingPhotos().forEach { queueUpload(it) }
    }

    suspend fun markError(bookId: String, message: String?) {
        books.setSyncState(bookId, SyncState.ERROR.name, message)
    }

    /** Sends the book and its new photos to the Sheet. Safe to repeat: the script ignores photos it already has. */
    suspend fun uploadBook(bookId: String) {
        val sheet = settings.current()
        if (!sheet.isComplete) throw MissingSettingsException()
        val book = books.get(bookId) ?: return
        books.setSyncState(bookId, SyncState.SYNCING.name, null)

        val condition = Condition.valueOf(book.condition)
        api.upsertBook(sheet, BookPayload(book.id, book.title, condition.label, book.createdAt))
            .folderUrl
            ?.let { books.setFolderUrl(bookId, it) }

        val all = photos.forBook(bookId)
        for (photo in all.filter { !it.uploaded }) {
            val pageType = PageType.valueOf(photo.pageType)
            val position = all.filter { it.pageType == photo.pageType }
                .sortedBy { it.takenAt }
                .indexOfFirst { it.id == photo.id } + 1
            val takenOn = Instant.ofEpochMilli(photo.takenAt).atZone(ZoneId.systemDefault()).toLocalDate()
            val fileName = FileNaming.photoFileName(book.title, takenOn, pageType, position)
            val jpeg = ImageUtils.prepareForUpload(File(photo.localPath))
            val reply = api.uploadPhoto(
                sheet,
                bookId,
                PhotoPayload(
                    id = photo.id,
                    fileName = fileName,
                    pageType = pageType.code,
                    takenAt = photo.takenAt,
                    mimeType = "image/jpeg",
                    dataBase64 = Base64.getEncoder().encodeToString(jpeg),
                ),
            )
            photos.markUploaded(photo.id, fileName, reply.fileUrl.orEmpty())
        }
        api.finishBook(sheet, bookId)
        books.setSyncState(bookId, SyncState.SYNCED.name, null)
    }
}
