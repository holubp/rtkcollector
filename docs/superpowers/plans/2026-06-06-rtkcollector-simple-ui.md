# RtkCollector Simple UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a minimal Android UI skeleton that lets a user select a V1 workflow, inspect the validated receiver command plan, dry-run a recording lifecycle, and stop/finalise the dry-run session.

**Architecture:** Keep workflow, command-plan, and dry-run lifecycle logic in pure Kotlin under `:core:workflow`. Convert `:app` into a small native Android app that depends on `:core:workflow` and only renders/controls the dry-run model. The UI must not implement USB, NTRIP networking, receiver serial TX, foreground services, RTKLIB, maps, shapefiles, or GIS editing.

**Tech Stack:** Kotlin 2.3.21, Gradle 9.5.1, Android Gradle Plugin 9.2.0, Android SDK 36, JUnit 5 for pure Kotlin tests, native Android `Activity` and `android.widget` views for the first UI.

---

## File Structure

- Modify `build.gradle.kts`: add Android and Kotlin Android plugins while preserving JVM module configuration and the bootstrap `assembleDebug` alias only if the Android app task is unavailable.
- Modify `app/build.gradle.kts`: convert `:app` from Kotlin/JVM to Android application; depend on `:core:workflow`.
- Create `app/src/main/AndroidManifest.xml`: declare package, label, theme, and `MainActivity`.
- Replace `app/src/main/kotlin/org/rtkcollector/app/BootstrapActivity.kt` with `MainActivity.kt`: native Android guided launcher and dry-run monitor.
- Create `core/workflow/src/main/kotlin/org/rtkcollector/core/workflow/ReceiverCommandPlan.kt`: command sequence phases, command plan examples, and validator.
- Create `core/workflow/src/test/kotlin/org/rtkcollector/core/workflow/ReceiverCommandPlanTest.kt`: ordering and validation tests.
- Create `core/workflow/src/main/kotlin/org/rtkcollector/core/workflow/WorkflowDryRunSession.kt`: pure Kotlin recording lifecycle model and observable state.
- Create `core/workflow/src/test/kotlin/org/rtkcollector/core/workflow/WorkflowDryRunSessionTest.kt`: dry-run start, stop, blocked-start, and shutdown outcome tests.
- Modify `docs/user-workflows.md`: add the first UI dry-run workflow section.
- Modify `docs/workflows.md`: cross-reference command plans and dry-run lifecycle.
- Modify `README.md`: state that the bootstrap app now has a simple dry-run UI and still no real receiver I/O.

## Task 1: Receiver Command Plan Model

**Files:**
- Create: `core/workflow/src/main/kotlin/org/rtkcollector/core/workflow/ReceiverCommandPlan.kt`
- Create: `core/workflow/src/test/kotlin/org/rtkcollector/core/workflow/ReceiverCommandPlanTest.kt`

- [ ] **Step 1: Write command-plan tests first**

Create `core/workflow/src/test/kotlin/org/rtkcollector/core/workflow/ReceiverCommandPlanTest.kt`:

