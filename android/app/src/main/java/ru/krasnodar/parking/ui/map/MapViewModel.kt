package ru.krasnodar.parking.ui.map

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.krasnodar.parking.ParkingApp
import ru.krasnodar.parking.data.networkAvailable
import ru.krasnodar.parking.shared.catalog.CatalogSearch
import ru.krasnodar.parking.shared.catalog.CatalogState
import ru.krasnodar.parking.shared.model.Parking

enum class MapMode { MAP, LIST }

data class MapUiState(
    val catalog: CatalogState = CatalogState(),
    val query: String = "",
    val mode: MapMode = MapMode.MAP,
    val showFree: Boolean = false,
    val selected: Parking? = null,
    /** Что показывать на карте и в списке после фильтров и поиска. */
    val visible: List<Parking> = emptyList(),
    val online: Boolean = true,
)

private data class Ui(val query: String = "", val mode: MapMode = MapMode.MAP, val showFree: Boolean = false, val selectedId: String? = null)

class MapViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as ParkingApp).catalog
    private val ui = MutableStateFlow(Ui())
    private val online = MutableStateFlow(true)

    val state: StateFlow<MapUiState> = combine(repo.state, ui, online) { cat, u, on ->
        val all = cat.snapshot?.parkings.orEmpty()
        val base = if (u.showFree) all else all.filter { it.paid }
        val visible = if (u.query.isBlank()) base else CatalogSearch.search(base, u.query, paidOnly = !u.showFree, limit = 200)
        MapUiState(
            catalog = cat,
            query = u.query,
            mode = u.mode,
            showFree = u.showFree,
            selected = all.firstOrNull { it.id == u.selectedId },
            visible = visible,
            online = on,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapUiState())

    init {
        viewModelScope.launch {
            repo.load()
            repo.refresh()
        }
        // Вернулась сеть — снова спрашиваем сервер. Нажимать ничего не нужно.
        viewModelScope.launch {
            app.networkAvailable().collect { on -> online.value = on }
        }
        viewModelScope.launch {
            online.drop(1).filter { it }.collect { repo.refresh() }
        }
    }

    fun onQuery(q: String) = ui.update { it.copy(query = q) }
    fun onMode(m: MapMode) = ui.update { it.copy(mode = m) }
    fun onShowFree(v: Boolean) = ui.update { it.copy(showFree = v) }
    fun select(id: String?) = ui.update { it.copy(selectedId = id) }
    fun retry() = viewModelScope.launch { repo.refresh() }
}
