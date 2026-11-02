package pt.estante.app.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import pt.estante.app.appContainer
import pt.estante.app.data.MissingSettingsException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

/** Uploads one book's photos when the phone is online, retrying with backoff if the upload fails. */
class UploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val bookId = inputData.getString(KEY_BOOK_ID) ?: return Result.failure()
        val repository = applicationContext.appContainer.repository
        return try {
            repository.uploadBook(bookId)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: MissingSettingsException) {
            repository.markError(bookId, e.message)
            Result.failure()
        } catch (e: Exception) {
            repository.markError(bookId, e.message ?: e.javaClass.simpleName)
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val KEY_BOOK_ID = "bookId"
        private const val MAX_ATTEMPTS = 5

        fun enqueue(context: Context, bookId: String) {
            val request = OneTimeWorkRequestBuilder<UploadWorker>()
                .setInputData(workDataOf(KEY_BOOK_ID to bookId))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("upload-$bookId", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}
