package pt.estante.app.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import pt.estante.app.data.SheetSettings
import java.util.concurrent.TimeUnit

class SheetApiException(message: String) : Exception(message)

@Serializable
data class BookPayload(
    val id: String,
    val title: String,
    val condition: String,
    val createdAt: Long,
)

@Serializable
data class PhotoPayload(
    val id: String,
    val fileName: String,
    val pageType: String,
    val takenAt: Long,
    val mimeType: String,
    val dataBase64: String,
)

@Serializable
private data class ApiRequest(
    val token: String,
    val action: String,
    val book: BookPayload? = null,
    val bookId: String? = null,
    val photo: PhotoPayload? = null,
)

@Serializable
data class ApiResponse(
    val ok: Boolean,
    val error: String? = null,
    val version: String? = null,
    val sheetUrl: String? = null,
    val folderUrl: String? = null,
    val fileUrl: String? = null,
)

/** Talks to the Apps Script web app inside the user's Google Sheet (backend/apps-script/Code.gs). */
class SheetApi(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build(),
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun ping(settings: SheetSettings): ApiResponse =
        call(settings, ApiRequest(settings.token, "ping"))

    suspend fun upsertBook(settings: SheetSettings, book: BookPayload): ApiResponse =
        call(settings, ApiRequest(settings.token, "upsertBook", book = book))

    suspend fun uploadPhoto(settings: SheetSettings, bookId: String, photo: PhotoPayload): ApiResponse =
        call(settings, ApiRequest(settings.token, "uploadPhoto", bookId = bookId, photo = photo))

    suspend fun finishBook(settings: SheetSettings, bookId: String): ApiResponse =
        call(settings, ApiRequest(settings.token, "finishBook", bookId = bookId))

    private suspend fun call(settings: SheetSettings, request: ApiRequest): ApiResponse =
        withContext(Dispatchers.IO) {
            val body = json.encodeToString(ApiRequest.serializer(), request).toRequestBody(jsonMediaType)
            // Apps Script answers a POST with a redirect to the result; OkHttp follows it as a GET.
            val httpRequest = Request.Builder().url(settings.scriptUrl).post(body).build()
            client.newCall(httpRequest).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw SheetApiException("The Google Sheet script answered HTTP ${response.code}.")
                }
                val reply = try {
                    json.decodeFromString(ApiResponse.serializer(), text)
                } catch (e: SerializationException) {
                    throw SheetApiException(
                        "That web app URL didn't answer like the Estante script. Check the URL, and that the " +
                            "deployment's access is set to \"Anyone\".",
                    )
                }
                if (!reply.ok) throw SheetApiException(reply.error ?: "The Google Sheet script reported an error.")
                reply
            }
        }
}
