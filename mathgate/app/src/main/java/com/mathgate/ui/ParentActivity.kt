package com.mathgate.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.mathgate.MathGateApp
import com.mathgate.R
import com.mathgate.core.PinPolicy
import com.mathgate.core.Settings
import com.mathgate.core.UsageStats
import com.mathgate.data.GateStore
import com.mathgate.data.PinCredentials
import com.mathgate.data.PinGuard
import com.mathgate.data.PinHasher
import com.mathgate.service.GateCoordinator
import com.mathgate.ui.theme.GateBackground
import com.mathgate.ui.theme.GateOnBackground
import com.mathgate.ui.theme.GateWrong
import com.mathgate.ui.theme.MathGateTvTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val LockoutTickMs = 500L
private val LimitPresetsMinutes = listOf(5, 10, 15, 20, 30, 45, 60)
private val DifficultyLevels = listOf(1, 2, 3, 4)
private val WatchedPackageOptions = listOf(
    "com.google.android.youtube.tv" to R.string.package_youtube,
    "com.google.android.youtube.tvkids" to R.string.package_youtube_kids,
    "com.google.android.youtube.tvmusic" to R.string.package_youtube_music,
    "com.tvwebbrowser.v22" to R.string.package_browser,
)

/**
 * Parent settings, protected by a PIN (phase 7). Also registered as the accessibility service
 * `settingsActivity`, which is why it is exported: the system entry launches it from another
 * process, and the PIN is what actually protects the settings.
 */
class ParentActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as MathGateApp
        app.gateCoordinator.start()
        setContent {
            MathGateTvTheme {
                ParentScreen(
                    store = app.gateStore,
                    coordinator = app.gateCoordinator,
                    onOpenSetup = { startActivity(Intent(this, SetupActivity::class.java)) },
                    onClose = { finish() },
                )
            }
        }
    }
}

@Composable
private fun ParentScreen(
    store: GateStore,
    coordinator: GateCoordinator,
    onOpenSetup: () -> Unit,
    onClose: () -> Unit,
) {
    var credentials by remember { mutableStateOf<PinCredentials?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var unlocked by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        credentials = store.readPin()
        loaded = true
    }

    val pin = credentials
    Box(modifier = Modifier.fillMaxSize().background(GateBackground)) {
        when {
            !loaded -> CenteredMessage(stringResource(R.string.parent_loading))
            pin == null -> NoPinScreen(onOpenSetup = onOpenSetup, onClose = onClose)
            unlocked -> ParentSettingsScreen(store = store, coordinator = coordinator, onClose = onClose)
            else -> PinGateScreen(
                store = store,
                credentials = pin,
                onUnlocked = { unlocked = true },
            )
        }
    }
}

@Composable
private fun PinGateScreen(
    store: GateStore,
    credentials: PinCredentials,
    onUnlocked: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var guard by remember { mutableStateOf(PinGuard()) }
    var busy by remember { mutableStateOf(false) }
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) { guard = store.readPinGuard() }

    val lockoutUntil = guard.lockoutUntilWallMs
    LaunchedEffect(lockoutUntil) {
        while (System.currentTimeMillis() < lockoutUntil) {
            nowMs = System.currentTimeMillis()
            delay(LockoutTickMs)
        }
        nowMs = System.currentTimeMillis()
    }
    val lockoutRemainingMs = (lockoutUntil - nowMs).coerceAtLeast(0L)
    val lockedOut = lockoutRemainingMs > 0L

    fun submit() {
        if (busy || lockedOut || !PinPolicy.isValid(pin)) return
        busy = true
        error = null
        val candidate = pin
        scope.launch {
            val correct = withContext(Dispatchers.Default) {
                PinHasher.verify(candidate, credentials.salt, credentials.hash)
            }
            if (correct) {
                store.writePinGuard(PinGuard())
                pin = ""
                busy = false
                onUnlocked()
            } else {
                val attempts = guard.failedAttempts + 1
                val lockout = PinPolicy.lockoutMs(attempts)
                val updated = PinGuard(
                    failedAttempts = attempts,
                    lockoutUntilWallMs = if (lockout > 0) System.currentTimeMillis() + lockout else 0L,
                )
                guard = updated
                store.writePinGuard(updated)
                pin = ""
                busy = false
                error = context.getString(R.string.parent_pin_wrong)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.parent_pin_prompt),
            color = GateOnBackground,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
        )
        val message = when {
            busy -> stringResource(R.string.parent_pin_checking)
            lockedOut -> stringResource(
                R.string.parent_pin_locked,
                ((lockoutRemainingMs + 999L) / 1000L).toInt(),
            )

            else -> error
        }
        if (message != null) {
            Text(
                text = message,
                color = GateWrong,
                fontSize = 24.sp,
                textAlign = TextAlign.Center,
            )
        }
        PinPad(
            pin = pin,
            onDigit = { digit -> if (!lockedOut && !busy && pin.length < PinPolicy.MAX_LENGTH) pin += digit },
            onErase = { if (!lockedOut && !busy) pin = pin.dropLast(1) },
        )
        Button(
            onClick = { submit() },
            enabled = pin.length >= PinPolicy.MIN_LENGTH && !busy && !lockedOut,
        ) {
            Text(text = stringResource(R.string.pin_ok), fontSize = 24.sp)
        }
    }
}

