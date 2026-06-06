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

    private val workflowOptions = listOf(
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
            text = "Workflow dry-run shell"
            textSize = 14f
        }
        val status = TextView(this).apply {
            text = "No USB, NTRIP networking, receiver TX, real file writing, RTKLIB, maps, shapefiles or GIS editing."
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
            workflowOptions.map { it.label },
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
        root.addView(status)
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
        val workflowOption = workflowOptions[workflowSpinner.selectedItemPosition.coerceAtLeast(0)]
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
            appendLine("Elapsed seconds: ${current.observables.elapsedSeconds}")
            appendLine("Receiver RX bytes: ${current.observables.receiverRxBytes}")
            appendLine("TX to receiver bytes: ${current.observables.txToReceiverBytes}")
            appendLine("Correction input bytes: ${current.observables.correctionInputBytes}")
            appendLine("Serial throughput B/s: ${current.observables.serialThroughputBytesPerSecond}")
            appendLine("Device solution: ${current.observables.latestDeviceSolution}")
            appendLine("Recording health: ${current.observables.recordingHealth}")
            appendLine("Raw observations: ${current.observables.rawObservationStatus}")
            appendLine("NTRIP: ${current.observables.ntripState}")
            appendLine("Correction age seconds: ${current.observables.correctionAgeSeconds ?: "n/a"}")
            appendLine("Parser: ${current.observables.parserStatus}")
            appendLine("Shutdown status: ${current.shutdownStatus}")
        }

        workflowSpinner.isEnabled = !current.canStop
        receiverSpinner.isEnabled = !current.canStop
        startButton.isEnabled = current.canStart
        stopButton.isEnabled = current.canStop
    }

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
