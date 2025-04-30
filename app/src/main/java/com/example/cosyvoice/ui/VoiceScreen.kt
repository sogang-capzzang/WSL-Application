package com.example.cosyvoice.ui

import AudioUtils
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import com.example.cosyvoice.BuildConfig
import com.example.cosyvoice.util.TTSClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.style.TextAlign
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceScreen(navController: NavHostController, person: String) {
    val context = LocalContext.current
    var isRecording by remember { mutableStateOf(false) }
    val audioUtils = remember { AudioUtils(context) }
    val ttsClient = remember { TTSClient() }
    val coroutineScope = rememberCoroutineScope()

    val viewModel = remember { VoiceScreenViewModel(coroutineScope) }
    val statusMessage by viewModel.statusMessage.collectAsState()
    val geminiResponse by viewModel.geminiResponse.collectAsState()

    val speechRecognizer = remember {
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        if (recognizer == null) {
            Log.e("VoiceScreen", "SpeechRecognizer 초기화 실패: 디바이스에서 음성 인식을 지원하지 않음")
            viewModel.updateStatusMessage("음성 인식 지원 안 됨")
        } else {
            Log.d("VoiceScreen", "SpeechRecognizer 초기화 성공")
        }
        recognizer
    }
    var recognizedText by remember { mutableStateOf<String?>(null) }

    var hasRecordPermission by remember { mutableStateOf(
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    ) }
    var hasStoragePermission by remember { mutableStateOf(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            true
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    ) }

    LaunchedEffect(Unit) {
        Log.d("VoiceScreen", "초기 권한 상태 - hasRecordPermission: $hasRecordPermission, hasStoragePermission: $hasStoragePermission")
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasRecordPermission = permissions[Manifest.permission.RECORD_AUDIO] == true
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            hasStoragePermission = permissions[Manifest.permission.WRITE_EXTERNAL_STORAGE] == true
        }
        Log.d("VoiceScreen", "권한 요청 결과 - hasRecordPermission: $hasRecordPermission, hasStoragePermission: $hasStoragePermission")
        if (!hasRecordPermission || !hasStoragePermission) {
            viewModel.updateStatusMessage("권한이 필요합니다. 설정에서 권한을 허용해주세요.")
        }
    }

    LaunchedEffect(Unit) {
        if (!hasRecordPermission || !hasStoragePermission) {
            Log.d("VoiceScreen", "권한 요청 다이얼로그 표시 요청")
            val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(Manifest.permission.RECORD_AUDIO)
            } else {
                arrayOf(
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            }
            permissionLauncher.launch(permissionsToRequest)
        } else {
            Log.d("VoiceScreen", "모든 권한이 이미 부여됨")
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            val newRecordPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            val newStoragePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                true
            } else {
                ContextCompat.checkSelfPermission(context,Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            }
            if (hasRecordPermission != newRecordPermission || hasStoragePermission != newStoragePermission) {
                hasRecordPermission = newRecordPermission
                hasStoragePermission = newStoragePermission
                Log.d("VoiceScreen", "권한 상태 업데이트: hasRecordPermission: $hasRecordPermission, hasStoragePermission: $hasStoragePermission")
            }
        }
    }

    LaunchedEffect(geminiResponse) {
        geminiResponse?.let { response ->
            val requestStartTime = System.currentTimeMillis()
            Log.d("VoiceScreen", "TTS 요청 시작: $requestStartTime")

            var hasReceivedData by mutableStateOf(false)
            ttsClient.requestTTS(response, person) { pcmData, size, isFirst, firstResponseTimestamp ->
                if (size > 0) {
                    hasReceivedData = true
                    viewModel.updateStatusMessage("TTS 데이터 수신 중... (${if (isFirst) "첫 번째" else "이어지는"} 데이터)")
                    if (isFirst && firstResponseTimestamp != null) {
                        val responseTime = firstResponseTimestamp - requestStartTime
                        Log.d("VoiceScreen", "TTS 요청부터 첫 번째 응답까지 걸린 시간: $responseTime ms")
                    }
                    audioUtils.playPcmDataStream(pcmData, size, isFirst, firstResponseTimestamp)
                } else if (hasReceivedData) {
                    viewModel.updateStatusMessage("모든 TTS 데이터 재생 완료")
                }
            }
        }
    }

    DisposableEffect(Unit) {
        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                viewModel.updateStatusMessage("음성 인식 준비 완료...")
                Log.d("VoiceScreen", "음성 인식 준비 완료")
            }

            override fun onBeginningOfSpeech() {
                viewModel.updateStatusMessage("음성 인식 시작...")
                Log.d("VoiceScreen", "음성 인식 시작: 음성 입력 감지됨")
            }

            override fun onRmsChanged(rmsdB: Float) {
                Log.d("VoiceScreen", "RMS 변경: $rmsdB")
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                Log.d("VoiceScreen", "버퍼 수신: ${buffer?.size ?: 0} 바이트")
            }

            override fun onEndOfSpeech() {
                viewModel.updateStatusMessage("음성 인식 종료...")
                Log.d("VoiceScreen", "음성 인식 종료")
            }

            override fun onError(error: Int) {
                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "오디오 입력 오류"
                    SpeechRecognizer.ERROR_CLIENT -> "클라이언트 오류"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "권한 부족"
                    SpeechRecognizer.ERROR_NETWORK -> "네트워크 오류"
                    SpeechRecognizer.ERROR_NO_MATCH -> "인식된 결과 없음"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "인식기 바쁨"
                    SpeechRecognizer.ERROR_SERVER -> "서버 오류"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "음성 입력 시간 초과"
                    else -> "알 수 없는 오류"
                }
                viewModel.updateStatusMessage("음성 인식 실패: $errorMessage")
                Log.e("VoiceScreen", "음성 인식 에러: $errorMessage (에러 코드: $error)")
                isRecording = false
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                recognizedText = matches?.firstOrNull()
                Log.d("VoiceScreen", "음성 인식 결과: $recognizedText")
                if (recognizedText != null) {
                    viewModel.updateStatusMessage("STT 완료: $recognizedText")
                    viewModel.callGeminiApi(recognizedText!!)
                }
                isRecording = false
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                Log.d("VoiceScreen", "부분 음성 인식 결과: $partial")
            }

            override fun onEvent(eventType: Int, params: Bundle?) {
                Log.d("VoiceScreen", "이벤트 발생: $eventType")
            }
        }
        speechRecognizer.setRecognitionListener(listener)

        onDispose {
            speechRecognizer.destroy()
            Log.d("VoiceScreen", "SpeechRecognizer 리소스 해제")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "음성 대화",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "뒤로가기",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
                )
            )
        },
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                // 상태 메시지
                AnimatedVisibility(
                    visible = statusMessage.isNotEmpty(),
                    enter = fadeIn(tween(500)),
                    exit = fadeOut(tween(500))
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp)
                            .shadow(8.dp, RoundedCornerShape(16.dp)),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = statusMessage,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier
                                .padding(16.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 음성 인식 버튼
                Button(
                    onClick = {
                        if (!hasRecordPermission || !hasStoragePermission) {
                            viewModel.updateStatusMessage("권한이 필요합니다. 설정에서 권한을 허용해주세요.")
                            val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                arrayOf(Manifest.permission.RECORD_AUDIO)
                            } else {
                                arrayOf(
                                    Manifest.permission.RECORD_AUDIO,
                                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                                )
                            }
                            permissionLauncher.launch(permissionsToRequest)
                            return@Button
                        }

                        if (!isRecording) {
                            viewModel.updateStatusMessage("음성 인식 중...")
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
                                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 3000L)
                                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
                                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
                            }
                            speechRecognizer.startListening(intent)
                            Log.d("VoiceScreen", "음성 인식 시작 요청")
                        } else {
                            viewModel.updateStatusMessage("음성 인식 중지...")
                            speechRecognizer.stopListening()
                            Log.d("VoiceScreen", "음성 인식 중지 요청")
                        }
                        isRecording = !isRecording
                    },
                    enabled = hasRecordPermission && hasStoragePermission,
                    modifier = Modifier
                        .size(130.dp)
                        .shadow(12.dp, RoundedCornerShape(60.dp)),
                    shape = RoundedCornerShape(60.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.primaryContainer
                                    )
                                ),
                                shape = RoundedCornerShape(100.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isRecording) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = if (isRecording) "음성 인식 중지" else "음성 인식 시작",
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 권한 설정 버튼
                AnimatedVisibility(
                    visible = !hasRecordPermission || !hasStoragePermission,
                    enter = fadeIn(tween(500)),
                    exit = fadeOut(tween(500))
                ) {
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            "설정으로 이동",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            audioUtils.release()
            Log.d("VoiceScreen", "AudioUtils 리소스 해제")
        }
    }
}

