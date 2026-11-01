package org.rtkcollector.app.ui.common

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import org.rtkcollector.app.recording.RecordingForegroundService
import org.rtkcollector.app.recording.RecordingStopConfirmation
import org.rtkcollector.app.recording.StopConfirmationPhase
import org.rtkcollector.app.recording.StopConfirmationRefreshLoop
import org.rtkcollector.app.settings.StopConfirmationSettings

/** One native dialog also covers the legacy Activity and configuration-recovery UI. */
object StopRecordingConfirmation {
    private var active: AlertDialog? = null

    fun dismissForNavigation(context: Context) {
        var current = context
        while (current is ContextWrapper && current !is Activity) current = current.baseContext
        active?.takeIf { it.ownerActivity === current }?.dismiss()
    }

    fun show(context: Context, controlId: String) {
        var current = context
        while (current is ContextWrapper && current !is Activity) current = current.baseContext
        val activity = current as? Activity ?: return
        if (activity.isFinishing || activity.isDestroyed || active?.isShowing == true) return
        val request = RecordingStopConfirmation.open(controlId, SystemClock.elapsedRealtime(),
            StopConfirmationSettings(activity).timing) ?: return
        val handler = Handler(Looper.getMainLooper())
        var outcome = "cancelled"
        var submitted = false
        val dialog = AlertDialog.Builder(activity).setTitle("Stop recording?")
            .setMessage("Recording continues until you confirm.")
            .setPositiveButton("Confirm", null).setNegativeButton("Cancel", null).create()
        dialog.setOwnerActivity(activity)
        active = dialog
        val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityPaused(a: Activity) { if (a === activity) { outcome = "backgrounded"; dialog.dismiss() } }
            override fun onActivityDestroyed(a: Activity) { if (a === activity) dialog.dismiss() }
            override fun onActivityCreated(a: Activity, state: Bundle?) {}
            override fun onActivityStarted(a: Activity) {}
            override fun onActivityResumed(a: Activity) {}
            override fun onActivityStopped(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, state: Bundle) {}
        }
        val refresh = StopConfirmationRefreshLoop(
            window = request.window,
            elapsedMillis = SystemClock::elapsedRealtime,
            uptimeMillis = SystemClock::uptimeMillis,
            isCurrent = { dialog.isShowing && RecordingStopConfirmation.pending()?.requestId == request.requestId },
            render = { frame ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).apply {
                    isEnabled = frame.phase == StopConfirmationPhase.ACTIVE
                    val label = "Confirm (${frame.remainingSeconds}s)"
                    if (text.toString() != label) text = label
                }
            },
            dismiss = {
                if (dialog.isShowing) { outcome = "expired"; dialog.dismiss() }
            },
            postAtUptime = { callback, at -> handler.postAtTime(callback, at); Unit },
            remove = { callback -> handler.removeCallbacks(callback) },
        )
        dialog.setOnDismissListener {
            refresh.cancel()
            activity.application.unregisterActivityLifecycleCallbacks(callbacks)
            RecordingStopConfirmation.cancel(request.requestId, outcome)
            if (active === dialog) active = null
        }
        fun initializeButtons() {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (!submitted && dialog.isShowing && dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled &&
                    RecordingStopConfirmation.pending()?.requestId == request.requestId &&
                    request.window.phase(SystemClock.elapsedRealtime()) == StopConfirmationPhase.ACTIVE) {
                    submitted = true
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                    runCatching { activity.startService(RecordingForegroundService.confirmedStopIntent(activity, request.requestId)) }
                        .onSuccess { outcome = "submitted" }
                        .onFailure {
                            outcome = "dispatch-failed"
                            Toast.makeText(activity, "Could not request Stop. Recording continues.", Toast.LENGTH_LONG).show()
                        }
                    // Keep the one-use authorization until service acceptance or expiry.
                    if (outcome == "submitted") {
                        refresh.cancel()
                        dialog.setOnDismissListener {
                            activity.application.unregisterActivityLifecycleCallbacks(callbacks)
                            if (active === dialog) active = null
                        }
                    }
                    dialog.dismiss()
                }
            }
            refresh.run()
        }
        activity.application.registerActivityLifecycleCallbacks(callbacks)
        try {
            dialog.show()
            initializeButtons()
        } catch (error: RuntimeException) {
            refresh.cancel()
            activity.application.unregisterActivityLifecycleCallbacks(callbacks)
            RecordingStopConfirmation.cancel(request.requestId, "unavailable")
            if (active === dialog) active = null
        }
    }
}
