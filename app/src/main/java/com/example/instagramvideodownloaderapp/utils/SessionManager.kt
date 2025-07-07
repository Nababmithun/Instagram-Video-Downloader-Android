package com.example.instagramvideodownloaderapp.utils

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

object SessionManager {
    private val Context.dataStore by preferencesDataStore("user_prefs")

    private val SESSION_ID_KEY = stringPreferencesKey("session_id")

    suspend fun saveSessionId(context: Context, sessionId: String) {
        context.dataStore.edit { prefs ->
            prefs[SESSION_ID_KEY] = sessionId
        }
    }

    val sessionIdFlow: (Context) -> Flow<String> = { context ->
        context.dataStore.data
            .map { prefs -> prefs[SESSION_ID_KEY] ?: "" }
    }
}