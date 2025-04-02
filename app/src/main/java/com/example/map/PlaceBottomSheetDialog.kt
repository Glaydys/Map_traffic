package com.example.map

import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import vn.vietmap.vietmapsdk.annotations.IconFactory
import vn.vietmap.vietmapsdk.annotations.Marker
import vn.vietmap.vietmapsdk.annotations.MarkerOptions
import vn.vietmap.vietmapsdk.camera.CameraPosition
import vn.vietmap.vietmapsdk.camera.CameraUpdateFactory
import vn.vietmap.vietmapsdk.geometry.LatLng
import vn.vietmap.vietmapsdk.maps.VietMapGL
import kotlin.math.*

class PlaceBottomSheetDialog(
    private val name: String,
    private val address: String,
    private val vietMapGL: VietMapGL
) : BottomSheetDialogFragment() {

    private lateinit var sharedPreferences: android.content.SharedPreferences
    private var currentMarker: Marker? = null // Lưu trữ marker hiện tại

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_place, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val placeName = view.findViewById<TextView>(R.id.placeName)
        val placeAddress = view.findViewById<TextView>(R.id.placeAddress)
        val startButton = view.findViewById<Button>(R.id.btn_start)

        placeName.text = name
        placeAddress.text = address

        sharedPreferences = requireActivity().getSharedPreferences("my_prefs", Context.MODE_PRIVATE)
        val startLat = sharedPreferences.getString("startLat", null)?.toDoubleOrNull()
        val startLng = sharedPreferences.getString("startLng", null)?.toDoubleOrNull()
        val destinationLat = sharedPreferences.getString("destinationLat", null)?.toDoubleOrNull()
        val destinationLng = sharedPreferences.getString("destinationLng", null)?.toDoubleOrNull()

        startButton.setOnClickListener {
            startNavigation()
            getRoute.fetchRoute(
                vietMapGL,
                "$startLat,$startLng",
                "$destinationLat,$destinationLng",
                "motorcycle")
        }
    }

    private fun startNavigation() {
        val startLat = sharedPreferences.getString("startLat", null)?.toDoubleOrNull()
        val startLng = sharedPreferences.getString("startLng", null)?.toDoubleOrNull()
        val destinationLat = sharedPreferences.getString("destinationLat", null)?.toDoubleOrNull()
        val destinationLng = sharedPreferences.getString("destinationLng", null)?.toDoubleOrNull()

        if (startLat != null && startLng != null && destinationLat != null && destinationLng != null) {
            val startLatLng = LatLng(startLat, startLng)
            val bearingAngle = bearing(startLat, startLng, destinationLat, destinationLng)

            vietMapGL.animateCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(startLatLng)
                        .zoom(20.0)
                        .bearing(bearingAngle.toDouble())
                        .build()
                ), 3000
            )

            drawArrow(startLatLng, bearingAngle)
            dismiss()
        }
    }

    private fun drawArrow(position: LatLng, bearing: Float) {
        clearMap()
        val iconFactory = IconFactory.getInstance(requireContext())
        val originalBitmap = BitmapFactory.decodeResource(resources, R.drawable.arrow)
        val rotatedBitmap = rotateBitmap(originalBitmap, bearing)
        val icon = iconFactory.fromBitmap(rotatedBitmap)

        val markerOptions = MarkerOptions()
            .position(position)
            .icon(icon)

        currentMarker = vietMapGL.addMarker(markerOptions)

        if (currentMarker != null) {
            Log.d("PlaceBottomSheetDialog", "Đã thêm arrow marker tại: $position, Góc: $bearing")
        } else {
            Log.e("PlaceBottomSheetDialog", "Không thể thêm marker")
        }
    }

    private fun rotateBitmap(source: Bitmap, angle: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(angle)
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun clearMap() {
        currentMarker?.remove()
        currentMarker = null
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            setCancelable(true)
            setCanceledOnTouchOutside(true)
            window?.setDimAmount(0f)
            window?.setBackgroundDrawableResource(android.R.color.transparent)
        }
    }

    private fun bearing(startLat: Double, startLng: Double, endLat: Double, endLng: Double): Float {
        val startLatRad = Math.toRadians(startLat)
        val startLngRad = Math.toRadians(startLng)
        val endLatRad = Math.toRadians(endLat)
        val endLngRad = Math.toRadians(endLng)

        val y = sin(endLngRad - startLngRad) * cos(endLatRad)
        val x = cos(startLatRad) * sin(endLatRad) -
                sin(startLatRad) * cos(endLatRad) * cos(endLngRad - startLngRad)
        val bearingRad = atan2(y, x)
        var bearingDegrees = Math.toDegrees(bearingRad)
        if (bearingDegrees < 0) {
            bearingDegrees += 360
        }
        return ((bearingDegrees + 90) % 360).toFloat()
    }
}
