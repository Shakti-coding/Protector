package com.filevault.pro.presentation.screen.lock

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import kotlinx.coroutines.launch
import java.security.MessageDigest

private enum class SetupStep {
    OVERVIEW, VERIFY_CURRENT, ENTER_NEW, CONFIRM_NEW, SUCCESS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLockSetupScreen(
    appPreferences: AppPreferences,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val appLockEnabled by appPreferences.appLockEnabled.collectAsState(false)
    val appLockType by appPreferences.appLockType.collectAsState("NONE")
    val storedHash by appPreferences.pinHash.collectAsState(null)
    val lockTimeoutMinutes by appPreferences.lockTimeoutMinutes.collectAsState(5)

    var step by remember { mutableStateOf(SetupStep.OVERVIEW) }
    var actionMode by remember { mutableStateOf("SET_PIN") }
    var newPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val hasBiometric = remember {
        BiometricManager.from(context)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
                BiometricManager.BIOMETRIC_SUCCESS
    }

    fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun savePin(pin: String, enableBio: Boolean) {
        scope.launch {
            appPreferences.setPinHash(sha256(pin))
            appPreferences.setAppLockEnabled(true)
            appPreferences.setAppLockType(if (enableBio) "BIOMETRIC" else "PIN")
            step = SetupStep.SUCCESS
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("App Lock", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (step == SetupStep.OVERVIEW) onBack()
                        else step = SetupStep.OVERVIEW
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (step) {
                SetupStep.OVERVIEW -> OverviewContent(
                    appLockEnabled = appLockEnabled,
                    appLockType = appLockType,
                    lockTimeoutMinutes = lockTimeoutMinutes,
                    hasBiometric = hasBiometric,
                    onEnableClick = {
                        actionMode = "SET_PIN"
                        newPin = ""
                        error = null
                        step = SetupStep.ENTER_NEW
                    },
                    onChangePinClick = {
                        actionMode = "CHANGE_PIN"
                        newPin = ""
                        error = null
                        step = SetupStep.VERIFY_CURRENT
                    },
                    onToggleBiometric = {
                        scope.launch {
                            val newType = if (appLockType == "BIOMETRIC") "PIN" else "BIOMETRIC"
                            appPreferences.setAppLockType(newType)
                            snackbarHostState.showSnackbar(
                                if (newType == "BIOMETRIC") "Biometric enabled" else "Biometric disabled"
                            )
                        }
                    },
                    onDisableClick = {
                        actionMode = "DISABLE"
                        error = null
                        step = SetupStep.VERIFY_CURRENT
                    },
                    onTimeoutChange = { minutes ->
                        scope.launch { appPreferences.setLockTimeoutMinutes(minutes) }
                    }
                )

                SetupStep.VERIFY_CURRENT -> SetupPinEntryScreen(
                    title = "Enter Current PIN",
                    subtitle = "Verify your identity before making changes",
                    error = error,
                    onPinComplete = { pin ->
                        if (sha256(pin) == storedHash) {
                            error = null
                            when (actionMode) {
                                "DISABLE" -> {
                                    scope.launch {
                                        appPreferences.setAppLockEnabled(false)
                                        appPreferences.setAppLockType("NONE")
                                        onBack()
                                    }
                                }
                                else -> step = SetupStep.ENTER_NEW
                            }
                        } else {
                            error = "Incorrect PIN. Please try again."
                        }
                    }
                )

                SetupStep.ENTER_NEW -> SetupPinEntryScreen(
                    title = "Set New PIN",
                    subtitle = "Choose a 4-digit PIN to protect FileVault",
                    error = error,
                    onPinComplete = { pin ->
                        newPin = pin
                        error = null
                        step = SetupStep.CONFIRM_NEW
                    }
                )

                SetupStep.CONFIRM_NEW -> SetupPinEntryScreen(
                    title = "Confirm PIN",
                    subtitle = "Enter your new PIN again to confirm",
                    error = error,
                    onPinComplete = { pin ->
                        if (pin == newPin) {
                            savePin(pin, hasBiometric && appLockType == "BIOMETRIC")
                        } else {
                            error = "PINs do not match. Try again."
                        }
                    }
                )

                SetupStep.SUCCESS -> SuccessContent(
                    hasBiometric = hasBiometric,
                    onEnableBiometric = {
                        val activity = context as? FragmentActivity ?: return@SuccessContent
                        val executor = ContextCompat.getMainExecutor(context)
                        val callback = object : BiometricPrompt.AuthenticationCallback() {
                            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                scope.launch {
                                    appPreferences.setAppLockType("BIOMETRIC")
                                    snackbarHostState.showSnackbar("Biometric enabled!")
                                }
                            }
                            override fun onAuthenticationError(code: Int, str: CharSequence) {}
                            override fun onAuthenticationFailed() {}
                        }
                        val prompt = BiometricPrompt(activity, executor, callback)
                        val info = BiometricPrompt.PromptInfo.Builder()
                            .setTitle("Enable Biometric")
                            .setSubtitle("Register your fingerprint as an alternative to PIN")
                            .setNegativeButtonText("Skip")
                            .build()
                        prompt.authenticate(info)
                    },
                    onDone = onBack
                )
            }
        }
    }
}

@Composable
private fun OverviewContent(
    appLockEnabled: Boolean,
    appLockType: String,
    lockTimeoutMinutes: Int,
    hasBiometric: Boolean,
    onEnableClick: () -> Unit,
    onChangePinClick: () -> Unit,
    onToggleBiometric: () -> Unit,
    onDisableClick: () -> Unit,
    onTimeoutChange: (Int) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier.size(80.dp).clip(CircleShape).background(
                            if (appLockEnabled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (appLockEnabled) Icons.Default.Lock else Icons.Default.LockOpen,
                            null, modifier = Modifier.size(40.dp),
                            tint = if (appLockEnabled) MaterialTheme.colorScheme.onPrimary
                                   else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (appLockEnabled) "App Lock Enabled" else "App Lock Disabled",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
                    )
                    if (appLockEnabled) {
                        Text(
                            when (appLockType) {
                                "BIOMETRIC" -> "PIN + Biometric"
                                else -> "PIN only"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                        )
                    }
                }
            }
        }

