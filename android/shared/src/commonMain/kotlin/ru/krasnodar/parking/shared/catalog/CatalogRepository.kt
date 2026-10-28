package ru.krasnodar.parking.shared.catalog

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import ru.krasnodar.parking.shared.model.CatalogSnapshot

/** Каталог, который поставляется вместе с приложением. */
fun interface CatalogSeed {
    suspend fun read(): String
}

/** Последний полученный с сервера каталог на устройстве. */
interface CatalogStore {
    suspend fun read(): StoredCatalog?
    suspend fun write(catalog: StoredCatalog)
}

data class StoredCatalog(val json: String, val etag: String?, val savedAt: String)

/** Сервер каталога. */
fun interface CatalogRemote {
    suspend fun fetch(etag: String?): RemoteResult
}

sealed interface RemoteResult {
    data object NotModified : RemoteResult
    data class Updated(val json: String, val etag: String?) : RemoteResult
    /** Нет сети или сервер недоступен. */
    data class Unreachable(val reason: String) : RemoteResult
    data class Failed(val httpCode: Int) : RemoteResult
}

enum class CatalogOrigin { SEED, CACHE, NETWORK }

sealed interface SyncStatus {
    data object NotChecked : SyncStatus
    data object Checking : SyncStatus
    data class UpToDate(val at: String) : SyncStatus
    data class Updated(val at: String) : SyncStatus
    /** Данные показываются из памяти устройства, сервер не ответил. */
    data class Offline(val at: String, val reason: String) : SyncStatus
    data class Error(val at: String, val message: String) : SyncStatus
}

data class CatalogState(
    val snapshot: CatalogSnapshot? = null,
    val origin: CatalogOrigin? = null,
    /** Когда сохранён локальный каталог, если он из кэша или сети. */
    val savedAt: String? = null,
    val sync: SyncStatus = SyncStatus.NotChecked,
    val loading: Boolean = true,
    /** Нечего показать совсем: ни кэша, ни встроенного каталога. */
    val fatal: String? = null,
)

/**
 * Офлайн-первый каталог: сразу показывает то, что есть на устройстве,
 * затем в фоне спрашивает сервер, изменилась ли версия.
 */
class CatalogRepository(
    private val seed: CatalogSeed,
    private val store: CatalogStore,
    private val remote: CatalogRemote,
    private val now: () -> String,
) {
    private val _state = MutableStateFlow(CatalogState())
    val state: StateFlow<CatalogState> = _state.asStateFlow()
    private var etag: String? = null

    suspend fun load() {
        val cached = runCatching { store.read() }.getOrNull()?.let { stored ->
            runCatching { CatalogCodec.decode(stored.json) }.getOrNull()?.let { stored to it }
        }
        val seeded = runCatching { CatalogCodec.decode(seed.read()) }.getOrNull()

        // Новая версия приложения может везти каталог свежее кэша.
        val useCache = cached != null && (seeded == null || cached.second.generatedAt >= seeded.generatedAt)
        when {
            useCache -> {
                val (stored, snap) = cached
                etag = stored.etag
                _state.update { it.copy(snapshot = snap, origin = CatalogOrigin.CACHE, savedAt = stored.savedAt, loading = false, fatal = null) }
            }
            seeded != null -> _state.update { it.copy(snapshot = seeded, origin = CatalogOrigin.SEED, savedAt = null, loading = false, fatal = null) }
            else -> _state.update { it.copy(loading = false, fatal = "Не удалось открыть каталог парковок на устройстве") }
        }
    }

    suspend fun refresh() {
        _state.update { it.copy(sync = SyncStatus.Checking) }
        val at = now()
        val sync = when (val r = remote.fetch(etag)) {
            is RemoteResult.NotModified -> SyncStatus.UpToDate(at)
            is RemoteResult.Updated -> {
                val snap = runCatching { CatalogCodec.decode(r.json) }
                if (snap.isFailure) {
                    // Битый ответ не заменяет рабочий каталог.
                    SyncStatus.Error(at, "Сервер прислал каталог, который не удалось прочитать")
                } else {
                    store.write(StoredCatalog(r.json, r.etag, at))
                    etag = r.etag
                    val changed = snap.getOrThrow().version != _state.value.snapshot?.version
                    _state.update { it.copy(snapshot = snap.getOrThrow(), origin = CatalogOrigin.NETWORK, savedAt = at, fatal = null, loading = false) }
                    if (changed) SyncStatus.Updated(at) else SyncStatus.UpToDate(at)
                }
            }
            is RemoteResult.Unreachable -> SyncStatus.Offline(at, r.reason)
            is RemoteResult.Failed -> SyncStatus.Error(at, "Сервер каталога ответил ${r.httpCode}")
        }
        _state.update { it.copy(sync = sync) }
    }
}
