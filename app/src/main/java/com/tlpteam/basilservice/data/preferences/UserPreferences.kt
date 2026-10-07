package com.tlpteam.basilservice.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "messenger_prefs")

@Serializable
data class SavedAccount(
    val username: String,
    val token: String,
    val serverUrl: String
)

class UserPreferences(private val context: Context) {

    companion object {
        private val KEY_SERVER_URL = stringPreferencesKey("server_url")
        private val KEY_AUTH_TOKEN = stringPreferencesKey("auth_token")
        private val KEY_SAVED_ACCOUNTS = stringPreferencesKey("saved_accounts")
        const val DEFAULT_SERVER_URL = "https://mewoify.tlpdev.ru"
    }

    val serverUrlFlow: Flow<String> = context.dataStore.data
        .map { prefs -> prefs[KEY_SERVER_URL] ?: DEFAULT_SERVER_URL }

    val authTokenFlow: Flow<String?> = context.dataStore.data
        .map { prefs -> prefs[KEY_AUTH_TOKEN] }

    val savedAccountsFlow: Flow<List<SavedAccount>> = context.dataStore.data
        .map { prefs ->
            val jsonStr = prefs[KEY_SAVED_ACCOUNTS] ?: return@map emptyList()
            try {
                Json.decodeFromString<List<SavedAccount>>(jsonStr)
            } catch (e: Exception) {
                emptyList()
            }
        }

    suspend fun saveServerUrl(url: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SERVER_URL] = url
        }
    }

    suspend fun saveAuthToken(token: String?) {
        context.dataStore.edit { prefs ->
            if (token == null) {
                prefs.remove(KEY_AUTH_TOKEN)
            } else {
                prefs[KEY_AUTH_TOKEN] = token
            }
        }
    }

    suspend fun addOrUpdateAccount(account: SavedAccount) {
        context.dataStore.edit { prefs ->
            val jsonStr = prefs[KEY_SAVED_ACCOUNTS]
            val accounts = try {
                if (jsonStr != null) Json.decodeFromString<MutableList<SavedAccount>>(jsonStr) else mutableListOf()
            } catch (e: Exception) {
                mutableListOf()
            }
            // Remove existing with same username and serverUrl
            accounts.removeAll { it.username == account.username && it.serverUrl == account.serverUrl }
            accounts.add(account)
            prefs[KEY_SAVED_ACCOUNTS] = Json.encodeToString(accounts)
        }
    }

    suspend fun removeAccount(username: String, serverUrl: String) {
        context.dataStore.edit { prefs ->
            val jsonStr = prefs[KEY_SAVED_ACCOUNTS] ?: return@edit
            val accounts = try {
                Json.decodeFromString<MutableList<SavedAccount>>(jsonStr)
            } catch (e: Exception) {
                mutableListOf()
            }
            accounts.removeAll { it.username == username && it.serverUrl == serverUrl }
            prefs[KEY_SAVED_ACCOUNTS] = Json.encodeToString(accounts)
        }
    }
}
