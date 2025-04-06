package com.example.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.*
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import okhttp3.OkHttpClient
import vn.vietmap.vietmapsdk.Vietmap
import vn.vietmap.vietmapsdk.geometry.LatLng
import vn.vietmap.vietmapsdk.maps.MapView
import vn.vietmap.vietmapsdk.maps.Style
import vn.vietmap.vietmapsdk.maps.VietMapGL
import java.io.IOException
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import vn.vietmap.vietmapsdk.annotations.IconFactory
import android.speech.tts.TextToSpeech
import java.util.Locale
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import com.example.map.Constants.LABELS_PATH
import com.example.map.Constants.MODEL_PATH
import kotlinx.coroutines.*
import vn.vietmap.vietmapsdk.annotations.MarkerOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sqrt


class MainActivity : AppCompatActivity(), Detector.DetectorListener {
    private lateinit var mapView: MapView
    private lateinit var vietMapGL: VietMapGL
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentMarker: vn.vietmap.vietmapsdk.annotations.Marker? = null
    private var startMarker: vn.vietmap.vietmapsdk.annotations.Marker? = null
    private var destinationMarker: vn.vietmap.vietmapsdk.annotations.Marker? = null
    private lateinit var editTextStart: EditText
    private lateinit var editTextDestination: EditText
    private lateinit var recyclerView: RecyclerView
    private var placeList = mutableListOf<Place>()
    private var filteredList = mutableListOf<Place>()
    private lateinit var adapter: PlaceAdapter
    private val searchHandler = android.os.Handler()
    private var searchRunnable: Runnable? = null
    private val apiKey = "$ApiKey"
    private val client = OkHttpClient()
    private val markerAddressMap = mutableMapOf<vn.vietmap.vietmapsdk.annotations.Marker, String>()
    private lateinit var speechToText: SpeechToText
    private var isSettingStartPoint = true
    private var lastRecognizedStartLocation: Place? = null
    private var lastRecognizedDestinationLocation: Place? = null
    private lateinit var textToSpeech: TextToSpeech
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private val SAMPLE_RATE = 44100.0 // Ép kiểu thành Double
    private val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    private var bufferSize = 0
    private val NOISE_THRESHOLD = 300
    private val HIGH_PASS_FILTER_FREQUENCY = 100.0

    lateinit var userLocation: String
    val drawnMarkers = mutableSetOf<String>() // Chứa danh sách các biển báo đã hiển thị
    private var imageAnalyzer: ImageAnalysis? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var detector: Detector? = null
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var alertImageView: LinearLayout

    private var v1 = 0.0
    private var v2 = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        Vietmap.getInstance(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 123)
        } else {
            initAudioRecorder()
        }
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions(REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
        mapView = findViewById(R.id.MapView)
        mapView.onCreate(savedInstanceState)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Load bản đồ
        mapView.getMapAsync { map ->
            vietMapGL = map
            vietMapGL.setStyle(
                Style.Builder()
                    .fromUri("https://maps.vietmap.vn/api/maps/light/styles.json?apikey=$apiKey")
            ) {
                vietMapGL.uiSettings.isCompassEnabled = true // Hiển thị la bàn
                vietMapGL.uiSettings.isZoomGesturesEnabled = true // Cho phép zoom
                enableUserLocation()
            }
        }

        editTextStart = findViewById(R.id.editTextStart)
        editTextDestination = findViewById(R.id.editTextDestination)

        editTextStart.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(
                newText: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) {
                recyclerView.visibility = View.VISIBLE
                searchRunnable?.let { searchHandler.removeCallbacks(it) }
                searchRunnable = Runnable {
                    val text = newText?.toString() ?: ""
                    if (text.isEmpty()) {
                        filteredList.clear()
                        adapter.notifyDataSetChanged()
                    } else {
                        getAutoComplete(text)
                    }
                }
                searchHandler.postDelayed(searchRunnable!!, 500)
            }
            override fun afterTextChanged(editable: Editable?) {}
        })

        editTextDestination.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(
                newText: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) {
                recyclerView.visibility = View.VISIBLE
                searchRunnable?.let { searchHandler.removeCallbacks(it) }
                searchRunnable = Runnable {
                    val text = newText?.toString() ?: ""
                    if (text.isEmpty()) {
                        filteredList.clear()
                        adapter.notifyDataSetChanged()
                    } else {
                        getAutoComplete(text)
                    }
                }
                searchHandler.postDelayed(searchRunnable!!, 500) // Delay 500ms trước khi gọi API
            }
            override fun afterTextChanged(editable: Editable?) {}
        })
