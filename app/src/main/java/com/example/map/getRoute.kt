package com.example.map

import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.*
import org.json.JSONObject
import vn.vietmap.vietmapsdk.geometry.LatLng
import vn.vietmap.vietmapsdk.maps.VietMapGL
import vn.vietmap.vietmapsdk.style.layers.LineLayer
import vn.vietmap.vietmapsdk.style.layers.PropertyFactory
import vn.vietmap.vietmapsdk.style.sources.GeoJsonSource
import com.mapbox.geojson.*
import vn.vietmap.vietmapsdk.camera.CameraUpdateFactory
import java.io.IOException

object getRoute {
    private val client = OkHttpClient()
    private val uiHandler = Handler(Looper.getMainLooper())
    private val tomTomKey = "5Kcc0veRP7M6siOT2Pna86xXBkoDuUSx"

    fun fetchRoute(vietMapGL: VietMapGL, from: String, to: String, vehicle: String) {
        val urlFetchRoute =
            "https://maps.vietmap.vn/api/route?api-version=1.1&apikey=$ApiKey&point=$from&point=$to&vehicle=$vehicle"

        val request = Request.Builder().url(urlFetchRoute).get().build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("fetchRoute", "Lỗi lấy route: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        Log.e("fetchRoute", "Lỗi: ${response.code}")
                        return
                    }

                    response.body?.string()?.let { responseBody ->
                        try {
                            val json = JSONObject(responseBody)
                            val encodedPolyline = json.optJSONArray("paths")
                                ?.optJSONObject(0)
                                ?.optString("points", "") ?: ""

                            if (encodedPolyline.isNotEmpty()) {
                                val routePoints = decodePolyline(encodedPolyline)
                                uiHandler.post {
                                    drawRoute(vietMapGL, routePoints)  // Vẽ tuyến đường
                                    checkTrafficWithTomTom(routePoints, vietMapGL)
                                }
                            } else {
                                Log.e("fetchRoute", "Không tìm thấy tuyến đường")
                            }
                        } catch (e: Exception) {
                            Log.e("fetchRoute", "Lỗi xử lý JSON: ${e.message}")
                        }
                    }
                }
            }
        })
    }

    private fun drawRoute(vietMapGL: VietMapGL, routePoints: List<LatLng>) {
        vietMapGL.getStyle { style ->
            val sourceId = "route-source"
            val layerId = "route-layer"

            val lineString = LineString.fromLngLats(routePoints.map {
                Point.fromLngLat(it.longitude, it.latitude)
            })

            var routeSource = style.getSourceAs<GeoJsonSource>(sourceId)
            if (routeSource == null) {
                routeSource = GeoJsonSource(sourceId, FeatureCollection.fromFeature(Feature.fromGeometry(lineString)))
                style.addSource(routeSource)
            } else {
                routeSource.setGeoJson(FeatureCollection.fromFeature(Feature.fromGeometry(lineString)))
            }

            if (style.getLayer(layerId) == null) {
                val routeLayer = LineLayer(layerId, sourceId).withProperties(
                    PropertyFactory.lineColor("#007AFF"),
                    PropertyFactory.lineWidth(5f)
                )
                style.addLayer(routeLayer)
            }
        }
    }


    private fun decodePolyline(encoded: String): List<LatLng> {
        val poly = mutableListOf<LatLng>()
        var index = 0
        var lat = 0
        var lng = 0

        while (index < encoded.length) {
            fun decode(): Int {
                var shift = 0
                var result = 0
                var b: Int
                do {
                    b = encoded[index++].code - 63
                    result = result or ((b and 0x1F) shl shift)
                    shift += 5
                } while (b >= 0x20)
                return if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
            }

            lat += decode()
            lng += decode()
            poly.add(LatLng(lat / 1E5, lng / 1E5))
        }
        return poly
    }

    private fun checkTrafficWithTomTom(routePoints: List<LatLng>, vietMapGL: VietMapGL) {
        for (i in 0 until routePoints.size - 1) {
            val start = routePoints[i]
            val end = routePoints[i + 1]

            val url = "https://api.tomtom.com/routing/1/calculateRoute/" +
                    "${start.latitude},${start.longitude}:${end.latitude},${end.longitude}/json" +
                    "?traffic=true&key=$tomTomKey"

            val request = Request.Builder().url(url).get().build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e("TomTom", "Failed to fetch traffic: ${e.message}")
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (!response.isSuccessful) {
                            Log.e("TomTom", "HTTP ${response.code}")
                            return
                        }

                        val body = response.body?.string() ?: return
                        val json = JSONObject(body)
                        val routes = json.optJSONArray("routes") ?: return
                        val summary = routes.optJSONObject(0)?.optJSONObject("summary") ?: return
                        val delay = summary.optInt("trafficDelayInSeconds", 0)

                        val isCongested = delay > 20  // Delay > 20s thì coi là kẹt xe

                        uiHandler.post {
                            drawTrafficSegment(vietMapGL, start, end, isCongested)
                        }
                    }
                }
            })
        }
    }

    private fun drawTrafficSegment(vietMapGL: VietMapGL, start: LatLng, end: LatLng, isCongested: Boolean) {
        vietMapGL.getStyle { style ->
            val id = "segment-${start.latitude}-${start.longitude}-${end.latitude}-${end.longitude}"

            val lineString = LineString.fromLngLats(
                listOf(
                    Point.fromLngLat(start.longitude, start.latitude),
                    Point.fromLngLat(end.longitude, end.latitude)
                )
            )

            val source = GeoJsonSource(id, FeatureCollection.fromFeature(Feature.fromGeometry(lineString)))
            style.addSource(source)

            val layer = LineLayer(id, id).withProperties(
                PropertyFactory.lineColor(if (isCongested) "#FF0000" else "#FFFF00"),
                PropertyFactory.lineWidth(5f)
            )
            style.addLayer(layer)
        }
    }

}
