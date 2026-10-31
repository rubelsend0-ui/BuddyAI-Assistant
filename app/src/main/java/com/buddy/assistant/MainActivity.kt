package com.buddy.assistant

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.telephony.SmsManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

interface BuddyApi {
    @POST("api/v1/chat")
    suspend fun sendChat(
        @Header("X-Buddy-Token") token: String,
        @Body request: ChatRequest
    ): ChatResponse
}

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var etBackendUrl: EditText
    private lateinit var etUserCommand: EditText
    private lateinit var btnSpeak: Button
    private lateinit var btnSend: Button
    private lateinit var btnPermAccessibility: Button
    private lateinit var tvLiveLog: TextView
    private lateinit var tvEngineStatus: TextView

    private var tts: TextToSpeech? = null

    private val speechLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK && res.data != null) {
            val list = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!list.isNullOrEmpty()) {
                val spoken = list[0]
                etUserCommand.setText(spoken)
                executeUserCommand(spoken)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etBackendUrl = findViewById(R.id.etBackendUrl)
        etUserCommand = findViewById(R.id.etUserCommand)
        btnSpeak = findViewById(R.id.btnSpeak)
        btnSend = findViewById(R.id.btnSend)
        btnPermAccessibility = findViewById(R.id.btnPermAccessibility)
        tvLiveLog = findViewById(R.id.tvLiveLog)
        tvEngineStatus = findViewById(R.id.tvEngineStatus)

        tts = TextToSpeech(this, this)

        requestNeededPermissions()

        btnPermAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(this, "Enable 'Buddy AI' under Accessibility", Toast.LENGTH_LONG).show()
        }

        btnSend.setOnClickListener {
            val cmd = etUserCommand.text.toString().trim()
            if (cmd.isNotEmpty()) executeUserCommand(cmd)
        }

        btnSpeak.setOnClickListener {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Buddy ko boliye...")
            }
            try {
                speechLauncher.launch(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Speech recognition unavailable", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun executeUserCommand(cmd: String) {
        tvLiveLog.text = "Synthesizing with Buddy Core Brain..."
        var url = etBackendUrl.text.toString().trim()
        if (!url.endsWith("/")) url += "/"

        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        val api = Retrofit.Builder()
            .baseUrl(url)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BuddyApi::class.java)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = api.sendChat(
                    token = "buddy-secret-key-prod-007",
                    request = ChatRequest(message = cmd)
                )
                withContext(Dispatchers.Main) {
                    tvLiveLog.text = response.message
                    speakText(response.message)
                    handleActions(response.actions)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val err = "Brain connection error: ${e.localizedMessage}"
                    tvLiveLog.text = err
                    speakText(err)
                }
            }
        }
    }

    private fun handleActions(actions: List<ActionItem>?) {
        if (actions.isNullOrEmpty()) return

        for (action in actions) {
            when (action.type) {
                "SEND_WHATSAPP_MESSAGE" -> {
                    val msg = action.parameters["message"]?.toString() ?: ""
                    BuddyAccessibilityService.isAutoSendPending = true

                    val encoded = URLEncoder.encode(msg, "UTF-8")
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("https://api.whatsapp.com/send?text=$encoded")
                        setPackage("com.whatsapp")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try {
                        startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(this, "WhatsApp not found", Toast.LENGTH_SHORT).show()
                    }
                }
                "CREATE_REMINDER", "SEND_SMS" -> {
                    val receiver = (action.parameters["receiver"] ?: action.parameters["contact"])?.toString() ?: ""
                    val body = (action.parameters["body"] ?: action.parameters["message"])?.toString() ?: ""
                    try {
                        val smsManager = getSystemService(SmsManager::class.java)
                        smsManager.sendTextMessage(receiver, null, body, null, null)
                        Toast.makeText(this, "Direct SMS dispatched to $receiver", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(this, "SMS failed: grant permission", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun speakText(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "BUDDY_SPEAK")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("en", "IN")
        }
    }

    private fun requestNeededPermissions() {
        val perms = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.SEND_SMS,
            Manifest.permission.CALL_PHONE
        )
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
                .launch(missing.toTypedArray())
        }
    }

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }
}
