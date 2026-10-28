package ru.krasnodar.parking.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlinx.coroutines.test.runTest
import ru.krasnodar.parking.shared.catalog.CatalogOrigin
import ru.krasnodar.parking.shared.catalog.CatalogRepository
import ru.krasnodar.parking.shared.catalog.CatalogStore
import ru.krasnodar.parking.shared.catalog.RemoteResult
import ru.krasnodar.parking.shared.catalog.StoredCatalog
import ru.krasnodar.parking.shared.catalog.SyncStatus

private class MemoryStore(var value: StoredCatalog? = null) : CatalogStore {
    override suspend fun read() = value
    override suspend fun write(catalog: StoredCatalog) { value = catalog }
}

class RepositoryTest {
    private val now = { "2026-10-28T20:00:00Z" }

    @Test
    fun seedFirstThenNetworkUpdateIsStored() = runTest {
        val store = MemoryStore()
        var sentEtag: String? = "unset"
        val repo = CatalogRepository({ snapshotJson("seed") }, store, { e -> sentEtag = e; RemoteResult.Updated(snapshotJson("net"), "\"net\"") }, now)
        repo.load()
        assertEquals(CatalogOrigin.SEED, repo.state.value.origin)
        assertEquals("seed", repo.state.value.snapshot?.version)
        repo.refresh()
        assertEquals(null, sentEtag)
        assertEquals(CatalogOrigin.NETWORK, repo.state.value.origin)
        assertIs<SyncStatus.Updated>(repo.state.value.sync)
        assertEquals("\"net\"", store.value?.etag)
    }

    @Test
    fun offlineKeepsCachedDataAndSaysSo() = runTest {
        val store = MemoryStore(StoredCatalog(snapshotJson("cached", "2026-10-29T00:00:00Z"), "\"cached\"", "2026-10-29T00:00:00Z"))
        var sentEtag: String? = null
        val repo = CatalogRepository({ snapshotJson("seed") }, store, { e -> sentEtag = e; RemoteResult.Unreachable("нет сети") }, now)
        repo.load()
        assertEquals(CatalogOrigin.CACHE, repo.state.value.origin)
        repo.refresh()
        assertEquals("\"cached\"", sentEtag)
        assertEquals("cached", repo.state.value.snapshot?.version)
        assertIs<SyncStatus.Offline>(repo.state.value.sync)
    }

    @Test
    fun newerSeedWinsOverOldCache() = runTest {
        val store = MemoryStore(StoredCatalog(snapshotJson("old", "2026-10-01T00:00:00Z"), null, "2026-10-01T00:00:00Z"))
        val repo = CatalogRepository({ snapshotJson("seed", "2026-10-28T19:00:00Z") }, store, { RemoteResult.NotModified }, now)
        repo.load()
        assertEquals("seed", repo.state.value.snapshot?.version)
    }

    @Test
    fun brokenServerResponseDoesNotReplaceCatalog() = runTest {
        val store = MemoryStore()
        val repo = CatalogRepository({ snapshotJson("seed") }, store, { RemoteResult.Updated("{oops", null) }, now)
        repo.load()
        repo.refresh()
        assertEquals("seed", repo.state.value.snapshot?.version)
        assertIs<SyncStatus.Error>(repo.state.value.sync)
        assertEquals(null, store.value)

        val wrongSchema = CatalogRepository({ snapshotJson("seed") }, store, { RemoteResult.Updated(snapshotJson("v2", schema = 2), null) }, now)
        wrongSchema.load()
        wrongSchema.refresh()
        assertEquals("seed", wrongSchema.state.value.snapshot?.version)
    }

    @Test
    fun brokenCacheFallsBackToSeed() = runTest {
        val repo = CatalogRepository({ snapshotJson("seed") }, MemoryStore(StoredCatalog("garbage", null, "x")), { RemoteResult.NotModified }, now)
        repo.load()
        assertNotNull(repo.state.value.snapshot)
        assertEquals(CatalogOrigin.SEED, repo.state.value.origin)
    }

    @Test
    fun nothingAtAllIsFatal() = runTest {
        val repo = CatalogRepository({ error("no asset") }, MemoryStore(), { RemoteResult.NotModified }, now)
        repo.load()
        assertNotNull(repo.state.value.fatal)
    }
}
