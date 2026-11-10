package com.roadmaster.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconAnchor
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconSize
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import com.roadmaster.app.data.Scoring

/**
 * Korea-centered MapLibre map.
 * The current blue route is drawn from live GPS fixes. Visited-road color tiers can be
 * supplied later from the trusted map-matching backend through setVisitedRoads().
 */
class RoadMapView private constructor(context: Context, initialized: Unit) : MapView(context) {
    private var map: MapLibreMap? = null
    private var routeSource: GeoJsonSource? = null
    private var currentLocationSource: GeoJsonSource? = null
    private val visitedRoadSources = mutableMapOf<Int, GeoJsonSource>()
    private var lastCameraPointCount = 0
    private var pendingRoute: List<TrackPoint> = emptyList()
    private var pendingVisitedRoads: List<VisitedRoadGeometry> = emptyList()
    private var pendingCurrentLocation: TrackPoint? = null

    init {
        onCreate(null)
        getMapAsync { mapLibreMap ->
            map = mapLibreMap
            mapLibreMap.cameraPosition = CameraPosition.Builder()
                .target(LatLng(KOREA_CENTER_LATITUDE, KOREA_CENTER_LONGITUDE))
                .zoom(KOREA_OVERVIEW_ZOOM)
                .build()

            mapLibreMap.setStyle(Style.Builder().fromJson(OSM_RASTER_STYLE)) { style ->
                visitedRoadSources.clear()
                Scoring.colors.forEachIndexed { index, color ->
                    val visitTier = index + 1
                    val sourceId = "roadmaster-visited-source-$visitTier"
                    val layerId = "roadmaster-visited-layer-$visitTier"
                    val source = GeoJsonSource(sourceId, FeatureCollection.fromFeatures(emptyArray()))
                    style.addSource(source)
                    style.addLayer(
                        LineLayer(layerId, sourceId).withProperties(
                            lineColor(color),
                            lineWidth(7.0f)
                        )
                    )
                    visitedRoadSources[visitTier] = source
                }

                val route = GeoJsonSource(ROUTE_SOURCE_ID, emptyLine())
                style.addSource(route)
                style.addLayer(
                    LineLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID).withProperties(
                        lineColor("#2389FF"),
                        lineWidth(5.0f)
                    )
                )
                routeSource = route

                val locationSource = GeoJsonSource(
                    LOCATION_SOURCE_ID,
                    FeatureCollection.fromFeatures(emptyArray())
                )
                style.addSource(locationSource)
                style.addImage(LOCATION_ICON_ID, createCuteLocationPin())
                style.addLayer(
                    SymbolLayer(LOCATION_LAYER_ID, LOCATION_SOURCE_ID).withProperties(
                        iconImage(LOCATION_ICON_ID),
                        iconSize(0.72f),
                        iconAnchor("bottom"),
                        iconAllowOverlap(true),
                        iconIgnorePlacement(true)
                    )
                )
                currentLocationSource = locationSource

                updateVisitedRoads(pendingVisitedRoads)
                updateRoute(pendingRoute)
                pendingCurrentLocation?.let(::setCurrentLocationMarker)
            }
        }
    }

    fun setRoute(points: List<TrackPoint>) {
        pendingRoute = points
        updateRoute(points)
    }

    /**
     * Draws backend-confirmed road geometries in one of five visit-count colors.
     * A visit count of five or more uses the final purple tier.
     */
    fun setVisitedRoads(roads: List<VisitedRoadGeometry>) {
        pendingVisitedRoads = roads
        updateVisitedRoads(roads)
    }

    fun setCurrentLocationMarker(point: TrackPoint) {
        pendingCurrentLocation = point
        currentLocationSource?.setGeoJson(Point.fromLngLat(point.longitude, point.latitude))
    }

    fun moveToCurrentLocation(point: TrackPoint) {
        setCurrentLocationMarker(point)
        map?.animateCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(point.latitude, point.longitude), CURRENT_LOCATION_ZOOM),
            650
        )
    }

    private fun updateVisitedRoads(roads: List<VisitedRoadGeometry>) {
        if (visitedRoadSources.isEmpty()) return

        for (tier in 1..Scoring.colors.size) {
            val features = roads
                .filter { it.visitCount.coerceIn(1, Scoring.colors.size) == tier && it.coordinates.size >= 2 }
                .map { road ->
                    Feature.fromGeometry(
                        LineString.fromLngLats(
                            road.coordinates.map { Point.fromLngLat(it.longitude, it.latitude) }
                        )
                    )
                }
            visitedRoadSources[tier]?.setGeoJson(FeatureCollection.fromFeatures(features.toTypedArray()))
        }
    }

    private fun updateRoute(points: List<TrackPoint>) {
        val source = routeSource ?: return
        if (points.size < 2) {
            source.setGeoJson(emptyLine())
            lastCameraPointCount = 0
            return
        }

        source.setGeoJson(
            LineString.fromLngLats(points.map { Point.fromLngLat(it.longitude, it.latitude) })
        )
        if (points.size > lastCameraPointCount) {
            val latest = points.last()
            map?.animateCamera(
                CameraUpdateFactory.newLatLngZoom(LatLng(latest.latitude, latest.longitude), TRACKING_ZOOM),
                500
            )
            lastCameraPointCount = points.size
        }
    }

    private fun createCuteLocationPin(): Bitmap {
        val bitmap = Bitmap.createBitmap(96, 120, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(39, 177, 224)
            style = Paint.Style.FILL
        }
        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 7f
        }
        val pin = Path().apply {
            moveTo(48f, 115f)
            cubicTo(41f, 101f, 12f, 72f, 12f, 45f)
            cubicTo(12f, 20f, 28f, 6f, 48f, 6f)
            cubicTo(68f, 6f, 84f, 20f, 84f, 45f)
            cubicTo(84f, 72f, 55f, 101f, 48f, 115f)
            close()
        }
        canvas.drawPath(pin, fill)
        canvas.drawPath(pin, outline)

        val face = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(225, 250, 255)
            style = Paint.Style.FILL
        }
        canvas.drawCircle(48f, 45f, 27f, face)

        val eyes = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(16, 61, 91)
            style = Paint.Style.FILL
        }
        canvas.drawCircle(39f, 42f, 3.8f, eyes)
        canvas.drawCircle(57f, 42f, 3.8f, eyes)

        val smile = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(16, 61, 91)
            style = Paint.Style.STROKE
            strokeWidth = 3.5f
            strokeCap = Paint.Cap.ROUND
        }
        val smilePath = Path().apply {
            moveTo(39f, 54f)
            quadTo(48f, 63f, 57f, 54f)
        }
        canvas.drawPath(smilePath, smile)

        val shine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            alpha = 210
        }
        canvas.drawCircle(29f, 25f, 5f, shine)
        return bitmap
    }

    private fun emptyLine() = LineString.fromLngLats(
        listOf(
            Point.fromLngLat(127.85, 36.35),
            Point.fromLngLat(127.851, 36.351)
        )
    )

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
        private const val LOCATION_ICON_ID = "roadmaster-cute-location-pin"

        // A broad South Korea overview. The first accepted GPS fix moves the camera to the user.
        private const val KOREA_CENTER_LATITUDE = 36.35
        private const val KOREA_CENTER_LONGITUDE = 127.85
        private const val KOREA_OVERVIEW_ZOOM = 6.2
        private const val TRACKING_ZOOM = 15.0
        private const val CURRENT_LOCATION_ZOOM = 16.0

        // Development/demo tiles only. Select a licensed production tile provider before launch.
        private val OSM_RASTER_STYLE = """
        {
          "version": 8,
          "name": "ROAD MASTER Korea Demo",
          "sources": {
            "osm-raster": {
              "type": "raster",
              "tiles": ["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],
              "tileSize": 256,
              "attribution": "© OpenStreetMap contributors"
            }
          },
          "layers": [
            {"id": "osm-background", "type": "background", "paint": {"background-color": "#dce7e5"}},
            {"id": "osm-raster-layer", "type": "raster", "source": "osm-raster", "paint": {"raster-opacity": 1.0}}
          ]
        }
        """.trimIndent()
    }
}

data class TrackPoint(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val recordedAtMillis: Long
)

/** Geometry returned by the backend for a road segment confirmed as visited. */
data class VisitedRoadGeometry(
    val segmentId: String,
    val visitCount: Int,
    val coordinates: List<TrackPoint>
)
