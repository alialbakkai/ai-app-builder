package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.QaloonRecitersCatalog
import com.example.ui.theme.ThemePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "tasmee_settings")

data class UserPreferences(
    val themePreference: ThemePreference = ThemePreference.SYSTEM,
    val mushafFontSizeSp: Float = 26f,
    val autoScrollEnabled: Boolean = true,
    val cloudVerificationEnabled: Boolean = true,
    val selectedReciterId: String = QaloonRecitersCatalog.reciters.first().id
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme_preference")
        val FONT_SIZE_SP = floatPreferencesKey("mushaf_font_size_sp")
        val AUTO_SCROLL = booleanPreferencesKey("auto_scroll_enabled")
        val CLOUD_VERIFICATION = booleanPreferencesKey("cloud_verification_enabled")
        val RECITER_ID = stringPreferencesKey("selected_reciter_id")
    }

    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        val themeStr = prefs[Keys.THEME] ?: ThemePreference.SYSTEM.name
        val theme = ThemePreference.entries.firstOrNull { it.name == themeStr }
            ?: ThemePreference.SYSTEM
        UserPreferences(
            themePreference = theme,
            mushafFontSizeSp = (prefs[Keys.FONT_SIZE_SP] ?: 26f).coerceIn(18f, 40f),
            autoScrollEnabled = prefs[Keys.AUTO_SCROLL] ?: true,
            cloudVerificationEnabled = prefs[Keys.CLOUD_VERIFICATION] ?: true,
            selectedReciterId = prefs[Keys.RECITER_ID] ?: QaloonRecitersCatalog.reciters.first().id
        )
    }

    suspend fun setThemePreference(theme: ThemePreference) {
        context.dataStore.edit { it[Keys.THEME] = theme.name }
    }

    suspend fun setFontSizeSp(sizeSp: Float) {
        context.dataStore.edit { it[Keys.FONT_SIZE_SP] = sizeSp.coerceIn(18f, 40f) }
    }

    suspend fun setAutoScrollEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_SCROLL] = enabled }
    }

    suspend fun setCloudVerificationEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.CLOUD_VERIFICATION] = enabled }
    }

    suspend fun setSelectedReciterId(reciterId: String) {
        context.dataStore.edit { it[Keys.RECITER_ID] = reciterId }
    }
}
