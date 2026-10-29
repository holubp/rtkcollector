package org.rtkcollector.app.alarms

import java.io.Closeable
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** Bounded advisory mailbox. Capture producers never call effects or wait for this owner. */
class RecordingAlarmController(private val clock: () -> Long, private val onChange: (AlarmDesiredState) -> Unit,
    private val onInvalidate: (Long) -> Unit = {}) : Closeable {
    private data class Mailbox(val generation: Long = -1, val commands: Map<String, AlarmCommand> = emptyMap())
    private val mailbox = AtomicReference(Mailbox())
    private val stopped = AtomicLong(-1)
    private val closed = AtomicBoolean(false)
    private val ready = Semaphore(0)
    private val policy = RecordingAlarmPolicy()
    @Volatile private var latest = AlarmDesiredState.EMPTY
    private val worker = Thread({
        while (!closed.get()) {
            try { ready.tryAcquire(250, TimeUnit.MILLISECONDS) } catch (_: InterruptedException) { break }
            val batch = mailbox.getAndUpdate { it.copy(commands = emptyMap()) }
            if (batch.generation < 0 || mailbox.get().generation != batch.generation) continue
            val commands = batch.commands.values.distinct().sortedWith(compareBy<AlarmCommand>(
                { if (it is AlarmCommand.Begin) 0 else 1 }, { elapsed(it) }))
            var result = latest
            commands.forEach { result = policy.accept(it) }
            result = policy.accept(if (stopped.get() == batch.generation)
                AlarmCommand.UserStop(batch.generation, clock()) else AlarmCommand.Tick(batch.generation, clock()))
            if (result != latest) { latest = result; runCatching { onChange(result) } }
        }
    }, "rtkcollector-alarm-policy").apply { isDaemon = true }
    init { runCatching { worker.start() }.onFailure { closed.set(true) } }

    fun post(command: AlarmCommand): Boolean {
        if (closed.get()) return false
        if (command is AlarmCommand.UserStop) { userStop(command.generation); return true }
        var accepted = false
        mailbox.updateAndGet { old ->
            accepted = false
            if (command is AlarmCommand.Begin) {
                if (command.generation <= old.generation) old else {
                    accepted = true; Mailbox(command.generation, mapOf("begin" to command))
                }
            } else if (command.generation != old.generation || stopped.get() == command.generation) old
            else {
                val key = when (command) {
                    is AlarmCommand.Position -> "position:${command.evidence.sourceId.take(80)}"
                    is AlarmCommand.CauseRaised -> "cause:${command.key}"
                    is AlarmCommand.CauseRecovered -> "cause:${command.key}"
                    is AlarmCommand.Acknowledge -> "ack:${command.episodes.firstOrNull()?.cause?.category}"
                    else -> command.javaClass.simpleName
                }
                if (old.commands.size >= 128 && key !in old.commands) old else {
                    accepted = true
                    val next = old.commands + (key to command)
                    old.copy(commands = if (command is AlarmCommand.Position && command.evidence.valid)
                        next + ("valid:${command.evidence.sourceId.take(80)}" to command) else next)
                }
            }
        }
        if (accepted && ready.availablePermits() == 0) ready.release()
        return accepted
    }
    fun userStop(generation: Long) {
        if (mailbox.get().generation != generation) return
        stopped.set(generation)
        runCatching { onInvalidate(generation) }
        if (ready.availablePermits() == 0) ready.release()
    }
    fun snapshot(): AlarmDesiredState = latest
    fun isCurrent(state: AlarmDesiredState): Boolean = !closed.get() &&
        mailbox.get().generation == state.generation && stopped.get() != state.generation && latest == state
    override fun close() { closed.set(true); worker.interrupt() }

    private fun elapsed(command: AlarmCommand): Long = when (command) {
        is AlarmCommand.Begin -> command.nowElapsedMillis
        is AlarmCommand.Position -> command.evidence.receivedElapsedMillis
        is AlarmCommand.CauseRaised -> command.nowElapsedMillis
        is AlarmCommand.CauseRecovered -> command.nowElapsedMillis
        is AlarmCommand.Tick -> command.nowElapsedMillis
        is AlarmCommand.Acknowledge -> command.nowElapsedMillis
        is AlarmCommand.SettingsChanged -> command.nowElapsedMillis
        is AlarmCommand.UserStop -> command.nowElapsedMillis
        is AlarmCommand.TerminalStop -> command.nowElapsedMillis
        is AlarmCommand.DeliveryChanged -> command.nowElapsedMillis
    }
}
