package com.example.cosyvoice.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.compose.rememberAsyncImagePainter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    var showPeople by remember { mutableStateOf(false) }
    var selectedPerson by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val people = listOf("woon", "minjoon", "gyeongyeon")

    val screenWidth = with(LocalDensity.current) { context.resources.displayMetrics.widthPixels.toDp() }

    Scaffold(
        topBar = {
            if (showPeople && selectedPerson == null) {
                TopAppBar(
                    title = { Text("보호자 선택") },
                    navigationIcon = {
                        IconButton(onClick = { showPeople = false }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "뒤로가기")
                        }
                    }
                )
            } else if (selectedPerson != null && navController.currentBackStackEntryAsState().value?.destination?.route == "home") {
                TopAppBar(
                    title = { Text(selectedPerson!!.replaceFirstChar { it.uppercase() }) },
                    navigationIcon = {
                        IconButton(onClick = { selectedPerson = null }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "뒤로가기")
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (!showPeople) {
                Button(
                    onClick = { showPeople = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("시작", color = MaterialTheme.colorScheme.onPrimary)
                }
            } else if (selectedPerson == null) {
                Text(
                    "누구를 선택하시겠어요?",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 32.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Top
                ) {
                    people.forEach { person ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedPerson = person }
                                .padding(8.dp)
                        ) {
                            val extension = "jpg"
                            val painter = rememberAsyncImagePainter("file:///android_asset/person/$person.$extension")

                            val itemWidth = (screenWidth - 48.dp) / 3
                            val itemHeight = itemWidth * 1.5f

                            Card(
                                shape = MaterialTheme.shapes.medium,
                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                            ) {
                                Image(
                                    painter = painter,
                                    contentDescription = "$person 사진",
                                    modifier = Modifier
                                        .width(itemWidth)
                                        .height(itemHeight)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = person.replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                NavHost(navController = navController, startDestination = "home") {
                    composable("home") {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 60.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                CircularButton(
                                    text = "립싱크 영상",
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    textColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    onClick = { navController.navigate("lipsync/$selectedPerson") }
                                )
                                CircularButton(
                                    text = "음성 대화",
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    textColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                    onClick = { navController.navigate("voice/$selectedPerson") }
                                )
                                CircularButton(
                                    text = "체조 영상",
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    textColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    onClick = { navController.navigate("exercise/$selectedPerson") }
                                )
                            }
                        }
                    }
                    composable("lipsync/{person}") { backStackEntry ->
                        val person = backStackEntry.arguments?.getString("person") ?: selectedPerson!!
                        LipSyncScreen(navController, person)
                    }
                    composable("voice/{person}") { backStackEntry ->
                        val person = backStackEntry.arguments?.getString("person") ?: selectedPerson!!
                        VoiceScreen(navController, person)
                    }
                    composable("exercise/{person}") { backStackEntry ->
                        val person = backStackEntry.arguments?.getString("person") ?: selectedPerson!!
                        ExerciseScreen(navController, person)
                    }
                }
            }
        }
    }
}

@Composable
fun CircularButton(
    text: String,
    containerColor: androidx.compose.ui.graphics.Color,
    textColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.dp
    val buttonSize = (screenWidthDp * 0.3f).coerceIn(100.dp..160.dp)

    Button(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        colors = ButtonDefaults.buttonColors(containerColor = containerColor),
        modifier = Modifier.size(buttonSize),
        contentPadding = PaddingValues()
    ) {
        Text(
            text = text,
            color = textColor,
            style = MaterialTheme.typography.titleSmall.copy(
                fontSize = if (buttonSize > 140.dp) 18.sp else 14.sp,
                fontWeight = FontWeight.Bold
            ),
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}