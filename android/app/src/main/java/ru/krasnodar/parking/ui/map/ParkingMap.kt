package ru.krasnodar.parking.ui.map

import android.content.Context
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.MultiLineString
import org.maplibre.geojson.Point
import ru.krasnodar.parking.shared.geo.Geo
import ru.krasnodar.parking.shared.model.GeometryStatus
import ru.krasnodar.parking.shared.model.Parking
import ru.krasnodar.parking.ui.theme.Brand

private const val SRC_LINES = "parking-lines"
private const val SRC_POINTS = "parking-points"
private const val LAYER_LINES = "parking-lines"
private const val LAYER_LINES_SELECTED = "parking-lines-selected"
private const val LAYER_POINTS = "parking-points"
private const val LAYER_POINTS_SELECTED = "parking-points-selected"

/** Базовая карта города, если она положена в assets/basemap (см. docs/offline-map.md). */
fun hasBundledBasemap(context: Context): Boolean =
    context.assets.list("basemap")?.contains("style.json") == true

/**
 * Без базовой карты показываем только парковки на нейтральном фоне. Никаких
 * обращений к сторонним онлайн-картам.
 */
private const val FALLBACK_STYLE = """{"version":8,"sources":{},"layers":[{"id":"bg","type":"background","paint":{"background-color":"#EEF1F5"}}]}"""

@Composable
fun ParkingMap(
    parkings: List<Parking>,
    selected: Parking?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val onSelectState by rememberUpdatedState(onSelect)
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }

    val mapView = remember {
        MapView(context).also { view ->
            view.onCreate(null)
            view.getMapAsync { m ->
                m.uiSettings.isRotateGesturesEnabled = false
                m.uiSettings.isAttributionEnabled = true
                m.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(Geo.KRASNODAR_CENTER.lat, Geo.KRASNODAR_CENTER.lon))
                    .zoom(12.5)
                    .build()
                // Не уводим камеру далеко от города.
                m.setLatLngBoundsForCameraTarget(LatLngBounds.from(45.25, 39.30, 44.90, 38.70))
                m.setMinZoomPreference(10.0)
                val builder = if (hasBundledBasemap(context)) {
                    Style.Builder().fromJson(context.assets.open("basemap/style.json").bufferedReader().use { it.readText() })
                } else {
                    Style.Builder().fromJson(FALLBACK_STYLE)
                }
                m.setStyle(builder) { s ->
                    addParkingLayers(s)
                    style = s
                }
                m.addOnMapClickListener { latLng ->
                    val p = m.projection.toScreenLocation(latLng)
                    val r = 24f * context.resources.displayMetrics.density
                    val hit = m.queryRenderedFeatures(RectF(p.x - r, p.y - r, p.x + r, p.y + r), LAYER_LINES, LAYER_POINTS)
                        .firstOrNull()
                    onSelectState(hit?.getStringProperty("id"))
                    true
                }
                map = m
            }
        }
    }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    LaunchedEffect(style, parkings) {
        val s = style ?: return@LaunchedEffect
        val (lines, points) = toFeatures(parkings)
        s.getSourceAs<GeoJsonSource>(SRC_LINES)?.setGeoJson(lines)
        s.getSourceAs<GeoJsonSource>(SRC_POINTS)?.setGeoJson(points)
    }

    LaunchedEffect(style, selected?.id) {
        val s = style ?: return@LaunchedEffect
        val filter = Expression.eq(Expression.get("id"), Expression.literal(selected?.id ?: ""))
        (s.getLayer(LAYER_LINES_SELECTED) as? LineLayer)?.setFilter(filter)
        (s.getLayer(LAYER_POINTS_SELECTED) as? CircleLayer)?.setFilter(filter)
        val anchor = selected?.let(Geo::anchor) ?: return@LaunchedEffect
        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(anchor.lat, anchor.lon), 16.0), 250)
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

private fun addParkingLayers(s: Style) {
    s.addSource(GeoJsonSource(SRC_LINES, FeatureCollection.fromFeatures(emptyList())))
    s.addSource(GeoJsonSource(SRC_POINTS, FeatureCollection.fromFeatures(emptyList())))
    val inactive = Brand.SegmentInactive.toArgb()
    val active = Brand.Blue.toArgb()
    s.addLayer(
        LineLayer(LAYER_LINES, SRC_LINES).withProperties(
            PropertyFactory.lineColor(inactive),
            PropertyFactory.lineWidth(Expression.interpolate(Expression.linear(), Expression.zoom(), Expression.stop(12, 2f), Expression.stop(17, 6f))),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
        ),
    )
    s.addLayer(
        CircleLayer(LAYER_POINTS, SRC_POINTS).withProperties(
            PropertyFactory.circleColor(inactive),
            PropertyFactory.circleRadius(Expression.interpolate(Expression.linear(), Expression.zoom(), Expression.stop(12, 3f), Expression.stop(17, 8f))),
            PropertyFactory.circleStrokeColor(android.graphics.Color.WHITE),
            PropertyFactory.circleStrokeWidth(1.5f),
        ),
    )
    s.addLayer(
        LineLayer(LAYER_LINES_SELECTED, SRC_LINES).withProperties(
            PropertyFactory.lineColor(active),
            PropertyFactory.lineWidth(Expression.interpolate(Expression.linear(), Expression.zoom(), Expression.stop(12, 4f), Expression.stop(17, 9f))),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
        ).withFilter(Expression.eq(Expression.get("id"), Expression.literal(""))),
    )
    s.addLayer(
        CircleLayer(LAYER_POINTS_SELECTED, SRC_POINTS).withProperties(
            PropertyFactory.circleColor(active),
            PropertyFactory.circleRadius(10f),
            PropertyFactory.circleStrokeColor(android.graphics.Color.WHITE),
            PropertyFactory.circleStrokeWidth(2f),
        ).withFilter(Expression.eq(Expression.get("id"), Expression.literal(""))),
    )
}

/**
 * Линии — только те участки, что пришли из источника. Парковки, у которых есть
 * лишь точка, рисуем точкой; без координат — не рисуем вовсе.
 */
private fun toFeatures(parkings: List<Parking>): Pair<FeatureCollection, FeatureCollection> {
    val lines = mutableListOf<Feature>()
    val points = mutableListOf<Feature>()
    for (p in parkings) {
        when (p.geometryStatus) {
            GeometryStatus.SEGMENTS -> {
                val ls = p.segments.filter { it.size == 2 }.map { seg ->
                    LineString.fromLngLats(seg.map { Point.fromLngLat(it.lon, it.lat) })
                }
                lines += Feature.fromGeometry(MultiLineString.fromLineStrings(ls)).also { it.addStringProperty("id", p.id) }
            }
            GeometryStatus.POINT_ONLY -> {
                val loc = p.location ?: continue
                points += Feature.fromGeometry(Point.fromLngLat(loc.lon, loc.lat)).also { it.addStringProperty("id", p.id) }
            }
            GeometryStatus.NONE -> Unit
        }
    }
    return FeatureCollection.fromFeatures(lines) to FeatureCollection.fromFeatures(points)
}