```kotlin
package org.rtkcollector.core.workflow

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReceiverCommandPlanTest {
    private val validator = ReceiverCommandPlanValidator()

    @Test
    fun `startup commands concatenate init then selected mode sequence`() {
        val plan = ReceiverCommandPlanExamples.um980RoverPlan()

        assertEquals(
            listOf(
                "# UM980 init commands intentionally omitted",
                "# UM980 rover commands intentionally omitted",
            ),
            plan.startupCommands(),
        )
    }

    @Test
    fun `shutdown commands are not part of startup commands`() {
        val plan = ReceiverCommandPlanExamples.um980RoverPlan(
            shutdownSequence = ReceiverCommandSequence(
                id = "um980-safe-shutdown",
                name = "UM980 safe shutdown reference",
                phase = ReceiverCommandPhase.SHUTDOWN,
                commands = listOf("# UM980 shutdown commands intentionally omitted"),
            ),
        )

        assertEquals(
            listOf(
                "# UM980 init commands intentionally omitted",
                "# UM980 rover commands intentionally omitted",
            ),
            plan.startupCommands(),
        )
        assertEquals(listOf("# UM980 shutdown commands intentionally omitted"), plan.shutdownCommands())
    }

    @Test
    fun `rover workflow accepts init plus rover sequence`() {
        val workflow = WorkflowExamples.plainRoverRecording(ReceiverCapabilityFixtures.um980N4())
        val plan = ReceiverCommandPlanExamples.um980RoverPlan()

        val result = validator.validate(workflow, plan)

        assertTrue(result.valid, result.errors.toString())
    }

    @Test
    fun `rover workflow rejects fixed-base mode command sequence`() {
        val workflow = WorkflowExamples.plainRoverRecording(ReceiverCapabilityFixtures.um980N4())
        val plan = ReceiverCommandPlanExamples.um980FixedBasePlan()

        val result = validator.validate(workflow, plan)

        assertFalse(result.valid)
        assertTrue(result.errors.any { it.code == "COMMAND_MODE_DOES_NOT_MATCH_WORKFLOW" })
    }

    @Test
    fun `plan rejects non-init init sequence phase`() {
        val workflow = WorkflowExamples.plainRoverRecording(ReceiverCapabilityFixtures.um980N4())
        val plan = ReceiverCommandPlanExamples.um980RoverPlan().copy(
            initSequence = ReceiverCommandSequence(
                id = "bad-init",
                name = "Bad init",
                phase = ReceiverCommandPhase.ROVER,
                commands = listOf("# wrong phase"),
            ),
        )

        val result = validator.validate(workflow, plan)

        assertFalse(result.valid)
        assertTrue(result.errors.any { it.code == "INIT_SEQUENCE_REQUIRES_INIT_PHASE" })
    }

    @Test
    fun `custom command plan requires custom command capability`() {
        val workflow = WorkflowExamples.plainRoverRecording(ReceiverCapabilityFixtures.genericNmeaRtcm())
        val plan = ReceiverCommandPlanExamples.genericRoverPlan().copy(customCommandsRequested = true)

        val result = validator.validate(workflow, plan)

        assertFalse(result.valid)
        assertTrue(result.errors.any { it.code == "CUSTOM_COMMANDS_REQUIRE_CAPABILITY" })
    }

    @Test
    fun `empty shutdown sequence is valid`() {
        val workflow = WorkflowExamples.plainRoverRecording(ReceiverCapabilityFixtures.um980N4())
        val plan = ReceiverCommandPlanExamples.um980RoverPlan(shutdownSequence = ReceiverCommandSequence.emptyShutdown())

        val result = validator.validate(workflow, plan)

        assertTrue(result.valid, result.errors.toString())
        assertEquals(emptyList<String>(), plan.shutdownCommands())
    }

    @Test
    fun `replay workflow accepts empty replay command plan`() {
        val workflow = WorkflowExamples.replayTest(ReceiverCapabilityFixtures.um980N4())
        val plan = ReceiverCommandPlanExamples.replayPlan(workflow.receiverProfileId)

        val result = validator.validate(workflow, plan)

        assertTrue(result.valid, result.errors.toString())
        assertEquals(emptyList<String>(), plan.startupCommands())
    }

    @Test
    fun `safe reference plan uses selected receiver profile id`() {
        val plan = ReceiverCommandPlanExamples.safeReferencePlan(
            receiverProfileId = "ublox-m8p2",
            receiverRole = ReceiverRole.FIXED_BASE,
        )

        assertEquals("ublox-m8p2", plan.receiverProfileId)
        assertEquals(ReceiverCommandPhase.FIXED_BASE, plan.modeSequence.phase)
        assertEquals(
            listOf(
                "# ublox-m8p2 init commands intentionally omitted",
                "# ublox-m8p2 fixed-base commands intentionally omitted",
            ),
            plan.startupCommands(),
        )
    }
}
```

- [ ] **Step 2: Run the new command-plan test and verify it fails**

Run:

```bash
sh ./gradlew :core:workflow:test --tests org.rtkcollector.core.workflow.ReceiverCommandPlanTest
```

Expected: compilation fails because `ReceiverCommandPlan`, `ReceiverCommandSequence`, `ReceiverCommandPhase`, `ReceiverCommandPlanExamples`, and `ReceiverCommandPlanValidator` do not exist yet.

- [ ] **Step 3: Add the command-plan model and validator**

Create `core/workflow/src/main/kotlin/org/rtkcollector/core/workflow/ReceiverCommandPlan.kt`:

