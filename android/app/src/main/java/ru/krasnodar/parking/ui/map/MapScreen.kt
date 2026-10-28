package ru.krasnodar.parking.ui.map

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.krasnodar.parking.shared.catalog.SyncStatus
import ru.krasnodar.parking.ui.components.StatusBanner
import ru.krasnodar.parking.ui.components.statusText
import ru.krasnodar.parking.ui.theme.Spacing

@Composable
fun MapScreen(vm: MapViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snapshot = state.catalog.snapshot
    BackHandler(enabled = state.selected != null) { vm.select(null) }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::onQuery,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Улица или номер парковки") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { vm.onQuery("") }) { Icon(Icons.Outlined.Clear, contentDescription = "Очистить поиск") }
                    }
                },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary),
            )
            Row(Modifier.padding(top = Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                    MapMode.entries.forEachIndexed { i, m ->
                        SegmentedButton(
                            selected = state.mode == m,
                            onClick = { vm.onMode(m) },
                            shape = SegmentedButtonDefaults.itemShape(i, MapMode.entries.size),
                        ) { Text(if (m == MapMode.MAP) "Карта" else "Список") }
                    }
                }
                FilterChip(
                    selected = state.showFree,
                    onClick = { vm.onShowFree(!state.showFree) },
                    label = { Text("Бесплатные") },
                    modifier = Modifier.padding(start = Spacing.s),
                )
            }
            val status = statusText(state.catalog, state.online)
            if (status != null) {
                StatusBanner(status, onRetry = if (state.catalog.sync is SyncStatus.Checking) null else ({ vm.retry() }), modifier = Modifier.padding(top = Spacing.s))
            }
            if (state.mode == MapMode.MAP && snapshot != null && !hasBundledBasemap(context)) {
                Text(
                    "Базовая карта города ещё не встроена: показаны только линии и точки парковок.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.s),
                )
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.catalog.loading -> ParkingListSkeleton()
                state.catalog.fatal != null -> Column(Modifier.padding(Spacing.l)) {
                    Text("Каталог недоступен", style = MaterialTheme.typography.titleLarge)
                    Text(state.catalog.fatal!!, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> Crossfade(targetState = state.mode, animationSpec = tween(250), label = "mode") { mode ->
                    when (mode) {
                        MapMode.MAP -> ParkingMap(state.visible, state.selected, vm::select, Modifier.fillMaxSize())
                        MapMode.LIST -> ParkingList(state.visible, state.query, { vm.select(it); vm.onMode(MapMode.MAP) }, Modifier.fillMaxSize())
                    }
                }
            }
            val selected = state.selected
            if (selected != null && state.mode == MapMode.MAP) {
                ParkingCard(
                    parking = selected,
                    source = snapshot?.source(selected.source),
                    onClose = { vm.select(null) },
                    modifier = Modifier.align(Alignment.BottomCenter).heightIn(max = 520.dp),
                )
            }
        }
    }
}
