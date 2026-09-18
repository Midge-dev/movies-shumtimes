package com.reelay.tv.data.plex

interface SecureTokenStore {
    suspend fun saveToken(token: String)
    suspend fun loadToken(): String?
    suspend fun clearToken()
}
