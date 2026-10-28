package ru.krasnodar.parking.shared.catalog

import kotlinx.serialization.json.Json
import ru.krasnodar.parking.shared.model.CatalogSnapshot

/** Разбор снимка. Новые поля сервера игнорируются, несовместимая схема — ошибка. */
object CatalogCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun decode(text: String): CatalogSnapshot {
        val snap = json.decodeFromString(CatalogSnapshot.serializer(), text)
        if (snap.schemaVersion != CatalogSnapshot.SUPPORTED_SCHEMA) {
            throw CatalogFormatException("Схема каталога ${snap.schemaVersion} не поддерживается этой версией приложения")
        }
        if (snap.parkings.isEmpty()) throw CatalogFormatException("Каталог пустой")
        return snap
    }
}

class CatalogFormatException(message: String) : Exception(message)
