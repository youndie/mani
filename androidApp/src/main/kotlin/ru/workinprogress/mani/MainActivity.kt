package ru.workinprogress.mani

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.profileinstaller.ProfileVerifier
import androidx.profileinstaller.ProfileVerifier.CompilationStatus.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.dsl.module

@OptIn(ExperimentalComposeUiApi::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            App(
                modifier = Modifier.semantics {
                    testTagsAsResourceId = true
                },
                platformModules = listOf(
                    module {
                        single<Context> { this@MainActivity }
                    }
                )
            )
        }
    }


    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            logCompilationStatus()
        }
    }

    private suspend fun logCompilationStatus() {
        withContext(Dispatchers.IO) {
            val status = ProfileVerifier.getCompilationStatusAsync().get()
            when (status.profileInstallResultCode) {
                RESULT_CODE_NO_PROFILE ->
                    Log.d(TAG, "ProfileInstaller: Baseline Profile not found")

                RESULT_CODE_COMPILED_WITH_PROFILE ->
                    Log.d(TAG, "ProfileInstaller: Compiled with profile")

                RESULT_CODE_PROFILE_ENQUEUED_FOR_COMPILATION ->
                    Log.d(TAG, "ProfileInstaller: Enqueued for compilation")

                RESULT_CODE_COMPILED_WITH_PROFILE_NON_MATCHING ->
                    Log.d(TAG, "ProfileInstaller: App was installed through Play store")

                RESULT_CODE_ERROR_PACKAGE_NAME_DOES_NOT_EXIST ->
                    Log.d(TAG, "ProfileInstaller: PackageName not found")

                RESULT_CODE_ERROR_CACHE_FILE_EXISTS_BUT_CANNOT_BE_READ ->
                    Log.d(TAG, "ProfileInstaller: Cache file exists but cannot be read")

                RESULT_CODE_ERROR_CANT_WRITE_PROFILE_VERIFICATION_RESULT_CACHE_FILE ->
                    Log.d(TAG, "ProfileInstaller: Can't write cache file")

                RESULT_CODE_ERROR_UNSUPPORTED_API_VERSION ->
                    Log.d(TAG, "ProfileInstaller: Enqueued for compilation")

                else ->
                    Log.d(TAG, "ProfileInstaller: Profile not compiled or enqueued")
            }
        }
    }
}

val TAG = "MANI_PROFILE"