        if (!appLockEnabled) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(0.4f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Set up App Lock", style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Protect your file catalog with a 4-digit PIN. Optionally add biometric authentication.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = onEnableClick, modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Lock, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Set Up PIN Lock")
                        }
                    }
                }
            }
        } else {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Security Actions", style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        ActionRow(Icons.Default.Pin, "Change PIN", "Update your 4-digit PIN", onClick = onChangePinClick)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(0.3f), modifier = Modifier.padding(vertical = 4.dp))
                        if (hasBiometric) {
                            ActionRow(
                                Icons.Default.Fingerprint,
                                if (appLockType == "BIOMETRIC") "Disable Biometric" else "Enable Biometric",
                                if (appLockType == "BIOMETRIC") "Switch to PIN only" else "Use fingerprint to unlock",
                                onClick = onToggleBiometric
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(0.3f), modifier = Modifier.padding(vertical = 4.dp))
                        }
                        ActionRow(
                            Icons.Default.LockOpen, "Disable App Lock",
                            "Remove PIN protection", tint = MaterialTheme.colorScheme.error,
                            onClick = onDisableClick
                        )
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Auto-Lock After", style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text("Lock app when it has been in background for:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                        Spacer(Modifier.height(12.dp))
                        listOf(
                            0 to "Immediately",
                            1 to "1 minute",
                            5 to "5 minutes",
                            15 to "15 minutes",
                            30 to "30 minutes"
                        ).forEach { (mins, label) ->
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onTimeoutChange(mins) }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = lockTimeoutMinutes == mins, onClick = { onTimeoutChange(mins) })
                                Spacer(Modifier.width(8.dp))
                                Text(label, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, null, modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("About App Lock", style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "• PIN is stored as a SHA-256 hash — never in plain text.\n" +
                        "• Biometric uses the device's secure hardware.\n" +
                        "• After 5 wrong attempts, a cooldown is applied.\n" +
                        "• App lock activates when the app is backgrounded past the timeout.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.6f),
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, color = tint)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
        }
        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurface.copy(0.3f))
    }
}

@Composable
private fun SuccessContent(
    hasBiometric: Boolean,
    onEnableBiometric: () -> Unit,
    onDone: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Box(
                modifier = Modifier.size(100.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(24.dp))
            Text("App Lock Enabled!", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                "FileVault Pro is now protected with a PIN.\nYou'll be asked for it every time you open the app.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(0.6f),
                textAlign = TextAlign.Center
            )
            if (hasBiometric) {
                Spacer(Modifier.height(20.dp))
                OutlinedButton(
                    onClick = onEnableBiometric,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Fingerprint, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Also Enable Biometric Unlock")
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onDone, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Done") }
        }
    }
}

@Composable
private fun SetupPinEntryScreen(
    title: String,
    subtitle: String,
    error: String?,
    onPinComplete: (String) -> Unit
) {
    var enteredPin by remember { mutableStateOf("") }
    var localError by remember(error) { mutableStateOf(error) }

    LaunchedEffect(error) { localError = error }

    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                listOf(MaterialTheme.colorScheme.primary.copy(0.85f), MaterialTheme.colorScheme.background)
            )
        ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(Icons.Default.Lock, null, modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onPrimary)
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(0.75f), textAlign = TextAlign.Center)
            Spacer(Modifier.height(32.dp))

            SetupPinDots(enteredPin = enteredPin, maxLength = 4)
            Spacer(Modifier.height(8.dp))

            AnimatedVisibility(localError != null, enter = fadeIn(), exit = fadeOut()) {
                Text(
                    localError ?: "", color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(24.dp))
            SetupPinPad(
                onDigit = { digit ->
                    if (enteredPin.length < 4) {
                        enteredPin += digit
                        localError = null
                        if (enteredPin.length == 4) {
                            val pin = enteredPin
                            enteredPin = ""
                            onPinComplete(pin)
                        }
                    }
                },
                onDelete = {
                    if (enteredPin.isNotEmpty()) enteredPin = enteredPin.dropLast(1)
                    localError = null
                }
            )
        }
    }
}

@Composable
private fun SetupPinDots(enteredPin: String, maxLength: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(maxLength) { index ->
            val filled = index < enteredPin.length
            Box(
                modifier = Modifier.size(18.dp).clip(CircleShape).background(
                    if (filled) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onPrimary.copy(0.3f)
                )
            )
        }
    }
}

@Composable
private fun SetupPinPad(
    onDigit: (String) -> Unit,
    onDelete: () -> Unit
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
                        "" -> Spacer(Modifier.size(72.dp))
                        "DEL" -> Box(
                            modifier = Modifier.size(72.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onPrimary.copy(0.1f))
                                .clickable { onDelete() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Backspace, "Delete",
                                tint = MaterialTheme.colorScheme.onPrimary)
                        }
                        else -> Box(
                            modifier = Modifier.size(72.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onPrimary.copy(0.15f))
                                .clickable { onDigit(key) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(key, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            }
        }
    }
}
