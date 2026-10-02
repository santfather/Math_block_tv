package com.mathgate.ui

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.mathgate.MathGateApp
import com.mathgate.R
import com.mathgate.core.PinPolicy
import com.mathgate.data.GateStore
import com.mathgate.data.PinCredentials
import com.mathgate.data.PinHasher
import com.mathgate.service.GuardAccessibilityService
import com.mathgate.service.GuardForegroundService
import com.mathgate.ui.theme.GateBackground
import com.mathgate.ui.theme.GateCorrect
import com.mathgate.ui.theme.GateOnBackground
import com.mathgate.ui.theme.GateWrong
import com.mathgate.ui.theme.MathGateTvTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class SetupStep { WELCOME, PERMISSIONS, CREATE_PIN, CONFIRM_PIN, DONE }

/**
 * First-run wizard and the only leanback launcher entry point (phase 7).
 *
 * If a PIN already exists the launcher opens the PIN-protected parent screen instead, so a child
 * cannot reach the settings by tapping the app icon.
 */
class SetupActivity : ComponentActivity() {

    private val refreshTick = mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startGuardService()
        val app = application as MathGateApp
        app.gateCoordinator.start()
        setContent {
            MathGateTvTheme {
                SetupScreen(
                    store = app.gateStore,
                    refreshKey = refreshTick.value,
                    onOpenParent = { startActivity(Intent(this, ParentActivity::class.java)) },
                    onClose = { finish() },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-read the permission state after the user returns from the system settings.
        refreshTick.value++
    }

    /** Brings up the keep-alive guard as soon as the app is opened (phase 3). */
    private fun startGuardService() {
        ContextCompat.startForegroundService(
            this,
            Intent(this, GuardForegroundService::class.java),
        )
    }
}

@Composable
private fun SetupScreen(
    store: GateStore,
    refreshKey: Int,
    onOpenParent: () -> Unit,
    onClose: () -> Unit,
) {
    var hasPin by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) { hasPin = store.readPin() != null }

    Box(modifier = Modifier.fillMaxSize().background(GateBackground)) {
        when (hasPin) {
            null -> CenteredText(stringResource(R.string.parent_loading))
            true -> {
                // Already configured: go straight to the PIN-protected parent screen.
                LaunchedEffect(Unit) {
                    onOpenParent()
                    onClose()
                }
            }

            false -> SetupWizard(
                store = store,
                refreshKey = refreshKey,
                onOpenParent = onOpenParent,
                onClose = onClose,
            )
        }
    }
}

@Composable
private fun SetupWizard(
    store: GateStore,
    refreshKey: Int,
    onOpenParent: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(SetupStep.WELCOME) }
    var pin by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(40.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(
            text = stringResource(R.string.setup_title),
            color = GateOnBackground,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
        )

        when (step) {
            SetupStep.WELCOME -> WelcomeStep(
                onNext = { step = SetupStep.PERMISSIONS },
                onClose = onClose,
            )

            SetupStep.PERMISSIONS -> PermissionsStep(
                context = context,
                refreshKey = refreshKey,
                onBack = { step = SetupStep.WELCOME },
                onNext = { step = SetupStep.CREATE_PIN },
            )

            SetupStep.CREATE_PIN -> PinStep(
                title = stringResource(R.string.setup_step_pin),
                subtitle = stringResource(
                    R.string.setup_pin_body,
                    PinPolicy.MIN_LENGTH,
                    PinPolicy.MAX_LENGTH,
                ),
                pin = pin,
                error = error,
                busy = false,
                onDigit = { if (pin.length < PinPolicy.MAX_LENGTH) pin += it },
                onErase = { pin = pin.dropLast(1) },
                onConfirm = {
                    if (PinPolicy.isValid(pin)) {
                        error = null
                        step = SetupStep.CONFIRM_PIN
                    } else {
                        error = context.getString(
                            R.string.setup_pin_invalid,
                            PinPolicy.MIN_LENGTH,
                            PinPolicy.MAX_LENGTH,
                        )
                    }
                },
                onBack = {
                    pin = ""
                    error = null
                    step = SetupStep.PERMISSIONS
                },
            )

            SetupStep.CONFIRM_PIN -> PinStep(
                title = stringResource(R.string.setup_step_pin),
                subtitle = stringResource(R.string.setup_pin_confirm_body),
                pin = confirmation,
                error = error,
                busy = saving,
                onDigit = { if (confirmation.length < PinPolicy.MAX_LENGTH) confirmation += it },
                onErase = { confirmation = confirmation.dropLast(1) },
                onConfirm = {
                    if (confirmation != pin) {
                        confirmation = ""
                        error = context.getString(R.string.setup_pin_mismatch)
                    } else {
                        saving = true
                        error = null
                        val chosen = pin
                        scope.launch {
                            val salt = PinHasher.newSalt()
                            val hash = withContext(Dispatchers.Default) { PinHasher.hash(chosen, salt) }
                            store.writePin(PinCredentials(salt = salt, hash = hash))
                            saving = false
                            pin = ""
                            confirmation = ""
                            step = SetupStep.DONE
                        }
                    }
                },
                onBack = {
                    confirmation = ""
                    error = null
                    step = SetupStep.CREATE_PIN
                },
            )

            SetupStep.DONE -> DoneStep(onOpenParent = onOpenParent, onClose = onClose)
        }
    }
}