//        val speechLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
//            if (result.resultCode == RESULT_OK) {
//                val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
//                matches?.let {
//                    if (it.isNotEmpty()) {
//                        val spokenText = it[0]
//                        if (isSettingStartPoint) {
//                            editTextStart.setText(spokenText)
//                            getAutoCompleteForVoiceStart(spokenText) // Gọi hàm tìm kiếm địa điểm bắt đầu bằng giọng nói
//                            isSettingStartPoint = false // Chuyển sang cài đặt điểm đến
//                        } else {
//                            editTextDestination.setText(spokenText)
//                            getAutoCompleteForVoiceDestination(spokenText) // Gọi hàm tìm kiếm địa điểm đến bằng giọng nói
//                        }
//                    }
//                }
//            } else {
//                Toast.makeText(this, "Không nhận diện được giọng nói!", Toast.LENGTH_SHORT).show()
//            }
//        }
//        speechToText = SpeechToText(this, speechLauncher)
//
//        // Khởi tạo TextToSpeech
//        textToSpeech = TextToSpeech(this) { status ->
//            if (status == TextToSpeech.SUCCESS) {
//                val localeVN = Locale("vi", "VN")
//                val result = textToSpeech.setLanguage(localeVN)
//
//                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
//                    Log.e("TextToSpeech", "Ngôn ngữ Vietnamese không được hỗ trợ hoặc thiếu data.")
//                    Toast.makeText(this, "TTS không hỗ trợ ngôn ngữ Vietnamese", Toast.LENGTH_SHORT).show()
//                } else {
//                    Log.d("TextToSpeech", "TTS đã sẵn sàng và hỗ trợ ngôn ngữ Vietnamese.")
//                    startVirtualAssistant()
//                }
//            } else {
//                Log.e("TextToSpeech", "Khởi tạo TextToSpeech thất bại, status: $status")
//                Toast.makeText(this, "TTS không hoạt động", Toast.LENGTH_SHORT).show()
//            }
//        }

        val assistantIcon: ImageView = findViewById(R.id.assistantIcon)
        assistantIcon.setOnClickListener {
            startVirtualAssistant()
        }

        recyclerView = findViewById(R.id.recyclerViewPlaces)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = PlaceAdapter(filteredList) { place ->
            val isStart = editTextStart.isFocused
            if (isStart) {
                editTextStart.setText(place.name)
                editTextStart.setSelection(place.name.length)
                editTextStart.clearFocus()
            } else {
                editTextDestination.setText(place.name)
                editTextDestination.setSelection(place.name.length)
                editTextDestination.setSelection(place.name.length)
                editTextDestination.clearFocus()
            }
            filteredList.clear()
            adapter.notifyDataSetChanged()
            getPlace(place.ref_id, isStart)
        }
        recyclerView.adapter = adapter

        cameraExecutor = Executors.newSingleThreadExecutor()
        detector = Detector(baseContext, MODEL_PATH, LABELS_PATH, object : Detector.DetectorListener {
            override fun onEmptyDetect() {}

            override fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long) {
                for (box in boundingBoxes) {
                    // Log.d("Detector", "Nhận diện: ${box.clsName} - Độ tin cậy: ${box.cnf}")
                    if (box.cnf > 0.7) {
                        val signType = box.clsName
                        val markerKey = signType
                        Log.d("Detector", "Nhận diện: $signType - Độ tin cậy: ${box.cnf}")
                        if (!drawnMarkers.contains(markerKey)) {    // Kiểm tra xem biển báo đã được vẽ chưa
                            showTrafficSignOnMap(signType)
                            speakTrafficSign(signType)
                            drawnMarkers.add(signType)
                        }
                    }
                }
            }
        })
    }

    fun showTrafficSignOnMap(signType: String) {
        runOnUiThread {
            val iconRes = when (signType) {
                "speed_limit_50" -> R.drawable.speed_limit_50
                "speed_limit_60" -> R.drawable.speed_limit_60
                "slow_down" -> R.drawable.slow_down
                "no_right_turn" -> R.drawable.no_right_turn
                else -> createTransparentBitmap() // Dùng bitmap trong suốt nếu không tìm thấy biển báo
            }

            // Hiển thị biển báo trong ImageView
            if (iconRes != createTransparentBitmap()) {
                val imageView = ImageView(this)
                imageView.setImageResource(iconRes as Int)
                // Thêm ImageView vào LinearLayout
                alertImageView.addView(imageView)
            }
        }
    }

    private fun speakTrafficSign(signType: String) {
        val textToSpeak = when (signType) {
            "speed_limit_50" -> "Giới hạn tốc độ 50 km/h"
            "speed_limit_60" -> "Giới hạn tốc độ 60 km/h"
            "slow_down" -> "Giảm tốc độ"
            "no_right_turn" -> "Cấm rẽ phải"
            else -> "Biển báo không xác định"
        }
        speakOut(textToSpeak)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 123 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initAudioRecorder()
        }
    }

    @SuppressLint("MissingPermission")
    private fun initAudioRecorder() {
        bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE.toInt(), CHANNEL_CONFIG, AUDIO_FORMAT)
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE.toInt(),
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )
        startRecording()
    }

    private fun startRecording() {
        audioRecord?.startRecording()
        isRecording = true
        CoroutineScope(Dispatchers.IO).launch {
            val buffer = ShortArray(bufferSize)
            while (isRecording) {
                val readResult = audioRecord?.read(buffer, 0, bufferSize) ?: 0
                if (readResult > 0) {
                    applyButterworthHighPassFilter(buffer, HIGH_PASS_FILTER_FREQUENCY)
                    val rms = calculateRMS(buffer)
                    if (rms > NOISE_THRESHOLD) {
                        Log.d("Audio", "RMS: $rms")
                    } else {
//                        Log.d("Audio", "Tiếng ồn bị bỏ qua")
                    }
                }
            }
            audioRecord?.stop()
            audioRecord?.release()
        }
    }

    private fun applyButterworthHighPassFilter(buffer: ShortArray, cutoffFrequency: Double) {
        val cutOff = cutoffFrequency / SAMPLE_RATE
        val a = exp(-2.0 * PI * cutOff)
        val k = (1 + a * a) / (1 - a)
        var v1Local = v1
        var v2Local = v2
        for (i in buffer.indices) {
            val input = buffer[i].toDouble()
            val v0 = (input + v2Local) / k
            val highPassOutput = v0 - v1Local + v2Local
            buffer[i] = highPassOutput.toInt().toShort()
            v2Local = v1Local
            v1Local = v0
        }
        v1 = v1Local
        v2 = v2Local
    }

    private fun calculateRMS(buffer: ShortArray): Double {
        var sum = 0.0
        for (element in buffer) {
            sum += element * element.toDouble()
        }
        return sqrt(sum / buffer.size)
    }

    private fun startVirtualAssistant() {
        promptForStartPoint()
    }

    private fun promptForStartPoint() {
        speakOut(" Bạn muốn bắt đầu từ đâu?")
        isSettingStartPoint = true
        Handler(Looper.getMainLooper()).postDelayed({
            speechToText.checkAndStartRecognition()
        }, 1500)
    }

    private fun promptForDestination() {
        speakOut(" Bạn muốn đi đến đâu?")
        isSettingStartPoint = false
        Handler(Looper.getMainLooper()).postDelayed({
            speechToText.checkAndStartRecognition()
        }, 1500)
    }

    private fun getPlace(ref_id: String, isStart: Boolean) {
        val url_getplace = "https://maps.vietmap.vn/api/place/v3?apikey=$apiKey&refid=$ref_id"
        val request = Request.Builder()
            .url(url_getplace)
            .get()
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("getPlace", "Lỗi lấy place: ${e.message}")
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Không thể lấy thông tin địa điểm", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    response.body?.let { responseBody ->
                        val json = JSONObject(responseBody.string())
                        if (json.has("lat") && json.has("lng")) {
                            val lat = json.getDouble("lat")
                            val lng = json.getDouble("lng")
                            val name = json.optString("name", "Không có tên")
                            val address = json.optString("address", "Không có địa chỉ")
                            Handler(Looper.getMainLooper()).post {
                                moveMarker(lat, lng, isStart)
                                val startLatLng = LatLng(lat, lng)
                                vietMapGL.moveCamera(
                                    vn.vietmap.vietmapsdk.camera.CameraUpdateFactory.newLatLngZoom(
                                        startLatLng, 15.0
                                    )
                                )
                                currentMarker = vietMapGL.addMarker(
                                    vn.vietmap.vietmapsdk.annotations.MarkerOptions()
                                        .position(startLatLng)
                                        .title(name)
                                )
                                currentMarker?.let { vietMapGL.removeMarker(it) } // Xóa marker cũ nếu có
                                currentMarker?.let { markerAddressMap[it] = address }
                                if (!isStart) {
                                    showPlaceDialog(name, address)
                                }
                                recyclerView.visibility = View.GONE
                                if (isStart && destinationMarker != null) {
                                    Toast.makeText(this@MainActivity, "Điểm bắt đầu đã được thay đổi.", Toast.LENGTH_SHORT).show()
                                } else if (!isStart && startMarker != null) {
                                    Toast.makeText(this@MainActivity, "Điểm đến đã được thay đổi.", Toast.LENGTH_SHORT).show()
                                } else if (startMarker != null && destinationMarker != null){
                                    Toast.makeText(this@MainActivity, "Điểm đi và điểm đến đã được chọn.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            Log.e("SearchResponse", "Không tìm thấy tọa độ trong response")
                        }
                    }
                }
            }
        })
    }

    private fun showPlaceDialog(name: String, address: String) {
        val bottomSheet = PlaceBottomSheetDialog(name, address, vietMapGL)
        bottomSheet.show(supportFragmentManager, "PlaceBottomSheet")
    }

    private fun getAutoComplete(query: String) {
        val url_searchRefid = "https://maps.vietmap.vn/api/autocomplete/v3?apikey=$apiKey&text=$query"
        val request = Request.Builder()
            .url(url_searchRefid)
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("AutoComplete", "Lỗi lấy AutoComplete: ${e.message}")
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Không thể lấy thông tin địa điểm", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onResponse(call: Call, response: Response) {
                Log.d("AutoComplete", "Response code: ${response.code}")
                if (response.isSuccessful) {
                    response.body?.let { responseBody ->
                        val responseString = responseBody.string()
                        val jsonArray = JSONArray(responseString) // Chuyển đổi sang JSON Array
                        placeList.clear()  // Xóa dữ liệu cũ
                        for (i in 0 until jsonArray.length()) {
                            val placeObj = jsonArray.getJSONObject(i)
                            val ref_id = placeObj.optString("ref_id","Không có refid")
                            val name = placeObj.optString("name", "Không xác định")
                            val address = placeObj.optString("address", "Không có địa chỉ")

                            placeList.add(Place(ref_id, name, address))
                        }

                        // Cập nhật danh sách trên UI Thread
                        runOnUiThread {
                            filteredList.clear()
                            filteredList.addAll(placeList)
                            adapter.notifyDataSetChanged()
                        }
                    }
                } else {
                    Log.e("AutoComplete", "Lỗi không phản hồi: ${response.message}")
                }
            }
        })
    }

    private fun getAutoCompleteForVoiceStart(query: String) {
        // Kiểm tra nếu câu truy vấn chứa lệnh "vị trí hiện tại"
        if (query.contains("vị trí hiện tại", ignoreCase = true) || query.contains("ở đây", ignoreCase = true)) {
            getCurrentLocationForStartPoint()
            return
        }
        val url_searchRefid = "https://maps.vietmap.vn/api/autocomplete/v3?apikey=$apiKey&text=$query"
        val request = Request.Builder()
            .url(url_searchRefid)
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("AutoComplete", "Lỗi lấy AutoComplete: ${e.message}")
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Không thể lấy thông tin địa điểm", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onResponse(call: Call, response: Response) {
                Log.d("AutoComplete", "Response code: ${response.code}")
                if (response.isSuccessful) {
                    response.body?.let { responseBody ->
                        val responseString = responseBody.string()
                        val jsonArray = JSONArray(responseString)
                        placeList.clear()
                        for (i in 0 until jsonArray.length()) {
                            val placeObj = jsonArray.getJSONObject(i)
                            val ref_id = placeObj.optString("ref_id","Không có refid")
                            val name = placeObj.optString("name", "Không xác định")
                            val address = placeObj.optString("address", "Không có địa chỉ")

                            placeList.add(Place(ref_id, name, address))
                        }
                        if (placeList.isNotEmpty()) {
                            lastRecognizedStartLocation = placeList.first() // Lưu địa điểm đầu tiên
                            val firstPlace = placeList.first()
                            runOnUiThread {
                                editTextStart.setText(firstPlace.name)
                                editTextStart.setSelection(firstPlace.name.length)
                                getPlace(firstPlace.ref_id, true)
                                promptForDestination() // Hỏi điểm đến sau khi đã có điểm đi
                            }
                        } else {
                            runOnUiThread {
                                speakOut("Không tìm thấy địa điểm!")
                                promptForStartPoint()
                            }
                        }
                    }
                } else {
                    Log.e("AutoComplete", "Lỗi không phản hồi: ${response.message}")
                }
            }
        })
    }

    //Hàm lấy vị trí hiện tại và đặt làm điểm đi
    @SuppressLint("MissingPermission")
    private fun getCurrentLocationForStartPoint() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            getUserLocation()
            return
        }
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                location?.let {
                    val latLng = LatLng(it.latitude, it.longitude)
                    runOnUiThread {
                        moveMarker(it.latitude, it.longitude, true) // Đặt vị trí làm điểm đi
                        vietMapGL.moveCamera(
                            vn.vietmap.vietmapsdk.camera.CameraUpdateFactory.newLatLngZoom(latLng, 15.0)
                        )
                        Toast.makeText(this@MainActivity, "Đã đặt vị trí hiện tại làm điểm đi.", Toast.LENGTH_SHORT).show()
                        promptForDestination() // Hỏi điểm đến
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("LocationError", "Lỗi khi lấy vị trí: ${e.message}")
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Không thể lấy vị trí hiện tại.", Toast.LENGTH_SHORT).show()
                    promptForStartPoint() // Hỏi lại điểm đi
                }
            }
    }

    private fun getAutoCompleteForVoiceDestination(query: String) {
        val url_searchRefid = "https://maps.vietmap.vn/api/autocomplete/v3?apikey=$apiKey&text=$query"
        val request = Request.Builder()
            .url(url_searchRefid)
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("AutoComplete", "Lỗi lấy AutoComplete: ${e.message}")
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Không thể lấy thông tin địa điểm", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onResponse(call: Call, response: Response) {
                Log.d("AutoComplete", "Response code: ${response.code}")
                if (response.isSuccessful) {
                    response.body?.let { responseBody ->
                        val responseString = responseBody.string()
                        val jsonArray = JSONArray(responseString)

                        placeList.clear()

                        for (i in 0 until jsonArray.length()) {
                            val placeObj = jsonArray.getJSONObject(i)
                            val ref_id = placeObj.optString("ref_id","Không có refid")
                            val name = placeObj.optString("name", "Không xác định")
                            val address = placeObj.optString("address", "Không có địa chỉ")

                            placeList.add(Place(ref_id, name, address))
                        }

                        if (placeList.isNotEmpty()) {
                            lastRecognizedDestinationLocation = placeList.first() // Lưu địa điểm đầu tiên
                            val firstPlace = placeList.first()
                            runOnUiThread {
                                editTextDestination.setText(firstPlace.name)
                                editTextDestination.setSelection(firstPlace.name.length)
                                getPlace(firstPlace.ref_id, false)
                                Toast.makeText(this@MainActivity, "Điểm đi: ${lastRecognizedStartLocation?.name}\nĐiểm đến: ${lastRecognizedDestinationLocation?.name}", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            runOnUiThread {
                                speakOut("Không tìm thấy địa điểm!")
                                promptForDestination() // Hỏi lại điểm đến nếu không tìm thấy
                            }
                        }
                    }
                } else {
                    Log.e("AutoComplete", "Lỗi không phản hồi: ${response.message}")
                }
            }
        })
    }

    private fun speakOut(text: String) {
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun enableUserLocation() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), 1)
            return
        }
        getUserLocation()
    }

    @SuppressLint("MissingPermission")
    private fun getUserLocation() {
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                if (location != null) {
                    updateUserLocation(location)
                } else {
                    // Nếu lastLocation bị null, yêu cầu cập nhật vị trí mới
                    requestNewLocation()
                }
            }
            .addOnFailureListener { e ->
                Log.e("LocationError", "Lỗi khi lấy vị trí: ${e.message}")
            }
    }

    // Hàm cập nhật vị trí trên bản đồ
    private fun updateUserLocation(location: Location) {
        val userLatLng = LatLng(location.latitude, location.longitude)
        userLocation = "${location.latitude},${location.longitude}"
        vietMapGL.moveCamera(
            vn.vietmap.vietmapsdk.camera.CameraUpdateFactory.newLatLngZoom(userLatLng, 15.0)
        )
        vietMapGL.clear() // Xóa marker cũ để tránh chồng chéo
        vietMapGL.addMarker(
            vn.vietmap.vietmapsdk.annotations.MarkerOptions()
                .position(userLatLng)
                .title("Vị trí của tôi")
                .icon(IconFactory.getInstance(this).fromResource(R.drawable.icon_location))
        )
    }

    // Nếu `lastLocation` bị null, yêu cầu cập nhật vị trí mới
    @SuppressLint("MissingPermission")
    private fun requestNewLocation() {
        val locationRequest = LocationRequest.create().apply {
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            interval = 5000
            fastestInterval = 2000
            numUpdates = 1
        }
        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.locations.lastOrNull()?.let { location ->
                    updateUserLocation(location)
                    fusedLocationClient.removeLocationUpdates(this)
                }
            }
        }
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider  = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases() {
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        imageAnalyzer = ImageAnalysis.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()

        imageAnalyzer?.setAnalyzer(cameraExecutor) { imageProxy ->
            val bitmapBuffer = Bitmap.createBitmap(
                imageProxy.width, imageProxy.height, Bitmap.Config.ARGB_8888
            )
            imageProxy.use { bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer) }
            imageProxy.close()

            val rotatedBitmap = Bitmap.createBitmap(
                bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height,
                Matrix().apply { postRotate(imageProxy.imageInfo.rotationDegrees.toFloat()) }, true
            )

            detector?.detect(rotatedBitmap)
        }

        cameraProvider?.unbindAll()
        try {
            cameraProvider?.bindToLifecycle(this, cameraSelector, imageAnalyzer)
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()) {
        if (it[Manifest.permission.CAMERA] == true) {
            startCamera()
        }else {
            Toast.makeText(this, "Camera permission is required", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onEmptyDetect() {}
    override fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long) {}

    companion object {
        private const val TAG = "Camera"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    // Hàm tạo bitmap trong suốt
    private fun createTransparentBitmap(): Bitmap {
        // Tạo một bitmap có kích thước 1x1 với màu trong suốt
        val width = 1
        val height = 1
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.TRANSPARENT) // Đặt màu nền là trong suốt
        return bitmap
    }

    private fun moveMarker(lat: Double, lng: Double, isStart: Boolean) {
        val latLng = LatLng(lat, lng)
        runOnUiThread {
            val sharedPreferences = getSharedPreferences("my_prefs", MODE_PRIVATE)
            val editor = sharedPreferences.edit()

            if (isStart) {
                startMarker?.let { vietMapGL.removeMarker(it) }
                startMarker = vietMapGL.addMarker(
                    vn.vietmap.vietmapsdk.annotations.MarkerOptions()
                        .position(latLng)
                        .title("Điểm đi")
                )
                Log.d("MoveMarker", "Đã cập nhật marker điểm đi: $lat, $lng")

                editor.putString("startLat", lat.toString())
                editor.putString("startLng", lng.toString())
            } else {
                destinationMarker?.let { vietMapGL.removeMarker(it) }
                destinationMarker = vietMapGL.addMarker(
                    vn.vietmap.vietmapsdk.annotations.MarkerOptions()
                        .position(latLng)
                        .title("Điểm đến")
                )
                Log.d("MoveMarker", "Đã cập nhật marker điểm đến: $lat, $lng")

                editor.putString("destinationLat", lat.toString())
                editor.putString("destinationLng", lng.toString())
            }

            editor.apply()
        }
    }

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() {
        super.onResume();
        mapView.onResume();
        if (allPermissionsGranted()){
        startCamera()
    } else {
        requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)
    } }
    override fun onPause() { super.onPause(); mapView.onPause() }
    override fun onStop() { super.onStop(); mapView.onStop() }
    override fun onDestroy() { super.onDestroy(); mapView.onDestroy()
        if (textToSpeech.isSpeaking) {
            textToSpeech.stop()
        }
        textToSpeech.shutdown()
        super.onDestroy()

        isRecording = false
        audioRecord?.release()
        audioRecord = null

        detector?.close()
        cameraExecutor.shutdown()
    }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
}