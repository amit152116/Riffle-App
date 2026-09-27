package com.amit152116.riffle.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.amit152116.riffle.data.local.analysis.AnalysisDao
import com.amit152116.riffle.data.local.analysis.AudioFeatures
import com.amit152116.riffle.data.local.analysis.QualityFlag
import com.amit152116.riffle.data.local.dedup.DedupDao
import com.amit152116.riffle.data.local.dedup.DupGroup
import com.amit152116.riffle.data.local.dedup.GroupContent
import com.amit152116.riffle.data.local.dedup.GroupMember
import com.amit152116.riffle.data.local.dedup.QuarantineLog
import com.amit152116.riffle.data.local.fingerprint.AudioPair
import com.amit152116.riffle.data.local.fingerprint.Fingerprint
import com.amit152116.riffle.data.local.fingerprint.FingerprintDao
import com.amit152116.riffle.data.local.fingerprint.MatchRun
import com.amit152116.riffle.data.local.fingerprint.RunTrack
import com.amit152116.riffle.data.local.library.AudioContent
import com.amit152116.riffle.data.local.library.IngestError
import com.amit152116.riffle.data.local.library.LibraryDao
import com.amit152116.riffle.data.local.library.ScanRun
import com.amit152116.riffle.data.local.library.Track

@Database(
    entities = [
        AudioContent::class,
        ScanRun::class,
        Track::class,
        IngestError::class,
        Fingerprint::class,
        MatchRun::class,
        RunTrack::class,
        AudioPair::class,
        DupGroup::class,
        GroupContent::class,
        GroupMember::class,
        QuarantineLog::class,
        AudioFeatures::class,
        QualityFlag::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class RiffleDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao
    abstract fun fingerprintDao(): FingerprintDao
    abstract fun dedupDao(): DedupDao
    abstract fun analysisDao(): AnalysisDao

    companion object {
        @Volatile private var instance: RiffleDatabase? = null

        fun getInstance(context: Context): RiffleDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    RiffleDatabase::class.java,
                    "riffle.db"
                ).build().also { instance = it }
            }
    }
}
