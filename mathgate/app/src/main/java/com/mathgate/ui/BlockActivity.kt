package com.mathgate.ui

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.mathgate.MathGateApp
import com.mathgate.R
import com.mathgate.core.ChoiceOptions
import com.mathgate.core.Clock
import com.mathgate.core.GateState
import com.mathgate.core.ProblemGenerator
import com.mathgate.core.Settings
import com.mathgate.service.AndroidClock
import com.mathgate.service.AnswerResult
import com.mathgate.service.GateCoordinator
import com.mathgate.ui.theme.GateAccent
import com.mathgate.ui.theme.GateBackground
import com.mathgate.ui.theme.GateCorrect
import com.mathgate.ui.theme.GateOnBackground
import com.mathgate.ui.theme.GateSurface
import com.mathgate.ui.theme.GateWrong
import com.mathgate.ui.theme.MathGateTvTheme
import kotlinx.coroutines.delay

private val KeypadSpacing = 12.dp
private val DigitButtonWidth = 130.dp
private val KeypadButtonHeight = 76.dp
private val DigitRows = listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9))
private const val DefaultFocusDigit = 5
private const val MaxInputDigits = 3
private const val CooldownTickMs = 200L
private const val SuccessDisplayMs = 900L

/**
 * Full-screen challenge surface shown over YouTube (phases 4-5).
 *
 * The screen renders the pending problem and collects the answer with the remote. It is a
 * thin view over [GateCoordinator]: answers go to the shared engine, which owns the rules,
 * the cooldown, the next problem and the effects (closing the screen, media pause/resume).
 */
class BlockActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep the challenge visible while it is being solved.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // BACK must never dismiss the challenge (roadmap phase 4).
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    // Intentionally ignored: only a correct answer unlocks the screen.
                }
            },
        )

        val coordinator = (application as MathGateApp).gateCoordinator
        coordinator.start()
        setContent {
            MathGateTvTheme {
                BlockScreen(coordinator = coordinator, onSolved = { finish() })
            }
        }
    }
}

@Composable
private fun BlockScreen(coordinator: GateCoordinator, onSolved: () -> Unit) {
    val context = LocalContext.current
    val state by coordinator.gateState.collectAsState()
    var controller by remember { mutableStateOf<BlockController?>(null) }

    LaunchedEffect(Unit) {
        controller = BlockController(
            clock = AndroidClock(context.applicationContext),
            coordinator = coordinator,
            settings = coordinator.currentSettings(),
        )
    }

    LaunchedEffect(state, controller) {
        val active = controller ?: return@LaunchedEffect
        state?.let { active.onEngineState(it) }
    }

    val active = controller
    if (active == null) {
        Box(modifier = Modifier.fillMaxSize().background(GateBackground))
    } else {
        BlockContent(controller = active, state = state, onSolved = onSolved)
    }
}

@Composable
private fun BlockContent(controller: BlockController, state: GateState?, onSolved: () -> Unit) {
    LaunchedEffect(controller.feedback) {
        if (controller.feedback == Feedback.CORRECT) {
            delay(SuccessDisplayMs)
            onSolved()
        }
    }

    // A challenge driven by the shared engine closes itself once the engine leaves the pending
    // state (a correct answer or a parent override).
    LaunchedEffect(controller, state) {
        if (controller.realMode && state != null &&
            state !is GateState.ChallengePending && controller.feedback != Feedback.CORRECT
        ) {
            onSolved()
        }
    }

    val cooldownRemainingMs by produceState(
        initialValue = controller.cooldownRemainingMs(),
        key1 = controller.pending,
    ) {
        while (controller.isCoolingDown()) {
            value = controller.cooldownRemainingMs()
            delay(CooldownTickMs)
        }
        value = 0L
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(GateBackground)
            .padding(40.dp)
            .onPreviewKeyEvent { controller.onKeyEvent(it) },
        horizontalArrangement = Arrangement.spacedBy(40.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.block_title),
                color = GateOnBackground.copy(alpha = 0.7f),
                fontSize = 26.sp,
            )
            Text(
                text = "${controller.pending.problem.text} =",
                color = GateOnBackground,
                fontSize = 72.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            AnswerField(input = controller.input)
            FeedbackBanner(
                feedback = controller.feedback,
                cooldownRemainingMs = cooldownRemainingMs,
            )
        }

        if (controller.multipleChoice) {
            ChoiceGrid(options = controller.options, onPick = controller::onOption)
        } else {
            Keypad(
                onDigit = controller::onDigit,
                onErase = controller::onErase,
                onSubmit = controller::onSubmit,
            )
        }
    }
}

