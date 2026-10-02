package com.mathgate

import android.app.Application
import android.os.SystemClock
import com.mathgate.core.EventLog
import com.mathgate.data.DataStoreGateStore
import com.mathgate.data.GateStore
import com.mathgate.detect.A11yForegroundDetector
import com.mathgate.detect.DetectorHeartbeat
import com.mathgate.service.AndroidClock
import com.mathgate.service.ChallengeEnforcer
import com.mathgate.service.GateCoordinator

/**
 * Application entry point and the process-wide component holder.
 *
 * Components are shared by the accessibility service, the keep-alive service and the UI;
 * [GateCoordinator] is the single owner of the state machine (phase 5).
 */
class MathGateApp : Application() {

    /** Recent transition log shared by all components (phase 3). */
    val eventLog: EventLog by lazy { EventLog() }

    /** Persisted gate state + settings (phase 2). */
    val gateStore: GateStore by lazy { DataStoreGateStore(this) }

    /** Primary foreground detector fed by the accessibility service (phase 3). */
    val a11yForegroundDetector: A11yForegroundDetector by lazy { A11yForegroundDetector() }

    /** Liveness of the accessibility channel, consulted by the fallback watchdog (phase 6). */
    val a11yHeartbeat: DetectorHeartbeat by lazy { DetectorHeartbeat(SystemClock::elapsedRealtime) }

    /** Starts/stops the challenge and drives media keys (phase 5). */
    val challengeEnforcer: ChallengeEnforcer by lazy { ChallengeEnforcer(this, eventLog) }

    /** Wires detector → engine → actions (phase 5). */
    val gateCoordinator: GateCoordinator by lazy {
        GateCoordinator(
            context = this,
            store = gateStore,
            clock = AndroidClock(this),
            detector = a11yForegroundDetector,
            enforcer = challengeEnforcer,
            eventLog = eventLog,
        )
    }
}
