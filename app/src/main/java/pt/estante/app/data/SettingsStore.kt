package pt.estante.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

data class SheetSettings(val scriptUrl: String, val token: String) {
    val isComplete: Boolean
        get() = scriptUrl.startsWith("https://script.google.com/") && token.isNotBlank()
}

class SettingsStore(private val context: Context) {
    private val scriptUrlKey = stringPreferencesKey("script_url")
    private val sealedTokenKey = stringPreferencesKey("script_token_sealed")

    val sheetSettings: Flow<SheetSettings> = context.dataStore.data.map { prefs ->
        SheetSettings(
            scriptUrl = prefs[scriptUrlKey].orEmpty(),
            token = prefs[sealedTokenKey]
                ?.let { runCatching { SecretBox.decrypt(it) }.getOrDefault("") }
                .orEmpty(),
        )
    }

    suspend fun current(): SheetSettings = sheetSettings.first()

    suspend fun saveSheetSettings(scriptUrl: String, token: String) {
        context.dataStore.edit { prefs ->
            prefs[scriptUrlKey] = scriptUrl.trim()
            prefs[sealedTokenKey] = SecretBox.encrypt(token.trim())
        }
    }
}