@Composable
private fun NoPinScreen(onOpenSetup: () -> Unit, onClose: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = stringResource(R.string.parent_no_pin),
            color = GateOnBackground,
            fontSize = 30.sp,
            textAlign = TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = onOpenSetup) {
                Text(text = stringResource(R.string.parent_no_pin_action), fontSize = 22.sp)
            }
            Button(onClick = onClose) {
                Text(text = stringResource(R.string.parent_back), fontSize = 22.sp)
            }
        }
    }
}

@Composable
private fun ParentSettingsScreen(
    store: GateStore,
    coordinator: GateCoordinator,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var settings by remember { mutableStateOf<Settings?>(null) }
    var stats by remember { mutableStateOf(UsageStats()) }

    LaunchedEffect(Unit) {
        settings = store.readSettings()
        stats = store.readState().stats
    }

    val apply: (Settings) -> Unit = { updated ->
        coordinator.updateSettings(updated)
        settings = updated
    }

    fun notify(messageRes: Int) {
        Toast.makeText(context, context.getString(messageRes), Toast.LENGTH_SHORT).show()
    }

    val current = settings
    if (current == null) {
        CenteredMessage(stringResource(R.string.parent_loading))
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(40.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Text(
            text = stringResource(R.string.parent_settings_title),
            color = GateOnBackground,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
        )

        SettingBlock(stringResource(R.string.parent_limit_label)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LimitPresetsMinutes.forEach { minutes ->
                    SelectableChip(
                        label = stringResource(R.string.parent_minutes, minutes),
                        selected = current.limitMs == minutes * 60_000L,
                        onClick = { apply(current.copy(limitMs = minutes * 60_000L)) },
                    )
                }
            }
        }

        SettingBlock(stringResource(R.string.parent_difficulty_label)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DifficultyLevels.forEach { level ->
                    SelectableChip(
                        label = stringResource(R.string.parent_level, level),
                        selected = current.difficultyLevel == level,
                        onClick = { apply(current.copy(difficultyLevel = level)) },
                    )
                }
            }
        }

        SettingBlock(stringResource(R.string.parent_input_label)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SelectableChip(
                    label = stringResource(R.string.parent_input_digits),
                    selected = !current.multipleChoice,
                    onClick = { apply(current.copy(multipleChoice = false)) },
                )
                SelectableChip(
                    label = stringResource(R.string.parent_input_choice),
                    selected = current.multipleChoice,
                    onClick = { apply(current.copy(multipleChoice = true)) },
                )
            }
        }

        SettingBlock(stringResource(R.string.parent_packages_label)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WatchedPackageOptions.forEach { (packageName, labelRes) ->
                    val selected = packageName in current.watchedPackages
                    SelectableChip(
                        label = stringResource(labelRes),
                        selected = selected,
                        onClick = {
                            val updated = if (selected) {
                                current.watchedPackages - packageName
                            } else {
                                current.watchedPackages + packageName
                            }
                            apply(current.copy(watchedPackages = updated))
                        },
                    )
                }
            }
        }

        ToggleChip(
            label = stringResource(R.string.parent_warn_label),
            checked = current.warnBeforeMs > 0L,
            onToggle = {
                apply(current.copy(warnBeforeMs = if (current.warnBeforeMs > 0L) 0L else 60_000L))
            },
        )

        ToggleChip(
            label = stringResource(R.string.parent_reset_on_power_loss_label),
            checked = current.resetOnPowerLoss,
            onToggle = { apply(current.copy(resetOnPowerLoss = !current.resetOnPowerLoss)) },
        )

        SettingBlock(stringResource(R.string.parent_stats_label)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.parent_stats_blocked, stats.blockedCount),
                    color = GateOnBackground,
                    fontSize = 22.sp,
                )
                Text(
                    text = stringResource(R.string.parent_stats_wrong, stats.wrongAnswers),
                    color = GateOnBackground,
                    fontSize = 22.sp,
                )
                Text(
                    text = stringResource(
                        R.string.parent_stats_total,
                        formatDuration(stats.totalWatchedMs),
                    ),
                    color = GateOnBackground,
                    fontSize = 22.sp,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick = {
                    coordinator.parentOverride()
                    notify(R.string.parent_unlocked_toast)
                    onClose()
                },
            ) {
                Text(text = stringResource(R.string.parent_unlock_now), fontSize = 22.sp)
            }
            Button(
                onClick = {
                    coordinator.resetTimer()
                    notify(R.string.parent_reset_toast)
                },
            ) {
                Text(text = stringResource(R.string.parent_reset_timer), fontSize = 22.sp)
            }
            Button(onClick = onClose) {
                Text(text = stringResource(R.string.parent_back), fontSize = 22.sp)
            }
        }
    }
}

@Composable
private fun SettingBlock(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(text = title, color = GateOnBackground.copy(alpha = 0.75f), fontSize = 20.sp)
        content()
    }
}

@Composable
private fun CenteredMessage(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text, color = GateOnBackground, fontSize = 26.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun formatDuration(totalMs: Long): String {
    val totalMinutes = totalMs / 60_000L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return if (hours > 0L) {
        stringResource(R.string.duration_hours_minutes, hours, minutes)
    } else {
        stringResource(R.string.duration_minutes, minutes)
    }
}
