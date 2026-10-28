package ru.krasnodar.parking.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ru.krasnodar.parking.shared.catalog.CatalogOrigin
import ru.krasnodar.parking.shared.catalog.CatalogState
import ru.krasnodar.parking.shared.catalog.SyncStatus
import ru.krasnodar.parking.shared.format.ParkingFormat
import ru.krasnodar.parking.ui.theme.Brand
import ru.krasnodar.parking.ui.theme.Spacing

/** Текст плашки состояния данных или null, если всё свежо. */
fun statusText(state: CatalogState, online: Boolean): String? {
    val dataDate = state.savedAt?.let(ParkingFormat::date)
        ?: state.snapshot?.sources?.firstOrNull()?.fetchedAt?.let(ParkingFormat::date)
    val where = when (state.origin) {
        CatalogOrigin.SEED -> "встроенный каталог"
        CatalogOrigin.CACHE, CatalogOrigin.NETWORK -> "сохранённый каталог"
        null -> return null
    }
    return when (val s = state.sync) {
        is SyncStatus.Offline -> if (!online) {
            "Нет сети. Показан $where от $dataDate."
        } else {
            "Сервер каталога недоступен. Показан $where от $dataDate."
        }
        is SyncStatus.Error -> "${s.message}. Показан $where от $dataDate."
        else -> null
    }
}

/** Заметная, но не навязчивая плашка: не модальное окно и не всплывашка. */
@Composable
fun StatusBanner(text: String, onRetry: (() -> Unit)?, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        color = Brand.Orange.copy(alpha = 0.12f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(Modifier.padding(start = Spacing.m, end = Spacing.s), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(vertical = 12.dp))
            if (onRetry != null) {
                TextButton(onClick = onRetry) { Text("Повторить", color = Color(0xFFB45309)) }
            }
        }
    }
}