```kotlin
package org.rtkcollector.core.workflow

enum class ReceiverCommandPhase {
    INIT,
    ROVER,
    BASE_CALIBRATION,
    FIXED_BASE,
    SHUTDOWN,
}

data class ReceiverCommandSequence(
    val id: String,
    val name: String,
    val phase: ReceiverCommandPhase,
    val commands: List<String> = emptyList(),
    val source: ReceiverCommandSource = ReceiverCommandSource.BUILT_IN_SAFE_REFERENCE,
) {
    companion object {
        fun emptyShutdown(): ReceiverCommandSequence =
            ReceiverCommandSequence(
                id = "empty-shutdown",
                name = "No shutdown commands",
                phase = ReceiverCommandPhase.SHUTDOWN,
                commands = emptyList(),
            )
    }
}

enum class ReceiverCommandSource {
    BUILT_IN_SAFE_REFERENCE,
    USER_SCRIPT_REFERENCE,
    PROFILE_REFERENCE,
}

data class ReceiverCommandPlan(
    val receiverProfileId: String,
    val initSequence: ReceiverCommandSequence,
    val modeSequence: ReceiverCommandSequence,
    val shutdownSequence: ReceiverCommandSequence = ReceiverCommandSequence.emptyShutdown(),
    val customCommandsRequested: Boolean = false,
) {
    fun startupSequences(): List<ReceiverCommandSequence> = listOf(initSequence, modeSequence)

    fun startupCommands(): List<String> = startupSequences().flatMap { it.commands }

    fun shutdownCommands(): List<String> = shutdownSequence.commands
}

object ReceiverCommandPlanExamples {
    fun genericRoverPlan(): ReceiverCommandPlan =
        ReceiverCommandPlan(
            receiverProfileId = "generic-nmea-rtcm",
            initSequence = ReceiverCommandSequence(
                id = "generic-init",
                name = "Generic NMEA/RTCM init reference",
                phase = ReceiverCommandPhase.INIT,
                commands = listOf("# Generic receiver init commands intentionally omitted"),
            ),
            modeSequence = ReceiverCommandSequence(
                id = "generic-rover",
                name = "Generic rover mode reference",
                phase = ReceiverCommandPhase.ROVER,
                commands = listOf("# Generic rover commands intentionally omitted"),
            ),
        )

    fun um980RoverPlan(
        shutdownSequence: ReceiverCommandSequence = ReceiverCommandSequence.emptyShutdown(),
    ): ReceiverCommandPlan =
        ReceiverCommandPlan(
            receiverProfileId = "um980-n4",
            initSequence = ReceiverCommandSequence(
                id = "um980-init-reference",
                name = "UM980/N4 init reference",
                phase = ReceiverCommandPhase.INIT,
                commands = listOf("# UM980 init commands intentionally omitted"),
            ),
            modeSequence = ReceiverCommandSequence(
                id = "um980-rover-reference",
                name = "UM980/N4 rover reference",
                phase = ReceiverCommandPhase.ROVER,
                commands = listOf("# UM980 rover commands intentionally omitted"),
            ),
            shutdownSequence = shutdownSequence,
        )

    fun um980BaseCalibrationPlan(): ReceiverCommandPlan =
        ReceiverCommandPlan(
            receiverProfileId = "um980-n4",
            initSequence = ReceiverCommandSequence(
                id = "um980-init-reference",
                name = "UM980/N4 init reference",
                phase = ReceiverCommandPhase.INIT,
                commands = listOf("# UM980 init commands intentionally omitted"),
            ),
            modeSequence = ReceiverCommandSequence(
                id = "um980-base-calibration-reference",
                name = "UM980/N4 base calibration reference",
                phase = ReceiverCommandPhase.BASE_CALIBRATION,
                commands = listOf("# UM980 base-calibration commands intentionally omitted"),
            ),
        )

    fun um980FixedBasePlan(): ReceiverCommandPlan =
        ReceiverCommandPlan(
            receiverProfileId = "um980-n4",
            initSequence = ReceiverCommandSequence(
                id = "um980-init-reference",
                name = "UM980/N4 init reference",
                phase = ReceiverCommandPhase.INIT,
                commands = listOf("# UM980 init commands intentionally omitted"),
            ),
            modeSequence = ReceiverCommandSequence(
                id = "um980-fixed-base-reference",
                name = "UM980/N4 fixed-base reference",
                phase = ReceiverCommandPhase.FIXED_BASE,
                commands = listOf("# UM980 fixed-base commands intentionally omitted"),
            ),
        )

    fun replayPlan(receiverProfileId: String = "file-replay"): ReceiverCommandPlan =
        ReceiverCommandPlan(
            receiverProfileId = receiverProfileId,
            initSequence = ReceiverCommandSequence(
                id = "replay-no-init",
                name = "Replay has no receiver init commands",
                phase = ReceiverCommandPhase.INIT,
                commands = emptyList(),
            ),
            modeSequence = ReceiverCommandSequence(
                id = "replay-no-mode",
                name = "Replay has no receiver mode commands",
                phase = ReceiverCommandPhase.INIT,
                commands = emptyList(),
            ),
        )

    fun safeReferencePlan(
        receiverProfileId: String,
        receiverRole: ReceiverRole,
    ): ReceiverCommandPlan {
        val phase = when (receiverRole) {
            ReceiverRole.ROVER -> ReceiverCommandPhase.ROVER
            ReceiverRole.BASE_CALIBRATION -> ReceiverCommandPhase.BASE_CALIBRATION
            ReceiverRole.FIXED_BASE -> ReceiverCommandPhase.FIXED_BASE
            ReceiverRole.REPLAY_TEST -> ReceiverCommandPhase.INIT
        }
        val modeLabel = when (phase) {
            ReceiverCommandPhase.INIT -> "replay"
            ReceiverCommandPhase.ROVER -> "rover"
            ReceiverCommandPhase.BASE_CALIBRATION -> "base-calibration"
            ReceiverCommandPhase.FIXED_BASE -> "fixed-base"
            ReceiverCommandPhase.SHUTDOWN -> "shutdown"
        }

        return ReceiverCommandPlan(
            receiverProfileId = receiverProfileId,
            initSequence = ReceiverCommandSequence(
                id = "$receiverProfileId-init-reference",
                name = "$receiverProfileId init reference",
                phase = ReceiverCommandPhase.INIT,
                commands = if (receiverRole == ReceiverRole.REPLAY_TEST) {
                    emptyList()
                } else {
                    listOf("# $receiverProfileId init commands intentionally omitted")
                },
            ),
            modeSequence = ReceiverCommandSequence(
                id = "$receiverProfileId-$modeLabel-reference",
                name = "$receiverProfileId $modeLabel reference",
                phase = phase,
                commands = if (receiverRole == ReceiverRole.REPLAY_TEST) {
                    emptyList()
                } else {
                    listOf("# $receiverProfileId $modeLabel commands intentionally omitted")
                },
            ),
        )
    }
}

class ReceiverCommandPlanValidator {
    fun validate(
        workflow: WorkflowSpec,
        commandPlan: ReceiverCommandPlan,
    ): WorkflowValidationResult {
        val errors = mutableListOf<WorkflowValidationMessage>()
        val warnings = mutableListOf<WorkflowValidationMessage>()

        if (commandPlan.receiverProfileId != workflow.receiverProfileId) {
            warnings += WorkflowValidationMessage(
                code = "COMMAND_PROFILE_DIFFERS_FROM_WORKFLOW_PROFILE",
                message = "Receiver command plan profile differs from the workflow receiver profile.",
            )
        }

        if (commandPlan.initSequence.phase != ReceiverCommandPhase.INIT) {
            errors += WorkflowValidationMessage(
                code = "INIT_SEQUENCE_REQUIRES_INIT_PHASE",
                message = "The receiver init sequence must use the INIT command phase.",
            )
        }

        val expectedMode = workflow.expectedCommandPhase()
        if (commandPlan.modeSequence.phase != expectedMode) {
            errors += WorkflowValidationMessage(
                code = "COMMAND_MODE_DOES_NOT_MATCH_WORKFLOW",
                message = "The selected receiver mode command sequence must match the workflow receiver role.",
            )
        }

        if (commandPlan.shutdownSequence.phase != ReceiverCommandPhase.SHUTDOWN) {
            errors += WorkflowValidationMessage(
                code = "SHUTDOWN_SEQUENCE_REQUIRES_SHUTDOWN_PHASE",
                message = "The receiver shutdown sequence must use the SHUTDOWN command phase.",
            )
        }

        if (commandPlan.customCommandsRequested && !workflow.receiverCapabilities.supportsCustomInitCommands) {
            errors += WorkflowValidationMessage(
                code = "CUSTOM_COMMANDS_REQUIRE_CAPABILITY",
                message = "Custom receiver command scripts require receiver custom-init capability.",
            )
        }

        return WorkflowValidationResult(
            valid = errors.isEmpty(),
            errors = errors,
            warnings = warnings,
        )
    }

    private fun WorkflowSpec.expectedCommandPhase(): ReceiverCommandPhase =
        when (receiverRole) {
            ReceiverRole.ROVER -> ReceiverCommandPhase.ROVER
            ReceiverRole.BASE_CALIBRATION -> ReceiverCommandPhase.BASE_CALIBRATION
            ReceiverRole.FIXED_BASE -> ReceiverCommandPhase.FIXED_BASE
            ReceiverRole.REPLAY_TEST -> ReceiverCommandPhase.INIT
        }
}
```

