package com.example.deepfakevoicedetection

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.core.content.ContextCompat

import com.example.deepfakevoicedetection.ui.theme.DeepfakeVoiceDetectionTheme


/*
 * ============================================================
 * COLOR PALETTE
 * ============================================================
 *
 * Background : #EEE3D0
 * Primary    : #35271F
 * Secondary  : #6B5141
 * Cards      : #F7F0E5
 * Accent     : #9A7958
 * Text       : #29201B
 *
 * ============================================================
 */

private val BackgroundColor = Color(0xFFEEE3D0)
private val PrimaryColor = Color(0xFF35271F)
private val SecondaryColor = Color(0xFF6B5141)
private val CardColor = Color(0xFFF7F0E5)
private val AccentColor = Color(0xFF9A7958)
private val TextColor = Color(0xFF29201B)

private val RealVoiceColor = Color(0xFF526B45)
private val DeepfakeVoiceColor = Color(0xFF8B3A32)


/*
 * ============================================================
 * MAIN ACTIVITY
 * ============================================================
 */

class MainActivity : ComponentActivity() {

    private lateinit var detector: DeepfakeDetector

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null

    private val sampleRate = 16000

    private var isRecording = false

    private val audioFilePicker =
        registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri: Uri? ->

            if (uri != null) {
                analyzeAudioFile(uri)
            }
        }

    /*
     * ========================================================
     * RUNTIME MICROPHONE PERMISSION
     * ========================================================
     */

    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->

            if (granted) {

                startAudioMonitoring()

            } else {

                detectionResult =
                    "MICROPHONE PERMISSION DENIED"

                confidence = 0.0
            }
        }


    /*
     * ========================================================
     * ON CREATE
     * ========================================================
     */

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(savedInstanceState)


        /*
         * Initialize deepfake detector.
         */

        detector = DeepfakeDetector(this)


        /*
         * Compose UI.
         */

        setContent {

            DeepfakeVoiceDetectionTheme {

                DeepfakeMonitoringScreen(

                    onStartMonitoring = {


                        /*
                         * Check microphone permission.
                         */

                        if (
                            ContextCompat.checkSelfPermission(
                                this,
                                Manifest.permission.RECORD_AUDIO
                            ) ==
                            PackageManager.PERMISSION_GRANTED
                        ) {

                            startAudioMonitoring()

                        } else {

                            permissionLauncher.launch(
                                Manifest.permission.RECORD_AUDIO
                            )
                        }
                    },


                    onStopMonitoring = {

                        stopAudioMonitoring()

                    },
                    onUploadAudio = {
                        audioFilePicker.launch("audio/*")
                    }
                )
            }
        }
    }


    /*
     * ============================================================
     * START AUDIO MONITORING
     * ============================================================
     */

    private fun startAudioMonitoring() {

        /*
         * Second permission check.
         *
         * AudioRecord requires RECORD_AUDIO permission.
         */

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) !=
            PackageManager.PERMISSION_GRANTED
        ) {

            detectionResult =
                "MICROPHONE PERMISSION REQUIRED"

            confidence = 0.0


            permissionLauncher.launch(
                Manifest.permission.RECORD_AUDIO
            )

            return
        }


        /*
         * Prevent multiple recording sessions.
         */

        if (isRecording) return


        /*
         * Get minimum buffer size.
         */

        val bufferSize =
            AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )


        /*
         * Validate buffer size.
         */

        if (bufferSize <= 0) {

            detectionResult =
                "AUDIO INITIALIZATION FAILED"

            confidence = 0.0

            return
        }


        /*
         * Create AudioRecord.
         */

        try {

            audioRecord = AudioRecord(

                MediaRecorder.AudioSource.MIC,

                sampleRate,

                AudioFormat.CHANNEL_IN_MONO,

                AudioFormat.ENCODING_PCM_16BIT,

                bufferSize * 2
            )

        } catch (
            securityException: SecurityException
        ) {

            detectionResult =
                "MICROPHONE ACCESS DENIED"

            confidence = 0.0

            return
        }


        /*
         * Verify AudioRecord initialization.
         */

        if (
            audioRecord?.state !=
            AudioRecord.STATE_INITIALIZED
        ) {

            detectionResult =
                "MICROPHONE INITIALIZATION FAILED"


            audioRecord?.release()

            audioRecord = null

            return
        }


        /*
         * Start recording.
         */

        try {

            audioRecord?.startRecording()

        } catch (
            securityException: SecurityException
        ) {

            detectionResult =
                "MICROPHONE ACCESS DENIED"


            audioRecord?.release()

            audioRecord = null

            return
        }


        /*
         * Set recording state.
         */

        isRecording = true

        detectionResult = "LISTENING"

        confidence = 0.0


        /*
         * ====================================================
         * AUDIO PROCESSING THREAD
         * ====================================================
         */

        recordingThread = Thread {

            /*
             * 64,600 samples at 16 kHz
             *
             * ≈ 4 seconds of audio.
             */

            val buffer =
                ShortArray(64600)


            while (isRecording) {

                var totalSamples = 0


                /*
                 * Fill the complete audio buffer.
                 */

                while (
                    totalSamples < buffer.size &&
                    isRecording
                ) {

                    val read =
                        try {

                            audioRecord?.read(
                                buffer,
                                totalSamples,
                                buffer.size - totalSamples
                            ) ?: 0

                        } catch (
                            securityException:
                            SecurityException
                        ) {

                            0
                        }


                    if (read > 0) {

                        totalSamples += read
                    }
                }


                /*
                 * Analyze the audio when the
                 * complete buffer is available.
                 */

                if (
                    totalSamples ==
                    buffer.size &&
                    isRecording
                ) {

                    val result =
                        analyzeAudio(buffer)


                    /*
                     * Update UI on main thread.
                     */

                    runOnUiThread {

                        if (isRecording) {

                            detectionResult =
                                result.label

                            confidence =
                                result.confidence
                        }
                    }
                }
            }
        }


        /*
         * Start processing thread.
         */

        recordingThread?.start()
    }


    /*
     * ============================================================
     * STOP AUDIO MONITORING
     * ============================================================
     */

    private fun stopAudioMonitoring() {

        /*
         * Stop processing loop.
         */

        isRecording = false


        /*
         * Stop AudioRecord.
         */

        try {

            audioRecord?.stop()

        } catch (_: Exception) {
        }


        /*
         * Release microphone.
         */

        audioRecord?.release()

        audioRecord = null


        /*
         * Clear thread reference.
         */

        recordingThread = null


        /*
         * Reset UI.
         */

        detectionResult = "READY"

        confidence = 0.0
    }


    /*
     * ============================================================
     * DEEPFAKE AUDIO ANALYSIS
     * ============================================================
     */

    private fun analyzeAudio(
        audio: ShortArray
    ): DetectionResult {

        return detector.detect(audio)
    }

    private fun analyzeAudioFile(uri: Uri) {

        Thread {

            try {

                val inputStream =
                    contentResolver.openInputStream(uri)

                if (inputStream == null) {
                    runOnUiThread {
                        detectionResult = "AUDIO FILE ERROR"
                        confidence = 0.0
                    }
                    return@Thread
                }

                val audioBytes =
                    inputStream.readBytes()

                inputStream.close()

                // Convert file bytes to PCM samples
                val shortBuffer =
                    java.nio.ByteBuffer
                        .wrap(audioBytes)
                        .order(java.nio.ByteOrder.LITTLE_ENDIAN)

                val samples =
                    ShortArray(audioBytes.size / 2)

                shortBuffer.asShortBuffer().get(samples)

                if (samples.isEmpty()) {
                    runOnUiThread {
                        detectionResult = "NO AUDIO"
                        confidence = 0.0
                    }
                    return@Thread
                }

                val result =
                    analyzeAudio(samples)

                runOnUiThread {

                    detectionResult = result.label
                    confidence = result.confidence
                }

            } catch (_: Exception) {

                runOnUiThread {

                    detectionResult = "AUDIO ANALYSIS FAILED"
                    confidence = 0.0
                }
            }

        }.start()
    }


    /*
     * ============================================================
     * ACTIVITY DESTROY
     * ============================================================
     */

    override fun onDestroy() {

        stopAudioMonitoring()

        detector.close()

        super.onDestroy()
    }
}


