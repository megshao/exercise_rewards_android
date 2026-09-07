package com.megshao.exerciserewards.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.ui.platform.LocalContext
import com.megshao.exerciserewards.AppContainer
import com.megshao.exerciserewards.ExerciseRewardsApplication

/**
 * 取得（或建立）一個吃 [AppContainer] 的 ViewModel。
 *
 * **為什麼不用 DI 框架**：整個 App 只有一個 container，用 Application 拿就好；
 * 多一層 Hilt 只是為了把同一個物件換一種方式傳下來。
 */
@Composable
public inline fun <reified VM : ViewModel> appViewModel(
    key: String? = null,
    crossinline create: (AppContainer, Context) -> VM,
): VM {
    val application = LocalContext.current.applicationContext as ExerciseRewardsApplication
    return viewModel(
        key = key,
        factory = viewModelFactory {
            initializer { create(application.container, application) }
        },
    )
}

/** 直接拿 container（不需要 ViewModel 的地方，例如根導覽讀偏好）。 */
@Composable
public fun rememberAppContainer(): AppContainer =
    (LocalContext.current.applicationContext as ExerciseRewardsApplication).container