@Composable
private fun AnswerField(input: String) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .width(340.dp)
            .height(96.dp)
            .background(GateSurface, shape)
            .border(4.dp, GateAccent, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (input.isEmpty()) {
            Text(
                text = stringResource(R.string.block_answer_hint),
                color = GateOnBackground.copy(alpha = 0.4f),
                fontSize = 40.sp,
            )
        } else {
            Text(
                text = input,
                color = GateOnBackground,
                fontSize = 52.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun FeedbackBanner(feedback: Feedback, cooldownRemainingMs: Long) {
    if (feedback == Feedback.NONE && cooldownRemainingMs <= 0L) {
        Spacer(modifier = Modifier.height(48.dp))
        return
    }

    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val scale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.6f,
        label = "block-feedback-scale",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val message = when (feedback) {
            Feedback.CORRECT -> stringResource(R.string.block_correct) to GateCorrect
            Feedback.WRONG -> stringResource(R.string.block_wrong) to GateWrong
            Feedback.NONE -> null
        }
        if (message != null) {
            Text(
                text = message.first,
                color = message.second,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.scale(scale),
            )
        }
        if (cooldownRemainingMs > 0L) {
            val seconds = ((cooldownRemainingMs + 999L) / 1000L).toInt()
            Text(
                text = stringResource(R.string.block_cooldown, seconds),
                color = GateOnBackground.copy(alpha = 0.8f),
                fontSize = 26.sp,
            )
        }
    }
}

@Composable
private fun Keypad(
    onDigit: (Int) -> Unit,
    onErase: () -> Unit,
    onSubmit: () -> Unit,
) {
    val defaultFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { defaultFocus.requestFocus() }

    Column(verticalArrangement = Arrangement.spacedBy(KeypadSpacing)) {
        DigitRows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(KeypadSpacing)) {
                row.forEach { digit ->
                    KeypadButton(
                        label = digit.toString(),
                        modifier = if (digit == DefaultFocusDigit) {
                            Modifier.focusRequester(defaultFocus)
                        } else {
                            Modifier
                        },
                        onClick = { onDigit(digit) },
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(KeypadSpacing)) {
            KeypadButton(label = "0", onClick = { onDigit(0) })
            KeypadButton(
                label = stringResource(R.string.block_erase),
                labelSize = 24.sp,
                onClick = onErase,
            )
            KeypadButton(
                label = stringResource(R.string.block_submit),
                labelSize = 24.sp,
                onClick = onSubmit,
            )
        }
    }
}

@Composable
private fun ChoiceGrid(options: List<Int>, onPick: (Int) -> Unit) {
    val defaultFocus = remember { FocusRequester() }
    LaunchedEffect(options) {
        if (options.isNotEmpty()) defaultFocus.requestFocus()
    }

    Column(verticalArrangement = Arrangement.spacedBy(KeypadSpacing)) {
        options.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(KeypadSpacing)) {
                pair.forEach { value ->
                    KeypadButton(
                        label = value.toString(),
                        width = 200.dp,
                        labelSize = 40.sp,
                        modifier = if (value == options.first()) {
                            Modifier.focusRequester(defaultFocus)
                        } else {
                            Modifier
                        },
                        onClick = { onPick(value) },
                    )
                }
            }
        }
    }
}

@Composable
private fun KeypadButton(
    label: String,
    modifier: Modifier = Modifier,
    width: Dp = DigitButtonWidth,
    labelSize: TextUnit = 32.sp,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.size(width = width, height = KeypadButtonHeight),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Text(text = label, fontSize = labelSize, fontWeight = FontWeight.Bold)
    }
}

private enum class Feedback { NONE, CORRECT, WRONG }

/**
 * View-model of the challenge screen.
 *
 * In the normal flow it is a thin adapter over the shared [GateCoordinator]. If the screen is
 * opened outside a pending challenge (for example over `adb shell am start`), it falls back to a
 * self-contained demo problem so every control stays testable.
 */
