package ru.krasnodar.parking

import android.app.Application
import org.maplibre.android.MapLibre
import ru.krasnodar.parking.data.AssetCatalogSeed
import ru.krasnodar.parking.data.FileCatalogStore
import ru.krasnodar.parking.data.HttpCatalogRemote
import ru.krasnodar.parking.data.nowIso
import ru.krasnodar.parking.shared.catalog.CatalogRepository

class ParkingApp : Application() {
    lateinit var catalog: CatalogRepository
        private set

    override fun onCreate() {
        super.onCreate()
        // Ключ API не нужен: стиль карты и данные локальные.
        MapLibre.getInstance(this)
        catalog = CatalogRepository(
            seed = AssetCatalogSeed(this),
            store = FileCatalogStore(this),
            remote = HttpCatalogRemote(BuildConfig.CATALOG_BASE_URL),
            now = ::nowIso,
        )
    }
}
