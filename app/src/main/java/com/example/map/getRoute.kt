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
                                    animateRoute(vietMapGL, routePoints) // Bắt đầu di chuyển
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

    // ⚡ Thêm hàm mô phỏng di chuyển theo tuyến đường
    private fun animateRoute(vietMapGL: VietMapGL, routePoints: List<LatLng>, index: Int = 0) {
        if (index >= routePoints.size) return  // Dừng khi đến điểm cuối

        val nextPosition = routePoints[index]

        // Di chuyển camera tới vị trí tiếp theo
        vietMapGL.animateCamera(
            CameraUpdateFactory.newLatLng(nextPosition),
            2000 // Thời gian di chuyển giữa các điểm (đơn vị: milliseconds)
        )

        // Xóa phần tuyến đường đã đi (từ điểm đầu đến điểm hiện tại)
        vietMapGL.getStyle { style ->
            val sourceId = "route-source"
            val lineString = LineString.fromLngLats(routePoints.subList(index, routePoints.size).map {
                Point.fromLngLat(it.longitude, it.latitude)
            })

            // Lấy hoặc tạo GeoJsonSource mới
            val routeSource = style.getSourceAs<GeoJsonSource>(sourceId)
                ?: GeoJsonSource(sourceId, FeatureCollection.fromFeature(Feature.fromGeometry(lineString)))
                    .also { style.addSource(it) }

            // Cập nhật lại GeoJsonSource với tuyến đường mới
            routeSource.setGeoJson(FeatureCollection.fromFeature(Feature.fromGeometry(lineString)))

            // Kiểm tra và thêm layer nếu chưa có
            if (style.getLayer("route-layer") == null) {
                val routeLayer = LineLayer("route-layer", sourceId).withProperties(
                    PropertyFactory.lineColor("#007AFF"),  // Màu xanh dương
                    PropertyFactory.lineWidth(5f)
                )
                style.addLayer(routeLayer)
            }
        }

        // Gọi tiếp tục di chuyển sau 2 giây
        Handler(Looper.getMainLooper()).postDelayed({
            animateRoute(vietMapGL, routePoints, index + 1)
        }, 2000)
    }
}
