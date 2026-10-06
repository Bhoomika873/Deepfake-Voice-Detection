package com.example.deepfakevoicedetection

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.media.AudioTrack
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.core.content.ContextCompat

import com.example.deepfakevoicedetection.ui.theme.DeepfakeVoiceDetectionTheme

import kotlin.concurrent.thread

class SpeakerIdentificationActivity : ComponentActivity() {

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val RECORD_SECONDS = 3
    }

    private var isRecording by mutableStateOf(false)
    private var statusText by mutableStateOf("Ready")
    private var showStatusDialog by mutableStateOf(false)
    private var showProgressDialog by mutableStateOf(false)
    private var progressMessage by mutableStateOf("")
    private var identificationResult by mutableStateOf<String?>(null)

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null

    private val requestPermission =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->

            if (granted) {
                startRecording()
            } else {
                statusText =
                    "Microphone permission required"
            }
        }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        setContent {

            DeepfakeVoiceDetectionTheme {

                var speakerName by remember {
                    mutableStateOf("")
                }

                var showStoredSpeakers by remember {
                    mutableStateOf(false)
                }

                var editingSpeaker by remember {
                    mutableStateOf<StoredSpeaker?>(null)
                }

                var editedSpeakerName by remember {
                    mutableStateOf("")
                }

                var showProgressDialog by remember {
                    mutableStateOf(false)
                }

                var progressMessage by remember {
                    mutableStateOf("")
                }

                var identificationResult by remember {
                    mutableStateOf<String?>(null)
                }

                LaunchedEffect(statusText) {

                    if (
                        statusText.isNotBlank() &&
                        statusText != "Ready"
                    ) {

                        showStatusDialog = true

                        delay(1500)

                        showStatusDialog = false
                    }
                }

                if (showStatusDialog) {

                    AlertDialog(
                        onDismissRequest = {
                            showStatusDialog = false
                        },
                        title = {
                            Text(
                                text = "Status",
                                fontWeight = FontWeight.Bold
                            )
                        },
                        text = {
                            Text(
                                text = statusText,
                                fontSize = 16.sp
                            )
                        },
                        confirmButton = {}
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFEEE3D0))
                        .padding(24.dp),

                    horizontalAlignment =
                        Alignment.CenterHorizontally,

                    verticalArrangement =
                        Arrangement.Center
                ) {

                    Text(
                        text = "Speaker Identification",
                        color = Color(0xFF35271F),
                        fontSize = 28.sp
                    )

                    Spacer(
                        modifier =
                            Modifier.height(24.dp)
                    )

                    OutlinedTextField(
                        value = speakerName,
                        onValueChange = {
                            speakerName = it
                        },
                        label = {
                            Text("Speaker name")
                        },
                        singleLine = true,
                        modifier =
                            Modifier.fillMaxWidth()
                    )

                    Spacer(
                        modifier =
                            Modifier.height(20.dp)
                    )

                    Button(
                        onClick = {

                            if (speakerName.isBlank()) {

                                statusText =
                                    "Enter a speaker name"

                            } else if (!isRecording) {

                                val enteredName = speakerName.trim()

                                val storage =
                                    SpeakerStorage(
                                        this@SpeakerIdentificationActivity
                                    )

                                val alreadyExists =
                                    storage.getSpeakers().any {
                                        it.name.equals(
                                            enteredName,
                                            ignoreCase = true
                                        )
                                    }

                                if (alreadyExists) {

                                    statusText =
                                        "Speaker already exists"

                                } else {

                                    startRegistration(
                                        enteredName
                                    ) {
                                        speakerName = ""
                                    }
                                }
                            }
                        },

                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                    ) {

                        Text(
                            text =
                                if (isRecording)
                                    "Recording..."
                                else
                                    "Register Voice"
                        )
                    }
                    Spacer(
                        modifier = Modifier.height(16.dp)
                    )

                    Button(
                        onClick = {

                            if (!isRecording) {
                                startIdentification()
                            }
                        },

                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Text(
                            text = "Identify Speaker"
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            showStoredSpeakers = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = "Stored Speakers",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (showStoredSpeakers) {

                        val storage = SpeakerStorage(this@SpeakerIdentificationActivity)
                        val speakers = storage.getSpeakers()

                        AlertDialog(
                            onDismissRequest = {
                                showStoredSpeakers = false
                            },
                            title = {
                                Text(
                                    text = "Stored Speakers",
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            text = {

                                if (speakers.isEmpty()) {

                                    Text("No speakers registered yet.")

                                } else {

                                    Column {

                                        speakers.forEach { speaker ->

                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {

                                                Text(
                                                    text = speaker.name,
                                                    modifier = Modifier.weight(1f),
                                                    fontSize = 16.sp
                                                )

                                                OutlinedButton(
                                                    onClick = {
                                                        playSpeakerAudio(speaker.audio)
                                                    }
                                                ) {
                                                    Text("▶")
                                                }

                                                Spacer(
                                                    modifier = Modifier.width(6.dp)
                                                )

                                                OutlinedButton(
                                                    onClick = {
                                                        editingSpeaker = speaker
                                                        editedSpeakerName = speaker.name
                                                    }
                                                ) {
                                                    Text("✏")
                                                }

                                                Spacer(
                                                    modifier = Modifier.width(6.dp)
                                                )

                                                OutlinedButton(
                                                    onClick = {
                                                        storage.deleteSpeaker(speaker.name)
                                                        showStoredSpeakers = false
                                                        showStoredSpeakers = true
                                                    }
                                                ) {
                                                    Text("🗑")
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        audioTrack?.let { track ->
                                            try {
                                                track.stop()
                                            } catch (_: Exception) {
                                            }

                                            try {
                                                track.release()
                                            } catch (_: Exception) {
                                            }
                                        }

                                        audioTrack = null
                                        showStoredSpeakers = false
                                        statusText = "Ready"
                                    }
                                ) {
                                    Text("Close")
                                }
                            }
                        )
                    }
                }
                if (editingSpeaker != null) {

                    AlertDialog(
                        onDismissRequest = {
                            editingSpeaker = null
                        },
                        title = {
                            Text("Edit Speaker Name")
                        },
                        text = {
                            OutlinedTextField(
                                value = editedSpeakerName,
                                onValueChange = {
                                    editedSpeakerName = it
                                },
                                label = {
                                    Text("Speaker name")
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {

                                    val newName =
                                        editedSpeakerName.trim()

                                    if (newName.isNotEmpty()) {

                                        val storage =
                                            SpeakerStorage(
                                                this@SpeakerIdentificationActivity
                                            )

                                        val oldSpeaker =
                                            editingSpeaker!!

                                        storage.deleteSpeaker(
                                            oldSpeaker.name
                                        )

                                        storage.saveSpeaker(
                                            name = newName,
                                            embedding = oldSpeaker.embedding,
                                            audio = oldSpeaker.audio
                                        )

                                        editingSpeaker = null

                                        statusText =
                                            "$newName updated successfully"
                                    }
                                }
                            ) {
                                Text("Save")
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    editingSpeaker = null
                                }
                            ) {
                                Text("Cancel")
                            }
                        }
                    )
                }
                if (showProgressDialog) {

                    AlertDialog(
                        onDismissRequest = {
                            // Do not allow closing while processing
                        },
                        title = {
                            Text(
                                text = "Processing Voice",
                                fontWeight = FontWeight.Bold
                            )
                        },
                        text = {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {

                                androidx.compose.material3.CircularProgressIndicator()

                                Spacer(
                                    modifier = Modifier.height(20.dp)
                                )

                                Text(
                                    text = progressMessage,
                                    fontSize = 16.sp
                                )
                            }
                        },
                        confirmButton = {}
                    )
                }
            }
        }
        }

    private fun playSpeakerAudio(audio: FloatArray) {

        if (audio.isEmpty()) {
            statusText = "No recorded audio available"
            return
        }

        Thread {

            try {

                val pcmData = ShortArray(audio.size) { index ->
                    (audio[index] * 32767f)
                        .coerceIn(-32768f, 32767f)
                        .toInt()
                        .toShort()
                }

                val bufferSize = pcmData.size * 2

                val track = AudioTrack(
                    android.media.AudioManager.STREAM_MUSIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize,
                    AudioTrack.MODE_STATIC
                )

                audioTrack = track

                track.write(
                    pcmData,
                    0,
                    pcmData.size
                )

                runOnUiThread {
                    statusText = "Playing speaker voice..."
                }

                track.play()

                while (
                    audioTrack === track &&
                    track.playbackHeadPosition < pcmData.size
                ) {
                    Thread.sleep(50)
                }

                try {
                    track.stop()
                } catch (_: Exception) {
                }

                track.release()

                if (audioTrack === track) {
                    audioTrack = null
                }

                runOnUiThread {
                    statusText = "Ready"
                }

            } catch (e: Exception) {

                runOnUiThread {
                    statusText =
                        "Playback error: ${e.message}"
                }
            }
        }.start()
    }

                private fun startRecording() {

                    if (
                        ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.RECORD_AUDIO
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        statusText = "Microphone permission required"
                        requestPermission.launch(
                            Manifest.permission.RECORD_AUDIO
                        )
                        return
                    }

                    val minBuffer =
                        AudioRecord.getMinBufferSize(
                            SAMPLE_RATE,
                            AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT
                        )

                    if (minBuffer <= 0) {
                        statusText = "Unable to initialize microphone"
                        return
                    }

                    val bufferSize =
                        maxOf(
                            minBuffer,
                            SAMPLE_RATE * 2
                        )

                    try {

                        val record =
                            AudioRecord(
                                MediaRecorder.AudioSource.MIC,
                                SAMPLE_RATE,
                                AudioFormat.CHANNEL_IN_MONO,
                                AudioFormat.ENCODING_PCM_16BIT,
                                bufferSize
                            )

                        if (record.state != AudioRecord.STATE_INITIALIZED) {
                            record.release()
                            statusText = "Microphone initialization failed"
                            return
                        }

                        audioRecord = record

                        isRecording = true
                        statusText = "Speak for 3 seconds..."

                        thread {

                            val audio =
                                ShortArray(
                                    SAMPLE_RATE * RECORD_SECONDS
                                )

                            var totalRead = 0

                            try {

                                record.startRecording()

                                while (
                                    totalRead < audio.size &&
                                    isRecording
                                ) {

                                    val read =
                                        record.read(
                                            audio,
                                            totalRead,
                                            audio.size - totalRead
                                        )

                                    if (read > 0) {
                                        totalRead += read
                                    }
                                }

                                // Convert recorded PCM to FloatArray
                                val audioFloat =
                                    FloatArray(totalRead) { index ->
                                        audio[index].toFloat() / 32768.0f
                                    }

                                if (audioFloat.isEmpty()) {
                                    runOnUiThread {
                                        statusText = "No audio recorded"
                                    }
                                    return@thread
                                }

                                runOnUiThread {
                                    statusText = "Creating speaker embedding..."
                                }

                                val engine =
                                    SpeakerRecognitionEngine(
                                        this@SpeakerIdentificationActivity
                                    )

                                try {

                                    val embedding =
                                        engine.createEmbedding(
                                            audioFloat
                                        )

                                    runOnUiThread {
                                        statusText =
                                            "Embedding created: ${embedding.size} dimensions"
                                    }

                                } finally {

                                    engine.close()
                                }

                            } catch (e: SecurityException) {

                                runOnUiThread {
                                    statusText =
                                        "Microphone permission was denied"
                                }

                            } catch (e: Exception) {

                                runOnUiThread {
                                    statusText =
                                        "Speaker processing error: ${e.message}"
                                }

                            } finally {

                                try {
                                    record.stop()
                                } catch (_: Exception) {
                                }

                                record.release()

                                audioRecord = null

                                runOnUiThread {
                                    isRecording = false
                                }
                            }
                        }

                    } catch (e: SecurityException) {

                        isRecording = false
                        statusText =
                            "Microphone permission was denied"

                    } catch (e: Exception) {

                        isRecording = false
                        statusText =
                            "Microphone error: ${e.message}"
                    }
                }

    private fun startRegistration(
        speakerName: String,
        onRegistered: () -> Unit
    ) {

                    if (
                        ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.RECORD_AUDIO
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        statusText =
                            "Microphone permission required"

                        requestPermission.launch(
                            Manifest.permission.RECORD_AUDIO
                        )

                        return
                    }

                    val minBuffer =
                        AudioRecord.getMinBufferSize(
                            SAMPLE_RATE,
                            AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT
                        )

                    if (minBuffer <= 0) {
                        statusText =
                            "Unable to initialize microphone"
                        return
                    }

                    val bufferSize =
                        maxOf(
                            minBuffer,
                            SAMPLE_RATE * 2
                        )

                    try {

                        val record =
                            AudioRecord(
                                MediaRecorder.AudioSource.MIC,
                                SAMPLE_RATE,
                                AudioFormat.CHANNEL_IN_MONO,
                                AudioFormat.ENCODING_PCM_16BIT,
                                bufferSize
                            )

                        if (
                            record.state !=
                            AudioRecord.STATE_INITIALIZED
                        ) {
                            record.release()

                            statusText =
                                "Microphone initialization failed"

                            return
                        }

                        audioRecord = record

                        isRecording = true
                        statusText =
                            "Speak for 3 seconds..."

                        thread {

                            val audio =
                                ShortArray(
                                    SAMPLE_RATE *
                                            RECORD_SECONDS
                                )

                            var totalRead = 0

                            try {

                                record.startRecording()

                                while (
                                    totalRead < audio.size &&
                                    isRecording
                                ) {

                                    val read =
                                        record.read(
                                            audio,
                                            totalRead,
                                            audio.size - totalRead
                                        )

                                    if (read > 0) {
                                        totalRead += read
                                    }
                                }

                                if (totalRead <= 0) {

                                    runOnUiThread {
                                        statusText =
                                            "No audio recorded"
                                    }

                                    return@thread
                                }

                                val audioFloat =
                                    FloatArray(totalRead) { index ->
                                        audio[index]
                                            .toFloat() / 32768.0f
                                    }

                                runOnUiThread {
                                    statusText =
                                        "Creating voice profile..."
                                }

                                val engine =
                                    SpeakerRecognitionEngine(
                                        this@SpeakerIdentificationActivity
                                    )

                                try {

                                    engine.registerSpeaker(
                                        name = speakerName,
                                        audio = audioFloat
                                    )

                                    runOnUiThread {
                                        statusText =
                                            "$speakerName registered successfully"

                                        onRegistered()
                                    }

                                } finally {

                                    engine.close()
                                }

                            } catch (e: SecurityException) {

                                runOnUiThread {
                                    statusText =
                                        "Microphone permission was denied"
                                }

                            } catch (e: Exception) {

                                runOnUiThread {
                                    statusText =
                                        "Registration error: ${e.message}"
                                }

                            } finally {

                                try {
                                    record.stop()
                                } catch (_: Exception) {
                                }

                                record.release()

                                audioRecord = null

                                runOnUiThread {
                                    isRecording = false
                                }
                            }

                        }

                    } catch (e: SecurityException) {

                        isRecording = false

                        statusText =
                            "Microphone permission was denied"

                    } catch (e: Exception) {

                        isRecording = false

                        statusText =
                            "Microphone error: ${e.message}"
                    }
                }

                private fun startIdentification() {

                    if (
                        ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.RECORD_AUDIO
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        statusText =
                            "Microphone permission required"

                        requestPermission.launch(
                            Manifest.permission.RECORD_AUDIO
                        )

                        return
                    }

                    val engine =
                        SpeakerRecognitionEngine(this)

                    val storedSpeakers =
                        engine.getStoredSpeakers()

                    if (storedSpeakers.isEmpty()) {

                        engine.close()

                        statusText =
                            "No speakers registered"

                        return
                    }

                    val minBuffer =
                        AudioRecord.getMinBufferSize(
                            SAMPLE_RATE,
                            AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT
                        )

                    if (minBuffer <= 0) {

                        engine.close()

                        statusText =
                            "Unable to initialize microphone"

                        return
                    }

                    val bufferSize =
                        maxOf(
                            minBuffer,
                            SAMPLE_RATE * 2
                        )

                    try {

                        val record =
                            AudioRecord(
                                MediaRecorder.AudioSource.MIC,
                                SAMPLE_RATE,
                                AudioFormat.CHANNEL_IN_MONO,
                                AudioFormat.ENCODING_PCM_16BIT,
                                bufferSize
                            )

                        if (
                            record.state !=
                            AudioRecord.STATE_INITIALIZED
                        ) {

                            record.release()
                            engine.close()

                            statusText =
                                "Microphone initialization failed"

                            return
                        }

                        audioRecord = record

                        isRecording = true

                        runOnUiThread {
                            progressMessage = "Recording voice..."
                            showProgressDialog = true
                        }

                        thread {

                            val audio =
                                ShortArray(
                                    SAMPLE_RATE *
                                            RECORD_SECONDS
                                )

                            var totalRead = 0

                            try {

                                record.startRecording()

                                while (
                                    totalRead < audio.size &&
                                    isRecording
                                ) {

                                    val read =
                                        record.read(
                                            audio,
                                            totalRead,
                                            audio.size - totalRead
                                        )

                                    if (read > 0) {
                                        totalRead += read
                                    }
                                }

                                if (totalRead <= 0) {

                                    runOnUiThread {
                                        statusText =
                                            "No audio recorded"
                                    }

                                    return@thread
                                }

                                val audioFloat =
                                    FloatArray(totalRead) { index ->
                                        audio[index]
                                            .toFloat() / 32768.0f
                                    }

                                runOnUiThread {
                                    progressMessage = "Comparing voice with stored speakers..."
                                }

                                val match =
                                    engine.identifySpeaker(
                                        audioFloat
                                    )

                                runOnUiThread {

                                    showProgressDialog = false

                                    if (match == null) {

                                        identificationResult =
                                            "NO — No voice matched"

                                        statusText =
                                            "No voice matched"

                                    } else {

                                        val percentage =
                                            match.similarity * 100f

                                        if (percentage >= 50f) {

                                            identificationResult =
                                                "YES — ${match.name}\n" +
                                                        "${"%.1f".format(percentage)}% similarity"

                                            statusText =
                                                "${match.name} detected"

                                        } else {

                                            identificationResult =
                                                "NO — No voice matched\n" +
                                                        "${"%.1f".format(percentage)}% similarity"

                                            statusText =
                                                "No voice matched"
                                        }
                                    }
                                }

                            } catch (e: SecurityException) {

                                runOnUiThread {
                                    statusText =
                                        "Microphone permission was denied"
                                }

                            } catch (e: Exception) {

                                runOnUiThread {
                                    statusText =
                                        "Identification error: ${e.message}"
                                }

                            } finally {

                                try {
                                    record.stop()
                                } catch (_: Exception) {
                                }

                                record.release()

                                audioRecord = null

                                engine.close()

                                runOnUiThread {
                                    isRecording = false
                                }
                            }
                        }

                    } catch (e: SecurityException) {

                        engine.close()

                        isRecording = false

                        statusText =
                            "Microphone permission was denied"

                    } catch (e: Exception) {

                        engine.close()

                        isRecording = false

                        statusText =
                            "Microphone error: ${e.message}"
                    }
                }


                override fun onDestroy() {

                    isRecording = false

                    audioRecord?.let {

                        try {
                            it.stop()
                        } catch (_: Exception) {
                        }

                        it.release()
                    }

                    audioRecord = null

                    super.onDestroy()
                }
            }
