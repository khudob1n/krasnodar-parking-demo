package ru.krasnodar.parking.data

import android.content.Context
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import ru.krasnodar.parking.shared.catalog.CatalogRemote
import ru.krasnodar.parking.shared.catalog.CatalogSeed
import ru.krasnodar.parking.shared.catalog.CatalogStore
import ru.krasnodar.parking.shared.catalog.RemoteResult
import ru.krasnodar.parking.shared.catalog.StoredCatalog

/** Каталог из assets/catalog/parkings.json, собранный из data/snapshot. */
class AssetCatalogSeed(private val context: Context) : CatalogSeed {
    override suspend fun read(): String = withContext(Dispatchers.IO) {
        context.assets.open("catalog/parkings.json").bufferedReader().use { it.readText() }
    }
}

/** Последний каталог с сервера во внутренней памяти приложения. */
class FileCatalogStore(context: Context) : CatalogStore {
    private val dir = File(context.filesDir, "catalog")
    private val data = File(dir, "parkings.json")
    private val meta = File(dir, "meta.json")

    override suspend fun read(): StoredCatalog? = withContext(Dispatchers.IO) {
        if (!data.exists() || !meta.exists()) return@withContext null
        val m = JSONObject(meta.readText())
        StoredCatalog(data.readText(), m.optString("etag").ifEmpty { null }, m.getString("saved_at"))
    }

    override suspend fun write(catalog: StoredCatalog) = withContext(Dispatchers.IO) {
        dir.mkdirs()
        // Сначала во временный файл: оборванная запись не испортит рабочий каталог.
        val tmp = File(dir, "parkings.json.tmp")
        tmp.writeText(catalog.json)
        if (!tmp.renameTo(data)) throw IOException("не удалось сохранить каталог")
        meta.writeText(JSONObject().put("etag", catalog.etag ?: "").put("saved_at", catalog.savedAt).toString())
    }
}

/** GET /api/v1/parkings с If-None-Match. Сжатие gzip HttpURLConnection снимает сам. */
class HttpCatalogRemote(private val baseUrl: String) : CatalogRemote {
    override suspend fun fetch(etag: String?): RemoteResult = withContext(Dispatchers.IO) {
        val conn = try {
            (URL("$baseUrl/api/v1/parkings").openConnection() as HttpURLConnection).apply {
                connectTimeout = 7_000
                readTimeout = 20_000
                setRequestProperty("Accept", "application/json")
                if (etag != null) setRequestProperty("If-None-Match", etag)
            }
        } catch (e: IOException) {
            return@withContext RemoteResult.Unreachable(e.javaClass.simpleName)
        }
        try {
            when (val code = conn.responseCode) {
                HttpURLConnection.HTTP_NOT_MODIFIED -> RemoteResult.NotModified
                HttpURLConnection.HTTP_OK -> RemoteResult.Updated(
                    conn.inputStream.bufferedReader().use { it.readText() },
                    conn.getHeaderField("ETag"),
                )
                else -> RemoteResult.Failed(code)
            }
        } catch (e: IOException) {
            RemoteResult.Unreachable(e.javaClass.simpleName)
        } finally {
            conn.disconnect()
        }
    }
}

fun nowIso(): String = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString()
