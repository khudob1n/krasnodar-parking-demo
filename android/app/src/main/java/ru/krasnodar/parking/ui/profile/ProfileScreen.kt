package ru.krasnodar.parking.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.krasnodar.parking.shared.catalog.CatalogOrigin
import ru.krasnodar.parking.shared.catalog.SyncStatus
import ru.krasnodar.parking.shared.format.ParkingFormat
import ru.krasnodar.parking.ui.map.MapViewModel
import ru.krasnodar.parking.ui.theme.Spacing

/** Профиль: пока раздел «О данных» — откуда каталог и насколько ему можно верить. */
@Composable
fun ProfileScreen(vm: MapViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val cat = state.catalog
    val snap = cat.snapshot
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.l)) {
        Text("Профиль", style = MaterialTheme.typography.headlineLarge)
        Text("О данных", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = Spacing.l))
        if (snap == null) {
            Text(ParkingFormat.NO_DATA, style = MaterialTheme.typography.bodyLarge)
            return@Column
        }
        InfoLine("Версия каталога", snap.version)
        InfoLine("Платных парковок", snap.counts.paid.toString())
        InfoLine("Мест на платных", snap.counts.paidPlaces.toString())
        InfoLine("Бесплатных", snap.counts.free.toString())
        InfoLine("С геометрией на карте", "${snap.counts.withGeometry} из ${snap.counts.total}")
        InfoLine(
            "Откуда сейчас",
            when (cat.origin) {
                CatalogOrigin.SEED -> "встроен в приложение"
                CatalogOrigin.CACHE -> "сохранён на устройстве ${cat.savedAt?.let(ParkingFormat::dateTime) ?: ""}"
                CatalogOrigin.NETWORK -> "получен с сервера ${cat.savedAt?.let(ParkingFormat::dateTime) ?: ""}"
                null -> ParkingFormat.NO_DATA
            },
        )
        InfoLine(
            "Проверка обновлений",
            when (val s = cat.sync) {
                SyncStatus.NotChecked -> "не выполнялась"
                SyncStatus.Checking -> "идёт…"
                is SyncStatus.UpToDate -> "актуально на ${ParkingFormat.dateTime(s.at)}"
                is SyncStatus.Updated -> "обновлено ${ParkingFormat.dateTime(s.at)}"
                is SyncStatus.Offline -> "сервер недоступен (${ParkingFormat.dateTime(s.at)})"
                is SyncStatus.Error -> s.message
            },
        )
        for (src in snap.sources) {
            Text(ParkingFormat.sourceLine(src), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = Spacing.m))
            if (src.provenance.isNotBlank()) {
                Text(src.provenance, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            "Демо-версия. Это не официальный сервис города: данные могут быть неполными или устаревшими, оплата не проводится. " +
                "Номер парковки — id из данных портала, его соответствие табличке не подтверждено. Тарифная зона и свободные места: нет данных.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.l),
        )
        Text(
            "Шрифт Onest — SIL Open Font License 1.1. Карта — MapLibre Native (BSD-2).",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.m),
        )
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Column(Modifier.padding(top = Spacing.m)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
        HorizontalDivider(Modifier.padding(top = Spacing.s), color = MaterialTheme.colorScheme.outline)
    }
}
