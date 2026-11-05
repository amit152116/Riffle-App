package com.amit152116.riffle.data.db

import androidx.room.TypeConverter
import com.amit152116.riffle.intelligence.model.ClusterMode
import com.amit152116.riffle.intelligence.model.EnergyBand
import com.amit152116.riffle.intelligence.model.TempoBand
import com.amit152116.riffle.intelligence.model.TimbreBand

enum class HashMethod { STREAMHASH, WHOLE_FILE }
enum class FingerprintPurpose { CANONICAL, ACOUSTID_LOOKUP }
enum class RunStatus { RUNNING, COMPLETE, FAILED }
enum class AbsentReason { MISSING, QUARANTINED }
enum class DupDecision { PROPOSED, APPROVED, REJECTED, APPLIED }
enum class QuarantineState { MOVED, FAILED, UNDONE }

// Stored as TEXT (@TypeConverter below), never ordinal - these back append-only
// event logs, so renaming or removing a constant breaks valueOf() on old rows.
// Only ever add new constants.
// EXTERNAL: opened with no source extra on the MediaItem at all - a
// controller outside our own UI (Assistant, another app's MediaController)
// set it directly, so there's no in-app screen to attribute the play to.
// Never used as an explicit default anywhere in-app; MANUAL covers every
// direct in-app tap that isn't ALBUM/ARTIST/PLAYLIST/SEARCH/QUEUE.
enum class PlaybackSource { MANUAL, ALBUM, ARTIST, PLAYLIST, QUEUE, SHUFFLE, SMART_SHUFFLE, AUTOPLAY, SEARCH, RECOMMENDATION, EXTERNAL }

// Bundle key PlayerViewModel.toMediaItem() stamps a PlaybackSource.name into
// MediaMetadata.extras, and PlaybackService.openEvent() reads back - the only
// way the service (which sees just the MediaItem) learns which screen
// started a play. Shared here since both sides need the exact same key.
const val PLAYBACK_SOURCE_EXTRA_KEY = "com.amit152116.riffle.PLAYBACK_SOURCE"
enum class PlaybackEndReason { TRACK_ENDED, USER_SKIPPED, FOCUS_LOST, BECOMING_NOISY, ERROR, UNCLOSED, UNKNOWN }
enum class PlaybackActionType { PAUSE, RESUME, SEEK }

// Mirrors Player.PLAY_WHEN_READY_CHANGE_REASON_* / PLAYBACK_SUPPRESSION_REASON_* -
// what Media3 can actually report, not the doc's phone-call/navigation guesses.
enum class PlaybackActionReason {
    USER, AUDIO_FOCUS_TRANSIENT, AUDIO_FOCUS_DUCK, AUDIO_FOCUS_PERMANENT,
    BECOMING_NOISY, REMOTE, END_OF_ITEM, SUPPRESSED, UNKNOWN
}

enum class InteractionType { FAVORITED, UNFAVORITED, ADDED_TO_PLAYLIST, REMOVED_FROM_PLAYLIST, HIDDEN, UNHIDDEN }
enum class QueueAction { ADD, REMOVE, MOVE }
enum class BrowseEntityType { ARTIST, ALBUM, GENRE, CLUSTER, TRACK, HOME_SECTION }
enum class SearchResultType { TRACK, ARTIST, ALBUM, NONE }
enum class ShuffleMode { TRUE_RANDOM, SIMILARITY, CLUSTER, GENRE, ARTIST }
enum class RecommendationSurface { HOME_RAIL, SIMILAR_TRACKS, CLUSTER_TAB }
enum class RecommendationActionType { CLICKED, DISMISSED, QUEUED }

// A PlaybackEvent/QueueEvent can originate from a ShuffleDecision or a
// RecommendationImpression, never both - one nullable (kind, id) pair instead
// of two mutually-exclusive nullable FK columns. Not a real FK (Room can't
// target either-of-two tables); resolve the join in app code.
enum class OriginKind { SHUFFLE_DECISION, RECOMMENDATION_IMPRESSION }

class Converters {
    @TypeConverter
    fun fromHashMethod(value: HashMethod?): String? = value?.name

    @TypeConverter
    fun toHashMethod(value: String?): HashMethod? = value?.let { HashMethod.valueOf(it) }

    @TypeConverter
    fun fromFingerprintPurpose(value: FingerprintPurpose?): String? = value?.name

    @TypeConverter
    fun toFingerprintPurpose(value: String?): FingerprintPurpose? =
        value?.let { FingerprintPurpose.valueOf(it) }

    @TypeConverter
    fun fromRunStatus(value: RunStatus?): String? = value?.name