/*
 * ============================================================
 * DETECTION RESULT
 * ============================================================
 */

data class DetectionResult(

    val label: String,

    val confidence: Double
)


/*
 * ============================================================
 * GLOBAL UI STATE
 * ============================================================
 */

var detectionResult by
mutableStateOf("READY")

var confidence by
mutableStateOf(0.0)


/*
 * ============================================================
 * MAIN COMPOSE SCREEN
 * ============================================================
 */

@Composable
fun DeepfakeMonitoringScreen(

    onStartMonitoring: () -> Unit,

    onStopMonitoring: () -> Unit,

    onUploadAudio: () -> Unit
) {

    /*
     * ========================================================
     * INFO DIALOG STATE
     * ========================================================
     */

    var showInfoDialog by remember {

        mutableStateOf(false)
    }


    /*
     * Context for opening LinkedIn.
     */

    val context =
        LocalContext.current


    /*
     * ========================================================
     * MONITORING STATE
     * ========================================================
     */

    val isMonitoring =

        detectionResult == "LISTENING" ||

                detectionResult == "REAL VOICE" ||

                detectionResult == "DEEPFAKE VOICE" ||

                detectionResult == "NO VOICE DETECTED"


    /*
     * ========================================================
     * MAIN SURFACE
     * ========================================================
     */

    Surface(

        modifier =
            Modifier.fillMaxSize(),

        color =
            BackgroundColor
    ) {


        Column(

            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(24.dp),

            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {


            /*
             * ==================================================
             * TOP INFORMATION BUTTON
             * ==================================================
             */

            Row(

                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),

                horizontalArrangement =
                    Arrangement.End
            ) {

                IconButton(

                    onClick = {

                        showInfoDialog = true
                    }
                ) {

                    Icon(

                        imageVector =
                            Icons.Default.Info,

                        contentDescription =
                            "App Information",

                        tint =
                            PrimaryColor,

                        modifier =
                            Modifier.size(30.dp)
                    )
                }
            }


            Spacer(
                modifier =
                    Modifier.height(8.dp)
            )


            /*
             * ==================================================
             * SECURITY ICON
             * ==================================================
             */

            Box(

                modifier =
                    Modifier
                        .size(70.dp)
                        .clip(CircleShape)
                        .background(
                            CardColor
                        ),

                contentAlignment =
                    Alignment.Center
            ) {

                Icon(

                    imageVector =
                        Icons.Default.Security,

                    contentDescription =
                        "Security",

                    tint =
                        PrimaryColor,

                    modifier =
                        Modifier.size(38.dp)
                )
            }


            Spacer(
                modifier =
                    Modifier.height(20.dp)
            )


            /*
             * ==================================================
             * TITLE
             * ==================================================
             */

            Text(

                text =
                    "Deepfake Voice",

                color =
                    PrimaryColor,

                fontSize =
                    30.sp,

                fontWeight =
                    FontWeight.Bold
            )


            Text(

                text =
                    "Live Monitoring",

                color =
                    SecondaryColor,

                fontSize =
                    28.sp,

                fontWeight =
                    FontWeight.Bold
            )


            Spacer(
                modifier =
                    Modifier.height(12.dp)
            )


            /*
             * ==================================================
             * DESCRIPTION
             * ==================================================
             */

            Text(

                text =
                    "AI-powered real-time voice authenticity detection",

                color =
                    SecondaryColor,

                fontSize =
                    14.sp,

                textAlign =
                    TextAlign.Center
            )


            Spacer(
                modifier =
                    Modifier.height(40.dp)
            )


            /*
             * ==================================================
             * MICROPHONE INDICATOR
             * ==================================================
             */

            Box(

                modifier =
                    Modifier
                        .size(170.dp)
                        .clip(CircleShape)
                        .background(

                            if (isMonitoring)

                                AccentColor.copy(
                                    alpha = 0.25f
                                )

                            else

                                SecondaryColor.copy(
                                    alpha = 0.15f
                                )
                        ),

                contentAlignment =
                    Alignment.Center
            ) {

                Icon(

                    imageVector =
                        Icons.Default.Mic,

                    contentDescription =
                        "Microphone",

                    tint =

                        if (isMonitoring)

                            SecondaryColor

                        else

                            AccentColor,

                    modifier =
                        Modifier.size(65.dp)
                )
            }


            Spacer(
                modifier =
                    Modifier.height(35.dp)
            )


            /*
             * ==================================================
             * DETECTION CARD
             * ==================================================
             */

            Column(

                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(
                            RoundedCornerShape(18.dp)
                        )
                        .background(
                            CardColor
                        )
                        .padding(22.dp),

                horizontalAlignment =
                    Alignment.CenterHorizontally
            ) {


                /*
                 * Detection card header.
                 */

                Row(

                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    Icon(

                        imageVector =
                            Icons.Default.GraphicEq,

                        contentDescription =
                            "Voice monitoring",

                        tint =
                            AccentColor,

                        modifier =
                            Modifier.size(28.dp)
                    )


                    Spacer(
                        modifier =
                            Modifier.size(10.dp)
                    )


                    Text(

                        text =
                            "Detection Status",

                        color =
                            PrimaryColor,

                        fontSize =
                            18.sp,

                        fontWeight =
                            FontWeight.SemiBold
                    )
                }


                Spacer(
                    modifier =
                        Modifier.height(20.dp)
                )


                /*
                 * Detection result.
                 */

                Text(

                    text =
                        detectionResult,

                    color = when {

                        detectionResult ==
                                "DEEPFAKE VOICE" ->

                            DeepfakeVoiceColor

                        detectionResult ==
                                "REAL VOICE" ->

                            RealVoiceColor

                        else ->

                            SecondaryColor
                    },

                    fontSize =
                        24.sp,

                    fontWeight =
                        FontWeight.Bold,

                    textAlign =
                        TextAlign.Center
                )


                Spacer(
                    modifier =
                        Modifier.height(10.dp)
                )


                /*
                 * Confidence score.
                 */

                if (confidence > 0) {

                    Text(

                        text =
                            "Confidence: " +
                                    "${(confidence * 100).toInt()}%",

                        color =
                            SecondaryColor,

                        fontSize =
                            14.sp
                    )

                } else {

                    Text(

                        text =
                            "Waiting for voice input...",

                        color =
                            SecondaryColor.copy(
                                alpha = 0.75f
                            ),

                        fontSize =
                            14.sp
                    )
                }
            }


            Spacer(
                modifier =
                    Modifier.height(30.dp)
            )


            /*
             * ==================================================
             * START / STOP BUTTON
             * ==================================================
             */

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Button(
                    onClick = {

                        if (isMonitoring) {
                            onStopMonitoring()
                        } else {
                            onStartMonitoring()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(62.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor =
                            if (isMonitoring)
                                SecondaryColor
                            else
                                PrimaryColor,
                        contentColor = Color.White
                    )
                ) {

                    Icon(
                        imageVector =
                            if (isMonitoring)
                                Icons.Default.Stop
                            else
                                Icons.Default.Mic,
                        contentDescription = null
                    )

                    Spacer(
                        modifier = Modifier.size(10.dp)
                    )

                    Text(
                        text =
                            if (isMonitoring)
                                "Stop Monitoring"
                            else
                                "Start Deepfake Monitoring",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(
                    onClick = {
                        onUploadAudio()
                    },
                    modifier = Modifier
                        .size(58.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(PrimaryColor)
                ) {

                    Icon(
                        imageVector = Icons.Default.UploadFile,
                        contentDescription = "Upload Audio",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(
                modifier = Modifier.height(14.dp)
            )

            Button(
                onClick = {
                    val intent = Intent(
                        context,
                        SpeakerIdentificationActivity::class.java
                    )
                    context.startActivity(intent)
                },

                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),

                shape = RoundedCornerShape(16.dp),

                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryColor,
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = "👤 Speaker Identification",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }


            Spacer(
                modifier =
                    Modifier.height(18.dp)
            )


            /*
             * ==================================================
             * BOTTOM DESCRIPTION
             * ==================================================
             */

            Text(

                text =

                    if (isMonitoring)

                        "Listening and analyzing voice..."

                    else


                        "Press Start to begin live monitoring",

                color =
                    SecondaryColor,

                fontSize =
                    12.sp,

                textAlign =
                    TextAlign.Center
            )
        }


        /*
         * ========================================================
         * INFORMATION DIALOG
         * ========================================================
         */

        if (showInfoDialog) {

            AlertDialog(

                onDismissRequest = {

                    showInfoDialog = false
                },


                /*
                 * ==================================================
                 * DIALOG TITLE
                 * ==================================================
                 */

                title = {

                    Text(

                        text =
                            "About Deepfake Voice",

                        color =
                            PrimaryColor,

                        fontSize =
                            22.sp,

                        fontWeight =
                            FontWeight.Bold
                    )
                },


                /*
                 * ==================================================
                 * DIALOG CONTENT
                 * ==================================================
                 */

                text = {

                    Column {


                        Text(

                            text =
                                "Deepfake Voice Detection is an " +
                                        "AI-powered real-time voice " +
                                        "authenticity monitoring application " +
                                        "designed to analyze live audio and " +
                                        "identify whether the detected voice " +
                                        "is likely to be real or " +
                                        "synthetically generated.",

                            color =
                                TextColor,

                            fontSize =
                                14.sp,

                            lineHeight =
                                21.sp
                        )


                        Spacer(
                            modifier =
                                Modifier.height(18.dp)
                        )


                        /*
                         * FEATURES
                         */

                        Text(

                            text =
                                "Features",

                            color =
                                SecondaryColor,

                            fontSize =
                                17.sp,

                            fontWeight =
                                FontWeight.Bold
                        )


                        Spacer(
                            modifier =
                                Modifier.height(8.dp)
                        )


                        Text(

                            text =
                                "• Real-time microphone monitoring\n" +
                                        "• AI-based voice authenticity detection\n" +
                                        "• Deepfake voice classification\n" +
                                        "• Confidence score display\n" +
                                        "• Continuous live analysis",

                            color =
                                TextColor,

                            fontSize =
                                14.sp,

                            lineHeight =
                                21.sp
                        )


                        Spacer(
                            modifier =
                                Modifier.height(20.dp)
                        )


                        /*
                         * DEVELOPER
                         */

                        Text(

                            text =
                                "Developer",

                            color =
                                SecondaryColor,

                            fontSize =
                                17.sp,

                            fontWeight =
                                FontWeight.Bold
                        )


                        Spacer(
                            modifier =
                                Modifier.height(6.dp)
                        )


                        Text(

                            text =
                                "Connect with me on LinkedIn",

                            color =
                                TextColor,

                            fontSize =
                                14.sp
                        )


                        Spacer(
                            modifier =
                                Modifier.height(8.dp)
                        )


                        /*
                         * LINKEDIN BUTTON
                         */

                        TextButton(

                            onClick = {

                                val intent =
                                    Intent(

                                        Intent.ACTION_VIEW,

                                        Uri.parse(
                                            "https://www.linkedin.com/in/rjunm/"
                                        )
                                    )


                                try {

                                    context.startActivity(
                                        intent
                                    )

                                } catch (_: Exception) {
                                }
                            }
                        ) {

                            Text(

                                text =
                                    "Arjun M",

                                color =
                                    AccentColor,

                                fontSize =
                                    15.sp,

                                fontWeight =
                                    FontWeight.Bold
                            )
                        }
                    }
                },


                /*
                 * ==================================================
                 * CLOSE BUTTON
                 * ==================================================
                 */

                confirmButton = {

                    TextButton(

                        onClick = {

                            showInfoDialog = false
                        }
                    ) {

                        Text(

                            text =
                                "Close",

                            color =
                                PrimaryColor,

                            fontWeight =
                                FontWeight.Bold
                        )
                    }
                },


                /*
                 * ==================================================
                 * DIALOG BACKGROUND
                 * ==================================================
                 */

                containerColor =
                    CardColor
            )
        }
    }
}