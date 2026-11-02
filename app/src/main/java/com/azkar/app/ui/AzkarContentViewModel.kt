package com.azkar.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.azkar.app.data.AzkarContent
import com.azkar.app.data.AzkarRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class AzkarContentViewModel @Inject constructor(
    repository: AzkarRepository
) : ViewModel() {
    val content: StateFlow<AzkarContent?> = repository.content
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
