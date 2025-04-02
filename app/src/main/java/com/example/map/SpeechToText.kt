package com.example.map

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class SpeechToText(private val context: Context, private val activityResultLauncher: ActivityResultLauncher<Intent>) {

    companion object {
        const val PERMISSION_REQUEST_CODE = 1
    }

    fun checkAndStartRecognition() {
        if (ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Yêu cầu quyền RECORD_AUDIO
            ActivityCompat.requestPermissions(
                context as MainActivity,
                arrayOf(android.Manifest.permission.RECORD_AUDIO),
                PERMISSION_REQUEST_CODE
            )
        } else {
            startSpeechRecognition()
        }
    }

    fun startSpeechRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Nói địa điểm")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        }

        activityResultLauncher.launch(intent)
        Log.d("SpeechRecognition", "Speech recognition intent launched.")
    }
}
