package com.amit152116.riffle.data.db

import androidx.room.TypeConverter

enum class HashMethod { STREAMHASH, WHOLE_FILE }
enum class FingerprintPurpose { CANONICAL, ACOUSTID_LOOKUP }
enum class RunStatus { RUNNING, COMPLETE, FAILED }
enum class AbsentReason { MISSING, QUARANTINED }
enum class DupDecision { PROPOSED, APPROVED, REJECTED, APPLIED }
enum class QuarantineState { MOVED, FAILED, UNDONE }

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
}
