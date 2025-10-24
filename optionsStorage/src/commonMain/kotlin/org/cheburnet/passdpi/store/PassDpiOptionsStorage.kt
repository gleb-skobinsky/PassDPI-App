package org.cheburnet.passdpi.store

import kotlinx.coroutines.flow.Flow

interface PassDpiOptionsStorage {
    suspend fun getPort(): Int

    fun observePort(): Flow<Int>

    suspend fun setPort(new: Int)
}