- [ ] **Step 4: Run command-plan tests**

Run:

```bash
sh ./gradlew :core:workflow:test --tests org.rtkcollector.core.workflow.ReceiverCommandPlanTest
```

Expected: PASS.

- [ ] **Step 5: Commit Task 1**

```bash
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector add core/workflow/src/main/kotlin/org/rtkcollector/core/workflow/ReceiverCommandPlan.kt core/workflow/src/test/kotlin/org/rtkcollector/core/workflow/ReceiverCommandPlanTest.kt
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector commit -m "Add receiver command plan model"
```

## Task 2: Workflow Dry-Run Session Model

**Files:**
- Create: `core/workflow/src/main/kotlin/org/rtkcollector/core/workflow/WorkflowDryRunSession.kt`
- Create: `core/workflow/src/test/kotlin/org/rtkcollector/core/workflow/WorkflowDryRunSessionTest.kt`

- [ ] **Step 1: Write dry-run lifecycle tests first**

Create `core/workflow/src/test/kotlin/org/rtkcollector/core/workflow/WorkflowDryRunSessionTest.kt`:

```kotlin
package org.rtkcollector.core.workflow

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WorkflowDryRunSessionTest {
    @Test
    fun `valid workflow and command plan can start dry-run recording`() {
        val session = WorkflowDryRunSession.create(
            workflow = WorkflowExamples.plainRoverRecording(ReceiverCapabilityFixtures.um980N4()),
            commandPlan = ReceiverCommandPlanExamples.um980RoverPlan(),
        )

        val recording = session.start()

        assertEquals(DryRunRecordingState.RECORDING, recording.state)
        assertTrue(recording.canStop)
        assertFalse(recording.canStart)
        assertEquals(2, recording.startupCommands.size)
    }

    @Test
    fun `invalid workflow cannot start dry-run recording`() {
        val session = WorkflowDryRunSession.create(
            workflow = WorkflowExamples.fixedBaseFromBasePosition(ReceiverCapabilityFixtures.ubloxM8p0()),
            commandPlan = ReceiverCommandPlanExamples.um980FixedBasePlan(),
        )

        val result = session.start()

        assertEquals(DryRunRecordingState.BLOCKED, result.state)
        assertFalse(result.validation.valid)
        assertTrue(result.validation.errors.any { it.code == "FIXED_BASE_REQUIRES_CAPABILITY" })
    }

    @Test
    fun `stop without shutdown commands records shutdown not configured`() {
        val recording = WorkflowDryRunSession.create(
            workflow = WorkflowExamples.plainRoverRecording(ReceiverCapabilityFixtures.um980N4()),
            commandPlan = ReceiverCommandPlanExamples.um980RoverPlan(),
        ).start()

        val stopped = recording.stop(transportAvailable = true)

        assertEquals(DryRunRecordingState.STOPPED, stopped.state)
        assertEquals(ShutdownCommandStatus.NOT_CONFIGURED, stopped.shutdownStatus)
        assertFalse(stopped.canStop)
    }

    @Test
    fun `stop with configured shutdown commands records sent status`() {
        val recording = WorkflowDryRunSession.create(
            workflow = WorkflowExamples.plainRoverRecording(ReceiverCapabilityFixtures.um980N4()),
            commandPlan = ReceiverCommandPlanExamples.um980RoverPlan(
                shutdownSequence = ReceiverCommandSequence(
                    id = "shutdown",
                    name = "Shutdown",
                    phase = ReceiverCommandPhase.SHUTDOWN,
                    commands = listOf("# shutdown commands intentionally omitted"),
                ),
            ),
        ).start()

        val stopped = recording.stop(transportAvailable = true)

        assertEquals(DryRunRecordingState.STOPPED, stopped.state)
        assertEquals(ShutdownCommandStatus.SENT, stopped.shutdownStatus)
        assertEquals(listOf("# shutdown commands intentionally omitted"), stopped.shutdownCommands)
    }

    @Test
    fun `stop with configured shutdown commands and missing transport records skipped status`() {
        val recording = WorkflowDryRunSession.create(
            workflow = WorkflowExamples.plainRoverRecording(ReceiverCapabilityFixtures.um980N4()),
            commandPlan = ReceiverCommandPlanExamples.um980RoverPlan(
                shutdownSequence = ReceiverCommandSequence(
                    id = "shutdown",
                    name = "Shutdown",
                    phase = ReceiverCommandPhase.SHUTDOWN,
                    commands = listOf("# shutdown commands intentionally omitted"),
                ),
            ),
        ).start()

        val stopped = recording.stop(transportAvailable = false)

        assertEquals(DryRunRecordingState.STOPPED, stopped.state)
        assertEquals(ShutdownCommandStatus.SKIPPED_TRANSPORT_UNAVAILABLE, stopped.shutdownStatus)
    }
}
```

