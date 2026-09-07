package com.megshao.exerciserewards

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.megshao.exerciserewards.ui.AppRoot
import com.megshao.exerciserewards.ui.theme.ExerciseRewardsTheme

public class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ExerciseRewardsTheme {
                AppRoot()
            }
        }
    }
}
