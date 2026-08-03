package com.example.ether.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.*
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private val Context.privacyDataStore: DataStore<Preferences> by preferencesDataStore(name = "privacy_stats")

@Singleton
class PrivacyRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.privacyDataStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private val _pendingAds = MutableStateFlow(0L)
    private val _pendingTrackers = MutableStateFlow(0L)

    private object Keys {
        val BLOCKED_TRACKERS_TOTAL = longPreferencesKey("blocked_trackers_total")
        val BLOCKED_ADS_TOTAL = longPreferencesKey("blocked_ads_total")
    }

    init {
        // Periodically flush pending counts to DataStore to avoid excessive disk writes
        scope.launch {
            _pendingAds.collectLatest { count ->
                if (count > 0) {
                    delay(5000) // Wait for more increments
                    flushAds()
                }
            }
        }
        scope.launch {
            _pendingTrackers.collectLatest { count ->
                if (count > 0) {
                    delay(5000)
                    flushTrackers()
                }
            }
        }
    }

    private suspend fun flushAds() {
        val toAdd = _pendingAds.getAndUpdate { 0 }
        if (toAdd > 0) {
            try {
                dataStore.edit { prefs ->
                    val current = prefs[Keys.BLOCKED_ADS_TOTAL] ?: 0L
                    prefs[Keys.BLOCKED_ADS_TOTAL] = current + toAdd
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to flush blocked ads to DataStore")
            }
        }
    }

    private suspend fun flushTrackers() {
        val toAdd = _pendingTrackers.getAndUpdate { 0 }
        if (toAdd > 0) {
            try {
                dataStore.edit { prefs ->
                    val current = prefs[Keys.BLOCKED_TRACKERS_TOTAL] ?: 0L
                    prefs[Keys.BLOCKED_TRACKERS_TOTAL] = current + toAdd
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to flush blocked trackers to DataStore")
            }
        }
    }

    val totalBlockedTrackers: Flow<Long> = dataStore.data.map { it[Keys.BLOCKED_TRACKERS_TOTAL] ?: 0L }
    val totalBlockedAds: Flow<Long> = dataStore.data.map { it[Keys.BLOCKED_ADS_TOTAL] ?: 0L }

    fun incrementBlockedTrackers(count: Long = 1) {
        _pendingTrackers.update { it + count }
    }

    fun incrementBlockedAds(count: Long = 1) {
        _pendingAds.update { it + count }
    }
}