- [ ] **Step 2: Run dry-run lifecycle tests and verify they fail**

Run:

```bash
sh ./gradlew :core:workflow:test --tests org.rtkcollector.core.workflow.WorkflowDryRunSessionTest
```

Expected: compilation fails because the dry-run session model does not exist yet.

- [ ] **Step 3: Add dry-run session model**

Create `core/workflow/src/main/kotlin/org/rtkcollector/core/workflow/WorkflowDryRunSession.kt`:

```kotlin
package org.rtkcollector.core.workflow

enum class DryRunRecordingState {
    READY,
    RECORDING,
    BLOCKED,
    STOPPED,
}

enum class ShutdownCommandStatus {
    NOT_ATTEMPTED,
    NOT_CONFIGURED,
    SENT,
    SKIPPED_TRANSPORT_UNAVAILABLE,
}

data class RecordingObservables(
    val elapsedSeconds: Long = 0,
    val receiverRxBytes: Long = 0,
    val txToReceiverBytes: Long = 0,
    val correctionInputBytes: Long = 0,
    val serialThroughputBytesPerSecond: Long = 0,
    val latestDeviceSolution: String = "No device solution yet",
    val ntripState: String = "Not configured",
    val correctionAgeSeconds: Long? = null,
    val rawObservationStatus: String = "Not observed yet",
    val parserStatus: String = "No parser failures",
    val recordingHealth: String = "Raw recorder dry-run ready",
)

data class WorkflowDryRunSession(
    val workflow: WorkflowSpec,
    val commandPlan: ReceiverCommandPlan,
    val validation: WorkflowValidationResult,
    val state: DryRunRecordingState,
    val observables: RecordingObservables = RecordingObservables(),
    val startupCommands: List<String> = emptyList(),
    val shutdownCommands: List<String> = emptyList(),
    val shutdownStatus: ShutdownCommandStatus = ShutdownCommandStatus.NOT_ATTEMPTED,
) {
    val canStart: Boolean get() = state == DryRunRecordingState.READY && validation.valid

    val canStop: Boolean get() = state == DryRunRecordingState.RECORDING

    fun start(): WorkflowDryRunSession =
        if (!validation.valid) {
            copy(state = DryRunRecordingState.BLOCKED)
        } else {
            copy(
                state = DryRunRecordingState.RECORDING,
                startupCommands = commandPlan.startupCommands(),
                observables = observables.copy(
                    recordingHealth = "Raw recorder dry-run active",
                    rawObservationStatus = if (workflow.recording.recordRawObservationsRequested) {
                        "Raw observations requested at ${workflow.recording.rawObservationMinimumRateHz ?: 1.0} Hz or higher"
                    } else {
                        "Raw observations not requested for this receiver/profile"
                    },
                    ntripState = if (workflow.correctionSource is CorrectionSourceSpec.Ntrip) {
                        "NTRIP dry-run configured"
                    } else {
                        "Not configured"
                    },
                ),
            )
        }

    fun stop(transportAvailable: Boolean): WorkflowDryRunSession {
        val shutdown = commandPlan.shutdownCommands()
        val status = when {
            shutdown.isEmpty() -> ShutdownCommandStatus.NOT_CONFIGURED
            transportAvailable -> ShutdownCommandStatus.SENT
            else -> ShutdownCommandStatus.SKIPPED_TRANSPORT_UNAVAILABLE
        }

        return copy(
            state = DryRunRecordingState.STOPPED,
            shutdownCommands = shutdown,
            shutdownStatus = status,
            observables = observables.copy(recordingHealth = "Raw recorder dry-run stopped"),
        )
    }

    companion object {
        fun create(
            workflow: WorkflowSpec,
            commandPlan: ReceiverCommandPlan,
        ): WorkflowDryRunSession {
            val workflowValidation = WorkflowValidator().validate(workflow)
            val commandValidation = ReceiverCommandPlanValidator().validate(workflow, commandPlan)
            val combined = WorkflowValidationResult(
                valid = workflowValidation.valid && commandValidation.valid,
                errors = workflowValidation.errors + commandValidation.errors,
                warnings = workflowValidation.warnings + commandValidation.warnings,
            )

            return WorkflowDryRunSession(
                workflow = workflow,
                commandPlan = commandPlan,
                validation = combined,
                state = if (combined.valid) DryRunRecordingState.READY else DryRunRecordingState.BLOCKED,
            )
        }
    }
}
```

