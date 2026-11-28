package com.example.marketmind.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.marketmind.data.cache.SettingsDataStore
import com.example.marketmind.data.repository.MarketRepository
import com.example.marketmind.data.repository.MarketRepositoryImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsDataStore = SettingsDataStore(application)
    private val repository: MarketRepository = MarketRepositoryImpl(application)

    val baseUrl: StateFlow<String> = settingsDataStore.baseUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val autoRefresh: StateFlow<Boolean> = settingsDataStore.autoRefresh
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val demoMode: StateFlow<Boolean> = settingsDataStore.demoMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _connectionResult = MutableStateFlow<String?>(null)
    val connectionResult: StateFlow<String?> = _connectionResult.asStateFlow()

    private val _isTesting = MutableStateFlow(false)
    val isTesting: StateFlow<Boolean> = _isTesting.asStateFlow()

    fun saveBaseUrl(url: String) {
        viewModelScope.launch {
            settingsDataStore.saveBaseUrl(url)
        }
    }

    fun saveAutoRefresh(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.saveAutoRefresh(enabled)
        }
    }

    fun saveDemoMode(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.saveDemoMode(enabled)
        }
    }

    fun testConnection(url: String) {
        viewModelScope.launch {
            _isTesting.value = true
            _connectionResult.value = "Testing connection to $url..."
            val (_, message) = repository.testConnection(url)
            _isTesting.value = false
            _connectionResult.value = message
        }
    }
}