class VoiceScreenViewModel(private val coroutineScope: CoroutineScope) {
    private val _statusMessage = MutableStateFlow("대기 중...")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _geminiResponse = MutableStateFlow<String?>(null)
    val geminiResponse: StateFlow<String?> = _geminiResponse.asStateFlow()

    fun updateStatusMessage(message: String) {
        _statusMessage.value = message
    }

    fun callGeminiApi(userInput: String) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val response = callGeminiApiInternal(userInput)
                if (response != null) {
                    _statusMessage.value = "Gemini 응답: $response"
                    _geminiResponse.value = response
                } else {
                    _statusMessage.value = "요청 실패"
                }
            } catch (e: Exception) {
                Log.e("VoiceScreen", "Gemini API 호출 중 오류: ${e.message}", e)
                _statusMessage.value = "요청 실패"
            }
        }
    }

    private var lastRequestTime = 0L

    private suspend fun callGeminiApiInternal(userInput: String): String? {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty()) {
            Log.e("VoiceScreen", "Gemini API 키가 설정되지 않음")
            return null
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()

        val currentTime = System.currentTimeMillis()
        val timeSinceLastRequest = currentTime - lastRequestTime
        if (timeSinceLastRequest < 1000) {
            Log.d("VoiceScreen", "요청 간 딜레이 적용: ${1000 - timeSinceLastRequest}ms 대기")
            delay(1000 - timeSinceLastRequest)
        }
        lastRequestTime = System.currentTimeMillis()

        val systemPrompt = """
            당신은 어르신을 돌보는 AI agent이에요. 어르신에게 보호자의 목소리로 따뜻하고 배려 깊은 말투로 대화해야 해요. 
            항상 ~해요체를 사용해서 정중하고 친근하게 말해주세요. 답변은 3문장만 간결하게 말해주세요.
            예를 들어, 어르신, ~하세요. 또는 어르신, 밥은 잘 드셔야해요. 같은 말투를 사용하세요. 절대 의문문은 쓰지 말고 평서문으로만 대답하세요.
            지금까지의 말에 대한 답변은 하지 말고 무조건 앞으로 나올 사용자: 이후에 나오는 말에만 답변하세요.
            사용자: $userInput
        """.trimIndent()
        Log.d("VoiceScreen", "전송할 프롬프트: $systemPrompt")

        val requestBody = """
            {
                "contents": [
                    {
                        "parts": [
                            {
                                "text": "$systemPrompt"
                            }
                        ]
                    }
                ],
                "generationConfig": {
                    "temperature": 0.5,
                    "topP": 0.9
                }
            }
        """.trimIndent()

        Log.d("VoiceScreen", "Gemini API 요청: $requestBody")

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "응답 본문 없음"
                    Log.e("VoiceScreen", "Gemini API 요청 실패: ${response.code}, ${response.message}")
                    Log.e("VoiceScreen", "오류 응답 본문: $errorBody")
                    return null
                }
                val responseBody = response.body?.string()
                Log.d("VoiceScreen", "Gemini API 응답: $responseBody")

                val json = JSONObject(responseBody)
                val candidates = json.getJSONArray("candidates")
                if (candidates.length() == 0) {
                    Log.e("VoiceScreen", "Gemini API 응답에 candidates가 없음")
                    return null
                }
                val firstCandidate = candidates.getJSONObject(0)
                val content = firstCandidate.getJSONObject("content")
                val parts = content.getJSONArray("parts")
                if (parts.length() == 0) {
                    Log.e("VoiceScreen", "Gemini API 응답에 parts가 없음")
                    return null
                }
                val text = parts.getJSONObject(0).getString("text")

                val usageMetadata = json.optJSONObject("usageMetadata")
                if (usageMetadata != null) {
                    val promptTokens = usageMetadata.getInt("promptTokenCount")
                    val candidatesTokens = usageMetadata.getInt("candidatesTokenCount")
                    val totalTokens = usageMetadata.getInt("totalTokenCount")
                    Log.d("VoiceScreen", "토큰 사용량 - Prompt: $promptTokens, Candidates: $candidatesTokens, Total: $totalTokens")
                }

                if (text.contains("사용자: ")) {
                    Log.e("VoiceScreen", "응답에 '사용자: ' 포함: $text")
                    return null
                }

                return text
            }
        } catch (e: Exception) {
            Log.e("VoiceScreen", "Gemini API 호출 중 오류: ${e.message}", e)
            return null
        }
    }
}