private class BlockController(
    private val clock: Clock,
    private val coordinator: GateCoordinator,
    settings: Settings,
) {
    /** True when a real pending challenge drives this screen through the shared engine. */
    val realMode: Boolean = coordinator.gateState.value is GateState.ChallengePending

    private val multipleChoiceSetting = settings.multipleChoice
    private val difficulty = settings.difficultyLevel

    /** Standalone fallback used only when there is no real pending challenge. */
    private var demo: GateState.ChallengePending =
        if (realMode) {
            coordinator.gateState.value as GateState.ChallengePending
        } else {
            GateState.ChallengePending(
                problem = ProblemGenerator.generate(difficulty, System.currentTimeMillis()),
                attempts = 0,
            )
        }

    var pending: GateState.ChallengePending by mutableStateOf(demo)
        private set
    var input: String by mutableStateOf("")
        private set
    var feedback: Feedback by mutableStateOf(Feedback.NONE)
        private set
    var options: List<Int> by mutableStateOf(emptyList())
        private set

    init {
        refreshOptions()
    }

    val multipleChoice: Boolean get() = multipleChoiceSetting

    /** Mirrors the shared engine state; ignored in standalone demo mode. */
    fun onEngineState(state: GateState) {
        if (!realMode) return
        val challenge = state as? GateState.ChallengePending ?: return
        pending = challenge
        refreshOptions()
    }

    fun isCoolingDown(): Boolean = clock.elapsedRealtimeMs() < pending.cooldownUntilElapsedMs

    fun cooldownRemainingMs(): Long =
        (pending.cooldownUntilElapsedMs - clock.elapsedRealtimeMs()).coerceAtLeast(0L)

    fun onDigit(digit: Int) {
        if (multipleChoice || isLocked()) return
        if (input.length >= MaxInputDigits) return
        if (feedback == Feedback.WRONG) feedback = Feedback.NONE
        input += digit
    }

    fun onErase() {
        if (multipleChoice || isLocked()) return
        if (feedback == Feedback.WRONG) feedback = Feedback.NONE
        input = input.dropLast(1)
    }

    fun onSubmit() {
        if (multipleChoice || isLocked()) return
        val value = input.toIntOrNull() ?: return
        submit(value)
    }

    fun onOption(value: Int) {
        if (!multipleChoice || isLocked()) return
        submit(value)
    }

    /** Handles the numeric keys of a TV remote; returns true when consumed. */
    fun onKeyEvent(event: KeyEvent): Boolean {
        if (multipleChoice || event.type != KeyEventType.KeyDown) return false
        if (event.key == Key.Backspace || event.key == Key.Delete) {
            onErase()
            return true
        }
        val digit = digitForKey(event.key) ?: return false
        onDigit(digit)
        return true
    }

    private fun isLocked(): Boolean = feedback == Feedback.CORRECT || isCoolingDown()

    private fun submit(value: Int) {
        if (realMode) {
            when (coordinator.submitAnswer(value)) {
                AnswerResult.CORRECT -> {
                    feedback = Feedback.CORRECT
                    input = ""
                }

                AnswerResult.WRONG -> {
                    input = ""
                    feedback = Feedback.WRONG
                    (coordinator.gateState.value as? GateState.ChallengePending)?.let {
                        pending = it
                        refreshOptions()
                    }
                }

                AnswerResult.IGNORED -> Unit
            }
            return
        }

        if (value == demo.problem.answer) {
            feedback = Feedback.CORRECT
            input = ""
            return
        }
        demo = demo.copy(
            problem = ProblemGenerator.generate(difficulty, System.nanoTime()),
            attempts = demo.attempts + 1,
        )
        pending = demo
        input = ""
        feedback = Feedback.WRONG
        refreshOptions()
    }

    private fun refreshOptions() {
        options = if (multipleChoiceSetting) {
            ChoiceOptions.forProblem(
                problem = pending.problem,
                seed = pending.problem.text.hashCode().toLong() * 31 + pending.attempts,
            )
        } else {
            emptyList()
        }
    }
}

private fun digitForKey(key: Key): Int? = when (key) {
    Key.Zero, Key.NumPad0 -> 0
    Key.One, Key.NumPad1 -> 1
    Key.Two, Key.NumPad2 -> 2
    Key.Three, Key.NumPad3 -> 3
    Key.Four, Key.NumPad4 -> 4
    Key.Five, Key.NumPad5 -> 5
    Key.Six, Key.NumPad6 -> 6
    Key.Seven, Key.NumPad7 -> 7
    Key.Eight, Key.NumPad8 -> 8
    Key.Nine, Key.NumPad9 -> 9
    else -> null
}
