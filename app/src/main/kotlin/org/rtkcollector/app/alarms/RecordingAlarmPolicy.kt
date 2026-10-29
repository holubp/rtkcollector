package org.rtkcollector.app.alarms

/** Pure, single-owner policy. All deadlines use supplied monotonic receipt time. */
class RecordingAlarmPolicy {
    private var generation = -1L
    private var settings = AlarmSettingsSnapshot.defaults()
    private var stopped = true
    private var terminal = false
    private var now = 0L
    private var lastValid: Long? = null
    private var episodeRevision = 0L
    private val sequences = mutableMapOf<String, Long>()
    private val causes = linkedMapOf<AlarmCauseKey, AlarmEpisode>()
    private val acknowledged = mutableSetOf<AlarmEpisodeId>()
    private var desired = AlarmDesiredState.EMPTY
    private val positionKey = AlarmCauseKey(AlarmCategory.POSITION_LOST, "position", "stale")

    fun accept(command: AlarmCommand): AlarmDesiredState {
        if (command is AlarmCommand.Begin) {
            if (command.generation <= generation) return desired
            generation = command.generation; settings = command.settings
            now = command.nowElapsedMillis; stopped = false; terminal = false; lastValid = null
            causes.clear(); acknowledged.clear(); sequences.clear()
            return render()
        }
        if (command.generation != generation || stopped) return desired
        when (command) {
            is AlarmCommand.UserStop -> { stopped = true; terminal = false; causes.clear(); acknowledged.clear() }
            is AlarmCommand.TerminalStop -> {
                now = maxOf(now, command.nowElapsedMillis)
                if (!terminal) { causes.clear(); acknowledged.clear(); terminal = true; raise(command.key, command.detail, true) }
            }
            is AlarmCommand.Position -> if (!terminal) {
                val e = command.evidence
                if (e.sequence > (sequences[e.sourceId] ?: -1L) && e.receivedElapsedMillis >= (lastValid ?: 0)) {
                    if (sequences.size < 16 || sequences.containsKey(e.sourceId)) sequences[e.sourceId] = e.sequence
                    if (e.valid) { lastValid = e.receivedElapsedMillis; remove(positionKey) }
                    now = maxOf(now, e.receivedElapsedMillis)
                }
            }
            is AlarmCommand.CauseRaised -> if (!terminal) {
                now = maxOf(now, command.nowElapsedMillis); raise(command.key, command.detail, false)
            }
            is AlarmCommand.CauseRecovered -> if (!terminal) {
                now = maxOf(now, command.nowElapsedMillis); remove(command.key)
            }
            is AlarmCommand.Tick -> now = maxOf(now, command.nowElapsedMillis)
            is AlarmCommand.Acknowledge -> acknowledged.addAll(command.episodes.intersect(causes.values.map { it.id }.toSet()))
            is AlarmCommand.SettingsChanged -> settings = command.settings
            is AlarmCommand.DeliveryChanged -> Unit
            is AlarmCommand.Begin -> Unit
        }
        if (!stopped && !terminal && lastValid?.let { now - it >= settings.positionLossDelayMillis } == true) {
            raise(positionKey, "No fresh valid position. Receiver recording may still be continuing.", false)
        }
        return render()
    }

    private fun raise(key: AlarmCauseKey, detail: String, fatal: Boolean) {
        if (causes.containsKey(key) || causes.size >= 32) return
        causes[key] = AlarmEpisode(AlarmEpisodeId(generation, key, ++episodeRevision), detail.take(240), fatal)
    }
    private fun remove(key: AlarmCauseKey) { causes.remove(key)?.let { acknowledged.remove(it.id) } }
    private fun render(): AlarmDesiredState {
        val receiverLost = causes.keys.any { it.category == AlarmCategory.CONNECTION_LOST }
        val eligible = causes.values.filterNot {
            it.terminal && it.id in acknowledged || receiverLost && it.id.cause.category == AlarmCategory.POSITION_LOST
        }.sortedByDescending { it.id.cause.category.priority }
        val audio = eligible.firstOrNull { it.id !in acknowledged && settings.categories.getValue(it.id.cause.category).acoustic }
        val next = AlarmDesiredState(generation, desired.revision,
            eligible.filter { settings.categories.getValue(it.id.cause.category).visual },
            audio?.let { AlarmAudioSelection(it.id, settings.categories.getValue(it.id.cause.category).sound) },
            terminal && causes.values.any { it.id !in acknowledged })
        if (next != desired) desired = next.copy(revision = desired.revision + 1)
        return desired
    }
}