- [ ] **Step 4: Run dry-run lifecycle tests**

Run:

```bash
sh ./gradlew :core:workflow:test --tests org.rtkcollector.core.workflow.WorkflowDryRunSessionTest
```

Expected: PASS.

- [ ] **Step 5: Run all workflow tests**

Run:

```bash
sh ./gradlew :core:workflow:test
```

Expected: PASS.

- [ ] **Step 6: Commit Task 2**

```bash
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector add core/workflow/src/main/kotlin/org/rtkcollector/core/workflow/WorkflowDryRunSession.kt core/workflow/src/test/kotlin/org/rtkcollector/core/workflow/WorkflowDryRunSessionTest.kt
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector commit -m "Add workflow dry-run session model"
```

## Task 3: Minimal Android App Shell

**Files:**
- Modify: `build.gradle.kts`
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Delete: `app/src/main/kotlin/org/rtkcollector/app/BootstrapActivity.kt`
- Create: `app/src/main/kotlin/org/rtkcollector/app/MainActivity.kt`

- [ ] **Step 1: Update Gradle plugins for Android**

Modify the root `build.gradle.kts` plugin block to include Android plugins:

```kotlin
plugins {
    id("com.android.application") version "9.2.0" apply false
    kotlin("android") version "2.3.21" apply false
    kotlin("jvm") version "2.3.21" apply false
}
```

Keep the existing `allprojects` and `subprojects` blocks. Replace the root `assembleDebug` alias with this guarded registration so it does not conflict with the real Android `:app:assembleDebug` task:

```kotlin
if (tasks.findByName("assembleDebug") == null) {
    tasks.register("assembleDebug") {
        description = "Runs debug assembly for the Android app when present."
        group = "build"
        dependsOn(":app:assembleDebug")
    }
}
```

- [ ] **Step 2: Convert app module to Android application**

Replace `app/build.gradle.kts` with:

```kotlin
plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "org.rtkcollector.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.rtkcollector.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }
}

dependencies {
    implementation(project(":core:workflow"))
}
```

- [ ] **Step 3: Add Android manifest**

Create `app/src/main/AndroidManifest.xml`:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:allowBackup="false"
        android:label="RtkCollector"
        android:supportsRtl="true"
        android:theme="@style/AppTheme">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

- [ ] **Step 4: Add minimal style resource**

Create `app/src/main/res/values/styles.xml`:

```xml
<resources>
    <style name="AppTheme" parent="android:style/Theme.Material.Light.NoActionBar">
        <item name="android:fontFamily">sans</item>
        <item name="android:windowLightStatusBar">true</item>
        <item name="android:navigationBarColor">#F7F7F2</item>
        <item name="android:statusBarColor">#F7F7F2</item>
        <item name="android:colorAccent">#006C67</item>
    </style>
</resources>
```

- [ ] **Step 5: Replace bootstrap object with native Activity UI**

Delete `app/src/main/kotlin/org/rtkcollector/app/BootstrapActivity.kt`.

Create `app/src/main/kotlin/org/rtkcollector/app/MainActivity.kt`:

