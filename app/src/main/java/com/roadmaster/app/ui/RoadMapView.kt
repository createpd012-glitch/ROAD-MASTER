package com.roadmaster.app.ui

import android.content.Context
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

class RoadMapView private constructor(context: Context, initialized: Unit) : MapView(context) {
    private var map: MapLibreMap? = null
    private var routeSource: GeoJsonSource? = null
    private var currentLocationSource: GeoJsonSource? = null
    private var lastCameraPointCount = 0
    private var pendingRoute: List<TrackPoint> = emptyList()
    private var pendingCurrentLocation: TrackPoint? = null

    init {
        onCreate(null)
        getMapAsync { mapLibreMap ->
            map = mapLibreMap
            mapLibreMap.cameraPosition = CameraPosition.Builder().target(LatLng(36.35, 127.85)).zoom(6.2).build()
            mapLibreMap.setStyle(Style.Builder().fromJson(OSM_RASTER_STYLE)) { style ->
                val route = GeoJsonSource(ROUTE_SOURCE_ID, emptyLine())
                style.addSource(route)
                style.addLayer(LineLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID).withProperties(lineColor("#2389FF"), lineWidth(5.0f)))
                routeSource = route

                val locationSource = GeoJsonSource(LOCATION_SOURCE_ID, emptyPoint())
                style.addSource(locationSource)
                style.addLayer(
                    CircleLayer(LOCATION_LAYER_ID, LOCATION_SOURCE_ID).withProperties(
                        circleColor("#E94735"),
                        circleRadius(10.0f),
                        circleStrokeColor("#FFFFFF"),
                        circleStrokeWidth(3.5f)
                    )
                )
                currentLocationSource = locationSource
                updateRoute(pendingRoute)
                pendingCurrentLocation?.let(::setCurrentLocationMarker)
            }
        }
    }

    fun setRoute(points: List<TrackPoint>) {
        pendingRoute = points
        updateRoute(points)
    }

    fun setCurrentLocationMarker(point: TrackPoint) {
        pendingCurrentLocation = point
        currentLocationSource?.setGeoJson(Point.fromLngLat(point.longitude, point.latitude))
    }

    fun moveToCurrentLocation(point: TrackPoint) {
        setCurrentLocationMarker(point)
        map?.animateCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(point.latitude, point.longitude), 16.0),
            650
        )
    }

    private fun updateRoute(points: List<TrackPoint>) {
        val source = routeSource ?: return
        if (points.size < 2) return
        source.setGeoJson(LineString.fromLngLats(points.map { Point.fromLngLat(it.longitude, it.latitude) }))
        if (points.size > lastCameraPointCount) {
            val latest = points.last()
            map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(latest.latitude, latest.longitude), 15.0), 500)
            lastCameraPointCount = points.size
        }
    }

    private fun emptyLine() = LineString.fromLngLats(listOf(Point.fromLngLat(127.85, 36.35), Point.fromLngLat(127.851, 36.351)))
    private fun emptyPoint() = Point.fromLngLat(127.85, 36.35)

    companion object {
        fun create(context: Context): RoadMapView {
            MapLibre.getInstance(context)
            return RoadMapView(context, Unit)
        }

        const val OSM_ATTRIBUTION = "© OpenStreetMap contributors"
        private const val ROUTE_SOURCE_ID = "roadmaster-route-source"
        private const val ROUTE_LAYER_ID = "roadmaster-route-layer"
        private const val LOCATION_SOURCE_ID = "roadmaster-current-location-source"
        private const val LOCATION_LAYER_ID = "roadmaster-current-location-layer"
        private val OSM_RASTER_STYLE = """
        {"version":8,"name":"ROAD MASTER OSM Demo","sources":{"osm-raster":{"type":"raster","tiles":["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],"tileSize":256,"attribution":"© OpenStreetMap contributors"}},"layers":[{"id":"osm-background","type":"background","paint":{"background-color":"#dce7e5"}},{"id":"osm-raster-layer","type":"raster","source":"osm-raster","paint":{"raster-opacity":1.0}}]}
        """.trimIndent()
    }
}

data class TrackPoint(val latitude: Double, val longitude: Double, val accuracyMeters: Float, val recordedAtMillis: Long)