@Composable
private fun WelcomeStep(onNext: () -> Unit, onClose: () -> Unit) {
    Text(
        text = stringResource(R.string.setup_welcome_body),
        color = GateOnBackground,
        fontSize = 24.sp,
    )
    NavigationRow(onBack = onClose, backLabel = stringResource(R.string.setup_finish), onNext = onNext)
}

@Composable
private fun PermissionsStep(
    context: Context,
    refreshKey: Int,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    val a11yEnabled = remember(refreshKey) { isAccessibilityEnabled(context) }
    val usageGranted = remember(refreshKey) { hasUsageAccess(context) }
    // The "Открыть" buttons sit far to the right of the navigation row, so Compose's geometric
    // focus search cannot reach "Далее" by pressing DOWN. Link it explicitly.
    val nextFocus = remember { FocusRequester() }

    Text(
        text = stringResource(R.string.setup_permissions_body),
        color = GateOnBackground,
        fontSize = 24.sp,
    )
    PermissionRow(
        title = stringResource(R.string.setup_a11y_title),
        granted = a11yEnabled,
        downFocus = nextFocus,
        onAction = { openSettings(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
    )
    PermissionRow(
        title = stringResource(R.string.setup_usage_title),
        granted = usageGranted,
        downFocus = nextFocus,
        onAction = { openSettings(context, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
    )
    Text(
        text = stringResource(R.string.setup_permissions_hint),
        color = GateOnBackground.copy(alpha = 0.7f),
        fontSize = 18.sp,
    )
    NavigationRow(onBack = onBack, onNext = onNext, nextFocusRequester = nextFocus)
}

@Composable
private fun PermissionRow(
    title: String,
    granted: Boolean,
    downFocus: FocusRequester,
    onAction: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, color = GateOnBackground, fontSize = 22.sp)
        Text(
            text = stringResource(
                if (granted) R.string.setup_permission_granted else R.string.setup_permission_missing,
            ),
            color = if (granted) GateCorrect else GateWrong,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        Button(
            onClick = onAction,
            modifier = Modifier.focusProperties { down = downFocus },
        ) {
            Text(text = stringResource(R.string.setup_permission_open), fontSize = 20.sp)
        }
    }
}

@Composable
private fun PinStep(
    title: String,
    subtitle: String,
    pin: String,
    error: String?,
    busy: Boolean,
    onDigit: (Int) -> Unit,
    onErase: () -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
) {
    Text(text = title, color = GateOnBackground, fontSize = 26.sp, fontWeight = FontWeight.Bold)
    Text(text = subtitle, color = GateOnBackground.copy(alpha = 0.8f), fontSize = 22.sp)
    if (error != null) {
        Text(text = error, color = GateWrong, fontSize = 20.sp)
    }
    if (busy) {
        Text(text = stringResource(R.string.parent_pin_checking), color = GateOnBackground, fontSize = 20.sp)
    }
    PinPad(pin = pin, onDigit = onDigit, onErase = onErase)
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Button(onClick = onBack, enabled = !busy) {
            Text(text = stringResource(R.string.setup_back), fontSize = 22.sp)
        }
        Button(onClick = onConfirm, enabled = pin.length >= PinPolicy.MIN_LENGTH && !busy) {
            Text(text = stringResource(R.string.setup_next), fontSize = 22.sp)
        }
    }
}

@Composable
private fun DoneStep(onOpenParent: () -> Unit, onClose: () -> Unit) {
    Text(text = stringResource(R.string.setup_done_body), color = GateOnBackground, fontSize = 24.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Button(onClick = onOpenParent) {
            Text(text = stringResource(R.string.setup_open_settings), fontSize = 22.sp)
        }
        Button(onClick = onClose) {
            Text(text = stringResource(R.string.setup_finish), fontSize = 22.sp)
        }
    }
}

@Composable
private fun NavigationRow(
    onBack: () -> Unit,
    onNext: () -> Unit,
    backLabel: String = stringResource(R.string.setup_back),
    nextFocusRequester: FocusRequester? = null,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Button(onClick = onBack) {
            Text(text = backLabel, fontSize = 22.sp)
        }
        val nextModifier = if (nextFocusRequester != null) {
            Modifier.focusRequester(nextFocusRequester)
        } else {
            Modifier
        }
        Button(onClick = onNext, modifier = nextModifier) {
            Text(text = stringResource(R.string.setup_next), fontSize = 22.sp)
        }
    }
}

@Composable
private fun CenteredText(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text, color = GateOnBackground, fontSize = 26.sp, textAlign = TextAlign.Center)
    }
}

private fun openSettings(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        // Some TV firmwares expose no dedicated screen; the parent can grant it from Settings.
    }
}

private fun isAccessibilityEnabled(context: Context): Boolean {
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ) ?: return false
    val component = "${context.packageName}/${GuardAccessibilityService::class.java.name}"
    return enabled.split(':').any { it.equals(component, ignoreCase = true) }
}

private fun hasUsageAccess(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = appOps.unsafeCheckOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        Process.myUid(),
        context.packageName,
    )
    return mode == AppOpsManager.MODE_ALLOWED
}