```kotlin
package org.rtkcollector.app

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import org.rtkcollector.core.workflow.ReceiverCapabilityFixtures
import org.rtkcollector.core.workflow.ReceiverCommandPlan
import org.rtkcollector.core.workflow.ReceiverCommandPlanExamples
import org.rtkcollector.core.workflow.SessionArtifact
import org.rtkcollector.core.workflow.WorkflowDryRunSession
import org.rtkcollector.core.workflow.WorkflowExamples
import org.rtkcollector.core.workflow.WorkflowSpec

class MainActivity : Activity() {
    private lateinit var workflowSpinner: Spinner
    private lateinit var receiverSpinner: Spinner
    private lateinit var detailsText: TextView
    private lateinit var validationText: TextView
    private lateinit var monitorText: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button

    private val receiverOptions = listOf(
        ReceiverOption("UM980/N4", "um980-n4"),
        ReceiverOption("u-blox M8P-0", "ublox-m8p0"),
        ReceiverOption("u-blox M8P-2", "ublox-m8p2"),
        ReceiverOption("u-blox M8T", "ublox-m8t"),
        ReceiverOption("Generic NMEA + RTCM", "generic-nmea-rtcm"),
    )

    private var session: WorkflowDryRunSession? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val title = TextView(this).apply {
            text = "RtkCollector"
            textSize = 26f
        }
        val subtitle = TextView(this).apply {
            text = "Bootstrap dry-run UI. No USB, NTRIP networking, receiver TX, maps, shapefiles or GIS editing."
            textSize = 14f
        }

        workflowSpinner = Spinner(this)
        receiverSpinner = Spinner(this)
        detailsText = TextView(this).apply { textSize = 14f }
        validationText = TextView(this).apply { textSize = 14f }
        monitorText = TextView(this).apply { textSize = 14f }
        startButton = Button(this).apply { text = "Start dry-run recording" }
        stopButton = Button(this).apply {
            text = "Stop dry-run recording"
            isEnabled = false
        }

        workflowSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            workflowOptions().map { it.label },
        )
        receiverSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            receiverOptions.map { it.label },
        )

        workflowSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                rebuildSession()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        receiverSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                rebuildSession()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        startButton.setOnClickListener {
            session = session?.start()
            render()
        }
        stopButton.setOnClickListener {
            session = session?.stop(transportAvailable = true)
            render()
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(label("Workflow"))
        root.addView(workflowSpinner)
        root.addView(label("Receiver profile"))
        root.addView(receiverSpinner)
        root.addView(detailsText)
        root.addView(validationText)
        root.addView(startButton)
        root.addView(stopButton)
        root.addView(monitorText)

        setContentView(ScrollView(this).apply { addView(root) })
        rebuildSession()
    }

    private fun rebuildSession() {
        val workflowOption = workflowOptions()[workflowSpinner.selectedItemPosition.coerceAtLeast(0)]
        val receiverOption = receiverOptions[receiverSpinner.selectedItemPosition.coerceAtLeast(0)]
        val workflow = workflowOption.build(receiverOption)
        val commandPlan = commandPlanFor(workflow)
        session = WorkflowDryRunSession.create(workflow, commandPlan)
        render()
    }

    private fun render() {
        val current = session ?: return
        val workflow = current.workflow
        val commandPlan = current.commandPlan

        detailsText.text = buildString {
            appendLine()
            appendLine("Workflow: ${workflow.name}")
            appendLine("Receiver role: ${workflow.receiverRole}")
            appendLine("Receiver profile: ${workflow.receiverProfileId}")
            appendLine("Init sequence: ${commandPlan.initSequence.name}")
            appendLine("Mode sequence: ${commandPlan.modeSequence.name}")
            appendLine("Shutdown sequence: ${commandPlan.shutdownSequence.name}")
            appendLine("Startup commands:")
            commandPlan.startupCommands().forEach { appendLine("  $it") }
            appendLine("Expected artifacts:")
            workflow.recording.expectedSessionArtifacts.sortedBy(SessionArtifact::name).forEach {
                appendLine("  ${it.name}")
            }
        }

        validationText.text = buildString {
            appendLine()
            appendLine(if (current.validation.valid) "Validation: valid" else "Validation: blocked")
            current.validation.errors.forEach { appendLine("ERROR ${it.code}: ${it.message}") }
            current.validation.warnings.forEach { appendLine("WARN ${it.code}: ${it.message}") }
        }

        monitorText.text = buildString {
            appendLine()
            appendLine("Dry-run state: ${current.state}")
            appendLine("Recording health: ${current.observables.recordingHealth}")
            appendLine("Raw observations: ${current.observables.rawObservationStatus}")
            appendLine("NTRIP: ${current.observables.ntripState}")
            appendLine("Shutdown status: ${current.shutdownStatus}")
        }

        startButton.isEnabled = current.canStart
        stopButton.isEnabled = current.canStop
    }

    private fun workflowOptions(): List<WorkflowOption> =
        listOf(
            WorkflowOption("Plain rover recording") { receiver ->
                WorkflowExamples.plainRoverRecording(receiver.capabilities(), receiver.profileId)
            },
            WorkflowOption("Rover + NTRIP to receiver") { receiver ->
                WorkflowExamples.roverWithNtripToReceiver(receiver.capabilities(), receiver.profileId)
            },
            WorkflowOption("Temporary base preparation") { receiver ->
                WorkflowExamples.temporaryBasePreparation(receiver.capabilities(), receiver.profileId)
            },
            WorkflowOption("Fixed base from accepted position") { receiver ->
                WorkflowExamples.fixedBaseFromBasePosition(receiver.capabilities(), receiver.profileId)
            },
            WorkflowOption("Replay test") { receiver ->
                WorkflowExamples.replayTest(receiver.capabilities(), receiver.profileId)
            },
        )

    private fun commandPlanFor(workflow: WorkflowSpec): ReceiverCommandPlan =
        ReceiverCommandPlanExamples.safeReferencePlan(
            receiverProfileId = workflow.receiverProfileId,
            receiverRole = workflow.receiverRole,
        )

    private fun label(text: String): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 12f
        }

    private data class ReceiverOption(
        val label: String,
        val profileId: String,
    ) {
        fun capabilities() =
            when (profileId) {
                "um980-n4" -> ReceiverCapabilityFixtures.um980N4()
                "ublox-m8p0" -> ReceiverCapabilityFixtures.ubloxM8p0()
                "ublox-m8p2" -> ReceiverCapabilityFixtures.ubloxM8p2()
                "ublox-m8t" -> ReceiverCapabilityFixtures.ubloxM8t()
                else -> ReceiverCapabilityFixtures.genericNmeaRtcm()
            }
    }

    private data class WorkflowOption(
        val label: String,
        val build: (ReceiverOption) -> WorkflowSpec,
    )
}
```

