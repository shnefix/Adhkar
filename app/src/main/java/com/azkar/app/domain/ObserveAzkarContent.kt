package com.azkar.app.domain

import com.azkar.app.data.AzkarContent
import com.azkar.app.data.AzkarRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveAzkarContent @Inject constructor(
    private val repository: AzkarRepository
) {
    operator fun invoke(): Flow<AzkarContent> = repository.content
}