    @TypeConverter
    fun toRunStatus(value: String?): RunStatus? = value?.let { RunStatus.valueOf(it) }

    @TypeConverter
    fun fromAbsentReason(value: AbsentReason?): String? = value?.name

    @TypeConverter
    fun toAbsentReason(value: String?): AbsentReason? = value?.let { AbsentReason.valueOf(it) }

    @TypeConverter
    fun fromDupDecision(value: DupDecision?): String? = value?.name

    @TypeConverter
    fun toDupDecision(value: String?): DupDecision? = value?.let { DupDecision.valueOf(it) }

    @TypeConverter
    fun fromQuarantineState(value: QuarantineState?): String? = value?.name

    @TypeConverter
    fun toQuarantineState(value: String?): QuarantineState? =
        value?.let { QuarantineState.valueOf(it) }

    @TypeConverter
    fun fromPlaybackSource(value: PlaybackSource?): String? = value?.name

    @TypeConverter
    fun toPlaybackSource(value: String?): PlaybackSource? = value?.let { PlaybackSource.valueOf(it) }

    @TypeConverter
    fun fromPlaybackEndReason(value: PlaybackEndReason?): String? = value?.name

    @TypeConverter
    fun toPlaybackEndReason(value: String?): PlaybackEndReason? = value?.let { PlaybackEndReason.valueOf(it) }

    @TypeConverter
    fun fromPlaybackActionType(value: PlaybackActionType?): String? = value?.name

    @TypeConverter
    fun toPlaybackActionType(value: String?): PlaybackActionType? = value?.let { PlaybackActionType.valueOf(it) }

    @TypeConverter
    fun fromPlaybackActionReason(value: PlaybackActionReason?): String? = value?.name

    @TypeConverter
    fun toPlaybackActionReason(value: String?): PlaybackActionReason? =
        value?.let { PlaybackActionReason.valueOf(it) }

    @TypeConverter
    fun fromInteractionType(value: InteractionType?): String? = value?.name

    @TypeConverter
    fun toInteractionType(value: String?): InteractionType? = value?.let { InteractionType.valueOf(it) }

    @TypeConverter
    fun fromQueueAction(value: QueueAction?): String? = value?.name

    @TypeConverter
    fun toQueueAction(value: String?): QueueAction? = value?.let { QueueAction.valueOf(it) }

    @TypeConverter
    fun fromBrowseEntityType(value: BrowseEntityType?): String? = value?.name

    @TypeConverter
    fun toBrowseEntityType(value: String?): BrowseEntityType? = value?.let { BrowseEntityType.valueOf(it) }

    @TypeConverter
    fun fromSearchResultType(value: SearchResultType?): String? = value?.name

    @TypeConverter
    fun toSearchResultType(value: String?): SearchResultType? = value?.let { SearchResultType.valueOf(it) }

    @TypeConverter
    fun fromShuffleMode(value: ShuffleMode?): String? = value?.name

    @TypeConverter
    fun toShuffleMode(value: String?): ShuffleMode? = value?.let { ShuffleMode.valueOf(it) }

    @TypeConverter
    fun fromRecommendationSurface(value: RecommendationSurface?): String? = value?.name

    @TypeConverter
    fun toRecommendationSurface(value: String?): RecommendationSurface? =
        value?.let { RecommendationSurface.valueOf(it) }

    @TypeConverter
    fun fromRecommendationActionType(value: RecommendationActionType?): String? = value?.name

    @TypeConverter
    fun toRecommendationActionType(value: String?): RecommendationActionType? =
        value?.let { RecommendationActionType.valueOf(it) }

    @TypeConverter
    fun fromOriginKind(value: OriginKind?): String? = value?.name

    @TypeConverter
    fun toOriginKind(value: String?): OriginKind? = value?.let { OriginKind.valueOf(it) }

    @TypeConverter
    fun fromTempoBand(value: TempoBand?): String? = value?.name

    @TypeConverter
    fun toTempoBand(value: String?): TempoBand? = value?.let { TempoBand.valueOf(it) }

    @TypeConverter
    fun fromEnergyBand(value: EnergyBand?): String? = value?.name

    @TypeConverter
    fun toEnergyBand(value: String?): EnergyBand? = value?.let { EnergyBand.valueOf(it) }

    @TypeConverter
    fun fromClusterMode(value: ClusterMode?): String? = value?.name

    @TypeConverter
    fun toClusterMode(value: String?): ClusterMode? = value?.let { ClusterMode.valueOf(it) }

    @TypeConverter
    fun fromTimbreBand(value: TimbreBand?): String? = value?.name

    @TypeConverter
    fun toTimbreBand(value: String?): TimbreBand? = value?.let { TimbreBand.valueOf(it) }
}