- [ ] **Step 6: Run pure Kotlin tests after Gradle changes**

Run:

```bash
sh ./gradlew :core:workflow:test
```

Expected: PASS. If dependency resolution fails because the Android Gradle Plugin is not cached and network is restricted, rerun with the required sandbox/network approval and report that approval was needed.

- [ ] **Step 7: Try Android debug assembly**

Run:

```bash
. /storage/3830-3863/Termux/AndroidSDK/env.sh
sh ./gradlew :app:assembleDebug
```

Expected on a normal Android SDK host: PASS and an APK under `app/build/outputs/apk/debug/`. Expected possible local Termux limitation: failure when AGP invokes x86-64 native SDK binaries such as `aapt2`. If that happens, do not hide it; report the exact failing tool and keep pure Kotlin tests green.

- [ ] **Step 8: Commit Task 3**

```bash
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector add build.gradle.kts app/build.gradle.kts app/src/main/AndroidManifest.xml app/src/main/res/values/styles.xml app/src/main/kotlin/org/rtkcollector/app/MainActivity.kt
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector add -u app/src/main/kotlin/org/rtkcollector/app/BootstrapActivity.kt
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector commit -m "Add Android workflow dry-run UI"
```

## Task 4: Documentation Updates

**Files:**
- Modify: `docs/user-workflows.md`
- Modify: `docs/workflows.md`
- Modify: `README.md`

- [ ] **Step 1: Update user workflow docs**

Add this section near the V1 workflow overview in `docs/user-workflows.md`:

```markdown
## Bootstrap UI Dry Run

The first Android UI is a dry-run workflow launcher. It lets the user choose a
workflow, receiver profile, command plan and expected recording artifacts, then
runs validation before entering a simulated recording monitor.

The dry-run UI intentionally does not open USB, send serial commands, connect to
NTRIP, start a foreground service or write session files. Its purpose is to keep
the future recording implementation aligned with validated workflow plans.

Receiver startup commands are represented as:

1. init sequence;
2. exactly one mode-specific sequence: rover, base-calibration or fixed-base.

Shutdown commands are optional and usually empty. They are modelled as a separate
post-stop phase so later receiver TX bytes can be logged without modifying the
authoritative receiver RX stream.
```

- [ ] **Step 2: Update workflow specification docs**

Add this paragraph to `docs/workflows.md` under the lifecycle or validation section:

```markdown
### Receiver Command Plans

Each startable workflow is paired with a receiver command plan. Startup commands
are ordered as the profile init sequence followed by exactly one workflow
mode-specific sequence. The model must reject conflicting mode sequences. Runtime
correction bytes are not command scripts, but they share the receiver TX path and
must be recorded separately from receiver RX when implemented. Optional shutdown
commands are a post-stop phase and must never rewrite or annotate
`receiver-rx.raw`.
```

- [ ] **Step 3: Update README**

Add this status note to the development-status section of `README.md`:

```markdown
The bootstrap Android UI is a dry-run workflow launcher and recording monitor.
It validates workflow and command-plan choices, but it does not yet implement
USB capture, receiver serial TX, NTRIP networking, foreground services or real
session file writing.
```

- [ ] **Step 4: Run documentation grep checks**

Run:

```bash
rg -n "map|shapefile|GIS|RTKLIB|NTRIP|USB" README.md docs/user-workflows.md docs/workflows.md
```

Expected: any `map`, `shapefile`, or `GIS` matches are explicit non-goals; `RTKLIB`, `NTRIP`, and `USB` matches are either out-of-scope notices or workflow documentation, not accidental implementation claims.

- [ ] **Step 5: Commit Task 4**

```bash
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector add README.md docs/user-workflows.md docs/workflows.md
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector commit -m "Document simple workflow dry-run UI"
```

## Task 5: Final Validation

**Files:**
- No new source files. Validate the integrated branch.

- [ ] **Step 1: Check repository status**

Run:

```bash
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector status --short --branch
```

Expected: branch contains only intended committed changes plus untracked `.superpowers/` scratch files. Do not commit `.superpowers/`.

- [ ] **Step 2: Run clean**

Run:

```bash
. /storage/3830-3863/Termux/AndroidSDK/env.sh
sh ./gradlew clean
```

Expected: PASS, unless local Android native build tools fail due to x86-64/aarch64 mismatch. If that happens, capture the exact failure and continue with pure Kotlin module tests.

- [ ] **Step 3: Run unit tests**

Run:

```bash
sh ./gradlew test
```

Expected: PASS for pure Kotlin modules. If Android plugin configuration prevents local execution because dependencies cannot be resolved, request network approval and rerun.

- [ ] **Step 4: Run Android debug assembly**

Run:

```bash
sh ./gradlew assembleDebug
```

Expected on normal host or CI: PASS. On this Termux device, a documented failure from x86-64 Android SDK native binaries is acceptable only if pure Kotlin tests pass and the failure is reported accurately.

- [ ] **Step 5: Final review and commit handling**

Run the requested final review workflow:

```bash
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector log --oneline --decorate -5
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector status --short --branch
```

Then use `$review-and-commit` if there are remaining uncommitted intended changes, and push only after validation results are understood.
