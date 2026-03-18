package com.filevault.pro.presentation.screen.lock

import android.security.keystore.KeyPermanentlyInvalidatedException
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.filevault.pro.data.preferences.AppPreferences
import java.security.MessageDigest

@Composable
fun AppLockScreen(
    appPreferences: AppPreferences,
    onUnlocked: () -> Unit
) {
    val context = LocalContext.current
    var enteredPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var attempts by remember { mutableStateOf(0) }
    val appLockType by appPreferences.appLockType.collectAsState("NONE")
    val storedHash by appPreferences.pinHash.collectAsState(null)

    LaunchedEffect(appLockType) {
        if (appLockType == "BIOMETRIC") {
            val biometricManager = BiometricManager.from(context)
            if (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
                BiometricManager.BIOMETRIC_SUCCESS) {
                triggerBiometric(context, onUnlocked) { error = it }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.background)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = "Locked",
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "FileVault Pro",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Text(
                "Enter your PIN to unlock",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(0.8f)
            )
            Spacer(Modifier.height(40.dp))

            PinDots(enteredPin = enteredPin, maxLength = 4)
            Spacer(Modifier.height(8.dp))

            AnimatedVisibility(error != null, enter = fadeIn(), exit = fadeOut()) {
                Text(
                    error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(24.dp))

            PinPad(
                onDigit = { digit ->
                    if (enteredPin.length < 4) {
                        enteredPin += digit
                        error = null
                        if (enteredPin.length == 4) {
                            val hash = sha256(enteredPin)
                            if (hash == storedHash) {
                                onUnlocked()
                            } else {
                                attempts++
                                error = if (attempts >= 5) "Too many attempts. Try again later."
                                        else "Incorrect PIN. ${5 - attempts} attempts remaining."
                                enteredPin = ""
                            }
                        }
                    }
                },
                onDelete = {
                    if (enteredPin.isNotEmpty()) enteredPin = enteredPin.dropLast(1)
                    error = null
                },
                onBiometric = if (appLockType == "BIOMETRIC") ({
                    triggerBiometric(context, onUnlocked) { error = it }
                }) else null
            )
        }
    }
}

@Composable
private fun PinDots(enteredPin: String, maxLength: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(maxLength) { index ->
            val filled = index < enteredPin.length
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(
                        if (filled) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onPrimary.copy(0.3f)
                    )
            )
        }
    }
}

@Composable
private fun PinPad(
    onDigit: (String) -> Unit,
    onDelete: () -> Unit,
    onBiometric: (() -> Unit)?
) {
    val digits = listOf("1","2","3","4","5","6","7","8","9","","0","DEL")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        digits.chunked(3).forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                row.forEach { key ->
                    when (key) {
                        "" -> {
                            if (onBiometric != null) {
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.onPrimary.copy(0.1f))
                                        .clickable { onBiometric() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Fingerprint, "Biometric",
                                        tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(32.dp))
                                }
                            } else {
                                Spacer(Modifier.size(72.dp))
                            }
                        }
                        "DEL" -> {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.onPrimary.copy(0.1f))
                                    .clickable { onDelete() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Backspace, "Delete",
                                    tint = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                        else -> {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.onPrimary.copy(0.15f))
                                    .clickable { onDigit(key) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    key,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun sha256(input: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}

private fun triggerBiometric(
    context: android.content.Context,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    val activity = context as? FragmentActivity ?: return
    val executor = ContextCompat.getMainExecutor(context)
    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            onSuccess()
        }
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                onError(errString.toString())
            }
        }
        override fun onAuthenticationFailed() {
            onError("Biometric authentication failed. Try again.")
        }
    }

    val prompt = BiometricPrompt(activity, executor, callback)
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Unlock FileVault Pro")
        .setSubtitle("Use your biometric to access your file catalog")
        .setNegativeButtonText("Use PIN")
        .build()
    prompt.authenticate(info)
}
