package com.example.cosyvoice.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.compose.rememberAsyncImagePainter

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    var showPeople by remember { mutableStateOf(false) }
    var selectedPerson by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val people = listOf("woon", "minjoon", "gyeongyeon")

    val screenWidth = with(LocalDensity.current) { context.resources.displayMetrics.widthPixels.toDp() }
    val imageWidth = (screenWidth - 48.dp) / 3
    val imageHeight = imageWidth * 1.2f

    if (!showPeople) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(onClick = { showPeople = true }) {
                Text("Start")
            }
        }
    } else if (selectedPerson == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                people.forEach { person ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val extension = if (person == "gyeongyeon") "png" else "jpg"
                        val painter = rememberAsyncImagePainter("file:///android_asset/person/$person.$extension")
                        Image(
                            painter = painter,
                            contentDescription = "Photo of $person",
                            modifier = Modifier
                                .width(imageWidth)
                                .height(imageHeight)
                                .clickable { selectedPerson = person }
                        )
                        Text(
                            text = person.capitalize(),
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
            Button(
                onClick = { showPeople = false },
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text("뒤로가기")
            }
        }
    } else {
        NavHost(navController = navController, startDestination = "main") {
            composable("main") {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // 중앙 버튼 묶음
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Button(
                                onClick = { navController.navigate("exercise/$selectedPerson") },
                                modifier = Modifier.size(120.dp, 60.dp)
                            ) {
                                Text("체조 영상")
                            }
                            Button(
                                onClick = { navController.navigate("lipsync/$selectedPerson") },
                                modifier = Modifier.size(120.dp, 60.dp)
                            ) {
                                Text("립싱크 영상")
                            }
                            Button(
                                onClick = { navController.navigate("voice") },
                                modifier = Modifier.size(120.dp, 60.dp)
                            ) {
                                Text("음성 녹음")
                            }
                        }
                    }

                    // 하단 중앙 '뒤로가기' 버튼
                    Button(
                        onClick = { selectedPerson = null },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp)
                    ) {
                        Text("뒤로가기")
                    }
                }
            }

            composable("exercise/{person}") { backStackEntry ->
                val person = backStackEntry.arguments?.getString("person") ?: selectedPerson!!
                ExerciseScreen(navController, person)
            }
            composable("lipsync/{person}") { backStackEntry ->
                val person = backStackEntry.arguments?.getString("person") ?: selectedPerson!!
                LipSyncScreen(navController, person)
            }
            composable("voice") {
                    backStackEntry ->
                val person = backStackEntry.arguments?.getString("person") ?: selectedPerson!!
                VoiceScreen(navController, person) }
        }
    }
}