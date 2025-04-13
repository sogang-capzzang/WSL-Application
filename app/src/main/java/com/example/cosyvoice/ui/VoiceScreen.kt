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
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import com.example.cosyvoice.BuildConfig
import com.example.cosyvoice.util.TTSClient
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

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

    // SpeechRecognizer 초기화
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

    // 권한 상태 관리
    var hasRecordPermission by remember { mutableStateOf(
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    ) }
    var hasStoragePermission by remember { mutableStateOf(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            true // Android 13 이상에서는 WRITE_EXTERNAL_STORAGE 불필요
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    ) }

    // 초기 권한 상태 로그
    LaunchedEffect(Unit) {
        Log.d("VoiceScreen", "초기 권한 상태 - hasRecordPermission: $hasRecordPermission, hasStoragePermission: $hasStoragePermission")
    }

    // 권한 요청 런처
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

    // 권한 확인 및 요청
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

    // 설정 화면에서 돌아왔을 때 권한 상태 업데이트
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000) // 1초마다 권한 상태 확인
            val newRecordPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            val newStoragePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                true
            } else {
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            }
            if (hasRecordPermission != newRecordPermission || hasStoragePermission != newStoragePermission) {
                hasRecordPermission = newRecordPermission
                hasStoragePermission = newStoragePermission
                Log.d("VoiceScreen", "권한 상태 업데이트: hasRecordPermission: $hasRecordPermission, hasStoragePermission: $hasStoragePermission")
            }
        }
    }

    // Gemini 응답 처리 및 CosyVoice2 서버로 전송
    LaunchedEffect(geminiResponse) {
        geminiResponse?.let { response ->
            val requestStartTime = System.currentTimeMillis()
            Log.d("VoiceScreen", "TTS 요청 시작: $requestStartTime")

            ttsClient.requestTTS(response, person) { pcmData, size, isFirst, firstResponseTimestamp ->
                if (size > 0) {
                    viewModel.updateStatusMessage("TTS 데이터 수신 중... (${if (isFirst) "첫 번째" else "이어지는"} 데이터)")
                    if (isFirst && firstResponseTimestamp != null) {
                        val responseTime = firstResponseTimestamp - requestStartTime
                        Log.d("VoiceScreen", "TTS 요청부터 첫 번째 응답까지 걸린 시간: $responseTime ms")
                    }
                    audioUtils.playPcmDataStream(pcmData, size, isFirst, firstResponseTimestamp)
                } else {
                    viewModel.updateStatusMessage("모든 TTS PCM 데이터 재생 완료")
                }
            }
        }
    }


    // SpeechRecognizer 리스너 설정
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
                    // Gemini API 호출
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.weight(1f)
        ) {
            Text(statusMessage, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(16.dp))

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
                enabled = hasRecordPermission && hasStoragePermission
            ) {
                Text(if (isRecording) "음성 인식 중지" else "음성 인식 시작")
            }

            // 권한이 거부된 경우 설정 화면으로 이동 버튼
            if (!hasRecordPermission || !hasStoragePermission) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    }
                ) {
                    Text("설정으로 이동")
                }
            }
        }

        Button(
            onClick = { navController.popBackStack() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Text("뒤로가기")
        }
    }

    // 컴포넌트 종료 시 리소스 해제
    DisposableEffect(Unit) {
        onDispose {
            audioUtils.release()
            Log.d("VoiceScreen", "AudioUtils 리소스 해제")
        }
    }
}

// ViewModel 클래스 정의
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

    // 마지막 요청 시간
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

        // 요청 간 딜레이 (최소 1초)
        val currentTime = System.currentTimeMillis()
        val timeSinceLastRequest = currentTime - lastRequestTime
        if (timeSinceLastRequest < 1000) {
            Log.d("VoiceScreen", "요청 간 딜레이 적용: ${1000 - timeSinceLastRequest}ms 대기")
            delay(1000 - timeSinceLastRequest)
        }
        lastRequestTime = System.currentTimeMillis()

        val systemPrompt = """
            당신은 어르신을 돌보는 AI agent이에요. 어르신에게 보호자의 목소리로 따뜻하고 배려 깊은 말투로 대화해야 해요. 
            항상 ~해요체를 사용해서 정중하고 친근하게 말해주세요. 진짜 대화하듯이 자연스럽게 말하고, 어르신의 말에 적절히 반응해주세요. 
            답변은 3문장정도로 간결하게 말해주세요.예를 들어, 어르신, ~하세요. 또는 어르신, 밥은 잘 드셔야해요. 같은 말투를 사용하는데, 절대 의문문은 쓰지말고 평서문으로만 대답하세요.
            지금까지의 말에 대한 답변은 하지말고 무주건 사용자 : 이후에 나오는 말에만 답변하세요.
        """.trimIndent()
        val fullPrompt = "$systemPrompt 사용자: $userInput"
        Log.d("VoiceScreen", "전송할 프롬프트: $fullPrompt")

        // 문장이 끊기지 않도록 재시도 로직
        var finalContent = ""
        var attempt = 0
        val maxAttempts = 2

        while (attempt < maxAttempts) {
            val requestBody = """
                {
                    "contents": [
                        {
                            "parts": [
                                {
                                    "text": "$fullPrompt${if (finalContent.isNotEmpty()) "\n이어 말해주세요: $finalContent" else ""}"
                                }
                            ]
                        }
                    ],
                    "generationConfig": {
                        "maxOutputTokens": 60,
                        "temperature": 0.5,
                        "topP": 0.9
                    }
                }
            """.trimIndent()

            Log.d("VoiceScreen", "Gemini API 요청: $requestBody")

            // HTTP 요청
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

                    // JSON 파싱
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
                    val finishReason = firstCandidate.getString("finishReason")

                    // 토큰 사용량 로그
                    val usageMetadata = json.optJSONObject("usageMetadata")
                    if (usageMetadata != null) {
                        val promptTokens = usageMetadata.getInt("promptTokenCount")
                        val candidatesTokens = usageMetadata.getInt("candidatesTokenCount")
                        val totalTokens = usageMetadata.getInt("totalTokenCount")
                        Log.d("VoiceScreen", "토큰 사용량 - Prompt: $promptTokens, Candidates: $candidatesTokens, Total: $totalTokens")
                    }
                    finalContent += text

                    // 문장이 끊겼는지 확인
                    when (finishReason) {
                        "STOP" -> {
                            // 문장이 완성됨
                            return finalContent
                        }
                        "MAX_TOKENS" -> {
                            attempt++
                            Log.d("VoiceScreen", "문장이 끊김, 재시도: $attempt")
                        }
                        else -> {
                            Log.e("VoiceScreen", "알 수 없는 finishReason: $finishReason")
                            return null
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("VoiceScreen", "Gemini API 호출 중 오류: ${e.message}", e)
                return null
            }
        }

        // 최대 재시도 후에도 문장이 완성되지 않음
        return if (finalContent.isNotEmpty()) {
            finalContent
        } else {
            Log.e("VoiceScreen", "최대 재시도 후에도 문장 완성 실패")
            null
        }
    }
}