import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.map.ApiKey
import okhttp3.*
import org.json.JSONObject
import vn.vietmap.vietmapsdk.geometry.LatLng
import vn.vietmap.vietmapsdk.maps.VietMapGL
import vn.vietmap.vietmapsdk.style.layers.LineLayer
import vn.vietmap.vietmapsdk.style.layers.PropertyFactory
import vn.vietmap.vietmapsdk.style.sources.GeoJsonSource
import com.mapbox.geojson.*
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
                                uiHandler.post { drawRoute(vietMapGL, routePoints) } // Chạy trên UI Thread
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

            val routeSource = style.getSourceAs<GeoJsonSource>(sourceId)
                ?: GeoJsonSource(sourceId, FeatureCollection.fromFeature(Feature.fromGeometry(lineString)))
                    .also { style.addSource(it) }

            routeSource.setGeoJson(FeatureCollection.fromFeature(Feature.fromGeometry(lineString)))

            if (style.getLayer(layerId) == null) {
                val routeLayer = LineLayer(layerId, sourceId).withProperties(
                    PropertyFactory.lineColor("#007AFF"),  // Màu xanh dương
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
}
