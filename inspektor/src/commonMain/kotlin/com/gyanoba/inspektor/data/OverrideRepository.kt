package com.gyanoba.inspektor.data

import com.gyanoba.inspektor.platform.getAppDataDir
import io.github.xxfast.kstore.KStore
import io.github.xxfast.kstore.extensions.getOrEmpty
import io.github.xxfast.kstore.extensions.minus
import io.github.xxfast.kstore.extensions.plus
import io.github.xxfast.kstore.extensions.updatesOrEmpty
import io.github.xxfast.kstore.file.extensions.listStoreOf
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
    override suspend fun add(override: Override) {
        require(override.id == 0L) { "New overrides must have id 0" }
        val newId = all.maxOfOrNull { it.id }?.plus(1) ?: 1
        store.plus(override.copy(id = newId))
    }

    override suspend fun remove(override: Override) = store.minus(override)

    override suspend fun update(override: Override) = store.update { overrideList ->
        overrideList?.map {
            if (it.id == override.id) override else it
        }
    }

    override val updates: Flow<List<Override>> get() = store.updatesOrEmpty

    private val cached : StateFlow<List<Override>> = store.updatesOrEmpty.stateIn(
        MainScope(), SharingStarted.WhileSubscribed(4000),
        runBlocking {
            store.getOrEmpty()
        }
    )

    init {
        // start collection to receive cached value
        GlobalScope.launch { cached.collect() }
    }

    override val all: List<Override> get() = cached.value

    override suspend fun getAll(): List<Override> = store.getOrEmpty()

    companion object {
        val Instance by lazy {
            OverrideRepositoryImpl(
                listStoreOf<Override>(file = getAppDataDir().toPath().resolve("overrideStore"))
            )
        }
    }
}