package dev.pranav.applock.features.intruder.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.pranav.applock.AppLockApplication
import dev.pranav.applock.features.lockscreen.ui.KeypadRow
import dev.pranav.applock.features.lockscreen.ui.PasswordIndicators
import dev.pranav.applock.ui.icons.Backspace

private const val MIN_GUEST_PASSWORD_LENGTH = 4

/**
 * Screen for setting up (or changing) the guest / decoy password used by the
 * Dual Password Intruder Monitoring System.
 *
 * The flow mirrors [dev.pranav.applock.features.setpassword.ui.SetPasswordScreen]:
 *  1. User enters a new PIN.
 *  2. User confirms the PIN.
 *  3. On match the SHA-256 hash is saved via [AppLockRepository.setGuestPassword].
 *
 * The guest password MUST differ from the admin password.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SetGuestPasswordScreen(navController: NavController) {
    var passwordState by remember { mutableStateOf("") }
    var confirmPasswordState by remember { mutableStateOf("") }
    var isConfirmationMode by remember { mutableStateOf(false) }

    var showMismatchError by remember { mutableStateOf(false) }
    var showLengthError by remember { mutableStateOf(false) }
    var showSameAsAdminError by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val appLockRepository = remember {
        (context.applicationContext as? AppLockApplication)?.appLockRepository
    }

    val configuration = LocalConfiguration.current
    val windowInfo = LocalWindowInfo.current
    val screenWidthDp = configuration.screenWidthDp.dp
    val screenHeightDp = configuration.screenHeightDp.dp
    val screenWidth = windowInfo.containerSize.width
    val screenHeight = windowInfo.containerSize.height
    val isLandscape = screenWidth > screenHeight

    val horizontalPadding = remember(screenWidthDp, isLandscape) {
        if (isLandscape) 0.dp else screenWidthDp * 0.12f
    }
    val buttonSpacing = remember(screenWidthDp, screenHeightDp, isLandscape) {
        if (isLandscape) screenHeightDp * 0.015f else screenWidthDp * 0.02f
    }
    val buttonSize = remember(screenWidthDp, screenHeightDp, isLandscape, buttonSpacing, horizontalPadding) {
        if (isLandscape) {
            val availableHeight = screenHeightDp * 0.8f
            val heightBasedSize = (availableHeight - buttonSpacing * 3) / 4f
            val widthBasedSize = ((screenWidthDp * 0.45f) - buttonSpacing * 2) / 3f
            minOf(heightBasedSize, widthBasedSize)
        } else {
            val availableWidth = screenWidthDp - (horizontalPadding * 2)
            (availableWidth - buttonSpacing * 2) / 3.5f
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Set Guest Password") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            appLockRepository?.clearGuestPassword()
                            Toast.makeText(context, "Guest password cleared", Toast.LENGTH_SHORT).show()
                            navController.popBackStack()
                        }
                    ) {
                        Text("Clear")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = horizontalPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top section: title + indicators + error
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = if (isConfirmationMode) "Confirm guest password" else "Enter guest password",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))

                val errorText = when {
                    showMismatchError -> "Passwords do not match. Try again."
                    showLengthError -> "Password must be at least $MIN_GUEST_PASSWORD_LENGTH digits."
                    showSameAsAdminError -> "Guest password must differ from the admin password."
                    else -> null
                }
                if (errorText != null) {
                    Text(
                        text = errorText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                PasswordIndicators(
                    passwordLength = if (isConfirmationMode) confirmPasswordState.length else passwordState.length
                )
            }

            // Bottom section: keypad
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(buttonSpacing)
            ) {
                val onKeyClick: (String) -> Unit = { key ->
                    val current = if (isConfirmationMode) confirmPasswordState else passwordState
                    val update: (String) -> Unit = { v ->
                        if (isConfirmationMode) confirmPasswordState = v else passwordState = v
                    }

                    when (key) {
                        "0", "1", "2", "3", "4", "5", "6", "7", "8", "9" -> {
                            if (current.length < 6) update(current + key)
                        }
                        "backspace" -> {
                            if (current.isNotEmpty()) update(current.dropLast(1))
                            showMismatchError = false
                            showLengthError = false
                            showSameAsAdminError = false
                        }
                        "proceed" -> {
                            if (current.length < MIN_GUEST_PASSWORD_LENGTH) {
                                showLengthError = true
                            } else if (!isConfirmationMode) {
                                isConfirmationMode = true
                            } else {
                                if (confirmPasswordState != passwordState) {
                                    showMismatchError = true
                                    confirmPasswordState = ""
                                } else if (appLockRepository?.validatePassword(passwordState) == true) {
                                    showSameAsAdminError = true
                                    passwordState = ""
                                    confirmPasswordState = ""
                                    isConfirmationMode = false
                                } else {
                                    appLockRepository?.setGuestPassword(passwordState)
                                    Toast.makeText(context, "Guest password saved", Toast.LENGTH_SHORT).show()
                                    navController.popBackStack()
                                }
                            }
                        }
                    }
                }

                KeypadRow(
                    keys = listOf("1", "2", "3"),
                    onKeyClick = onKeyClick,
                    buttonSize = buttonSize,
                    buttonSpacing = buttonSpacing
                )
                KeypadRow(
                    keys = listOf("4", "5", "6"),
                    onKeyClick = onKeyClick,
                    buttonSize = buttonSize,
                    buttonSpacing = buttonSpacing
                )
                KeypadRow(
                    keys = listOf("7", "8", "9"),
                    onKeyClick = onKeyClick,
                    buttonSize = buttonSize,
                    buttonSpacing = buttonSpacing
                )
                KeypadRow(
                    keys = listOf("backspace", "0", "proceed"),
                    icons = listOf(
                        Backspace,
                        null,
                        if (isConfirmationMode) Icons.Default.Check else Icons.AutoMirrored.Rounded.KeyboardArrowRight
                    ),
                    onKeyClick = onKeyClick,
                    buttonSize = buttonSize,
                    buttonSpacing = buttonSpacing
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}


private const val MIN_PASSWORD_LENGTH = 4

/**
 * Screen for setting up (or changing) the guest / decoy password used by the
 * Dual Password Intruder Monitoring System.
 *
 * The flow mirrors [SetPasswordScreen]:
 *  1. User enters a new PIN.
 *  2. User confirms the PIN.
 *  3. On match the hash is saved via [AppLockRepository.setGuestPassword].
 *
 * The guest password MUST differ from the admin password.  If the user enters
 * the same value the screen shows an error and resets.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SetGuestPasswordScreen(navController: NavController) {
    var passwordState by remember { mutableStateOf("") }
    var confirmPasswordState by remember { mutableStateOf("") }
    var isConfirmationMode by remember { mutableStateOf(false) }

    var showMismatchError by remember { mutableStateOf(false) }
    var showLengthError by remember { mutableStateOf(false) }
    var showSameAsAdminError by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val appLockRepository = remember {
        (context.applicationContext as? AppLockApplication)?.appLockRepository
    }

    val configuration = LocalConfiguration.current
    val windowInfo = LocalWindowInfo.current
    val screenWidthDp = configuration.screenWidthDp.dp
    val screenHeightDp = configuration.screenHeightDp.dp
    val screenWidth = windowInfo.containerSize.width
    val screenHeight = windowInfo.containerSize.height
    val isLandscape = screenWidth > screenHeight

    val horizontalPadding = if (isLandscape) 0.dp else screenWidthDp * 0.12f
    val buttonSpacing = if (isLandscape) screenHeightDp * 0.015f else screenWidthDp * 0.02f

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Set Guest Password") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            if (appLockRepository != null) {
                                appLockRepository.clearGuestPassword()
                                Toast.makeText(context, "Guest password cleared", Toast.LENGTH_SHORT).show()
                                navController.popBackStack()
                            }
                        }
                    ) {
                        Text("Clear")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = horizontalPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = if (isConfirmationMode) "Confirm guest password" else "Enter guest password",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))

                val errorText = when {
                    showMismatchError -> "Passwords do not match. Try again."
                    showLengthError -> "Password must be at least $MIN_PASSWORD_LENGTH digits."
                    showSameAsAdminError -> "Guest password must differ from the admin password."
                    else -> null
                }
                if (errorText != null) {
                    Text(
                        text = errorText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                PasswordIndicators(
                    passwordLength = if (isConfirmationMode) confirmPasswordState.length else passwordState.length
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Row 1: 1 2 3
                KeypadRow(
                    keys = listOf("1", "2", "3"),
                    buttonSpacing = buttonSpacing,
                    onKey = { key ->
                        showMismatchError = false
                        showLengthError = false
                        showSameAsAdminError = false
                        if (isConfirmationMode) {
                            if (confirmPasswordState.length < 6) confirmPasswordState += key
                        } else {
                            if (passwordState.length < 6) passwordState += key
                        }
                    },
                    onBackspace = {
                        showMismatchError = false
                        showLengthError = false
                        showSameAsAdminError = false
                        if (isConfirmationMode) {
                            if (confirmPasswordState.isNotEmpty())
                                confirmPasswordState = confirmPasswordState.dropLast(1)
                        } else {
                            if (passwordState.isNotEmpty())
                                passwordState = passwordState.dropLast(1)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(buttonSpacing))

                KeypadRow(
                    keys = listOf("4", "5", "6"),
                    buttonSpacing = buttonSpacing,
                    onKey = { key ->
                        if (isConfirmationMode) {
                            if (confirmPasswordState.length < 6) confirmPasswordState += key
                        } else {
                            if (passwordState.length < 6) passwordState += key
                        }
                    }
                )

                Spacer(modifier = Modifier.height(buttonSpacing))

                KeypadRow(
                    keys = listOf("7", "8", "9"),
                    buttonSpacing = buttonSpacing,
                    onKey = { key ->
                        if (isConfirmationMode) {
                            if (confirmPasswordState.length < 6) confirmPasswordState += key
                        } else {
                            if (passwordState.length < 6) passwordState += key
                        }
                    }
                )

                Spacer(modifier = Modifier.height(buttonSpacing))

                // Bottom row: backspace, 0, confirm
                Row(
                    horizontalArrangement = Arrangement.spacedBy(buttonSpacing),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Backspace placeholder / actual backspace
                    IconButton(
                        onClick = {
                            if (isConfirmationMode) {
                                if (confirmPasswordState.isNotEmpty())
                                    confirmPasswordState = confirmPasswordState.dropLast(1)
                            } else {
                                if (passwordState.isNotEmpty())
                                    passwordState = passwordState.dropLast(1)
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Backspace,
                            contentDescription = "Backspace",
                            modifier = Modifier.alpha(
                                if ((if (isConfirmationMode) confirmPasswordState else passwordState).isNotEmpty()) 1f else 0f
                            )
                        )
                    }

                    // 0
                    KeypadRow(
                        keys = listOf("0"),
                        buttonSpacing = buttonSpacing,
                        onKey = { key ->
                            if (isConfirmationMode) {
                                if (confirmPasswordState.length < 6) confirmPasswordState += key
                            } else {
                                if (passwordState.length < 6) passwordState += key
                            }
                        }
                    )

                    // Confirm / next
                    IconButton(
                        onClick = {
                            val current = if (isConfirmationMode) confirmPasswordState else passwordState
                            if (!isConfirmationMode) {
                                // First entry
                                if (current.length < MIN_PASSWORD_LENGTH) {
                                    showLengthError = true
                                } else {
                                    isConfirmationMode = true
                                }
                            } else {
                                // Confirmation step
                                if (confirmPasswordState != passwordState) {
                                    showMismatchError = true
                                    confirmPasswordState = ""
                                } else if (appLockRepository?.validatePassword(passwordState) == true) {
                                    showSameAsAdminError = true
                                    passwordState = ""
                                    confirmPasswordState = ""
                                    isConfirmationMode = false
                                } else {
                                    appLockRepository?.setGuestPassword(passwordState)
                                    Toast.makeText(
                                        context,
                                        "Guest password saved",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    navController.popBackStack()
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Confirm",
                            modifier = Modifier.alpha(
                                if ((if (isConfirmationMode) confirmPasswordState else passwordState).length >= MIN_PASSWORD_LENGTH) 1f else 0.3f
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
