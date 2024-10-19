package com.gyanoba.inspektor.data

import com.gyanoba.inspektor.platform.getAppDataDir
import io.github.xxfast.kstore.KStore
import io.github.xxfast.kstore.extensions.getOrEmpty
import io.github.xxfast.kstore.extensions.minus
import io.github.xxfast.kstore.extensions.plus
import io.github.xxfast.kstore.extensions.updatesOrEmpty
import io.github.xxfast.kstore.file.extensions.listStoreOf
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath


public interface OverrideRepository {
    public suspend fun add(override: Override)

    public suspend fun remove(override: Override)

    public suspend fun update(override: Override)

    public val updates: Flow<List<Override>>

    public val all: List<Override>

    public suspend fun getAll(): List<Override>
}

internal class OverrideRepositoryImpl(
    private val store: KStore<List<Override>>
): OverrideRepository {
    override suspend fun add(override: Override) = store.plus(override)

    override suspend fun remove(override: Override) = store.minus(override)

    override suspend fun update(override: Override) = store.update { overrideList ->
        overrideList?.map {
            if (it.id == override.id) override else it
        }
    }

    override val updates: StateFlow<List<Override>> get() = store.updatesOrEmpty.stateIn(
        MainScope(),
        SharingStarted.Eagerly,
        runBlocking { store.getOrEmpty() }
    )

    override val all: List<Override> get() = updates.value

    override suspend fun getAll(): List<Override> = store.getOrEmpty()

    companion object {
        val Instance by lazy {
            OverrideRepositoryImpl(
                listStoreOf<Override>(file = getAppDataDir().toPath().resolve("overrideStore"))
            )
        }
    }
}