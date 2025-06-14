package com.example.englishapp.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.tokenDataStore by preferencesDataStore(name = "token_prefs")

object TokenDataStore {
    private val TOKEN_KEY = stringPreferencesKey("token")

    suspend fun saveToken(context: Context, token: String) {
        context.tokenDataStore.edit { prefs ->
            prefs[TOKEN_KEY] = token
        }
    }
    suspend fun getToken(context: Context): String? {
        return context.tokenDataStore.data
            .map { prefs -> prefs[TOKEN_KEY] }
            .first()
    }
    suspend fun clearToken(context: Context) {
        context.tokenDataStore.edit { prefs ->
            prefs.remove(TOKEN_KEY)
        }
    }
}
