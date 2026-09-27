package com.amit152116.riffle.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.amit152116.riffle.data.db.analysis.AnalysisDao
import com.amit152116.riffle.data.db.cluster.ClusterAssignment
import com.amit152116.riffle.data.db.cluster.ClusterCentroid
import com.amit152116.riffle.data.db.cluster.ClusterDao
import com.amit152116.riffle.data.db.cluster.ClusterRun
import com.amit152116.riffle.data.db.cluster.TrackSimilarity
import com.amit152116.riffle.data.db.analysis.AudioFeatures
import com.amit152116.riffle.data.db.analysis.QualityFlag
import com.amit152116.riffle.data.db.dedup.DedupDao
import com.amit152116.riffle.data.db.dedup.DupGroup
import com.amit152116.riffle.data.db.dedup.GroupContent
import com.amit152116.riffle.data.db.dedup.GroupMember
import com.amit152116.riffle.data.db.dedup.QuarantineLog
import com.amit152116.riffle.data.db.fingerprint.AudioPair
import com.amit152116.riffle.data.db.fingerprint.Fingerprint
import com.amit152116.riffle.data.db.fingerprint.FingerprintDao
import com.amit152116.riffle.data.db.fingerprint.MatchRun
import com.amit152116.riffle.data.db.fingerprint.RunTrack
import com.amit152116.riffle.data.db.library.AudioContent
import com.amit152116.riffle.data.db.library.IngestError
import com.amit152116.riffle.data.db.library.LibraryDao
import com.amit152116.riffle.data.db.library.ScanRun
import com.amit152116.riffle.data.db.library.Track
import com.amit152116.riffle.data.db.musicbrainz.AcoustidCache
import com.amit152116.riffle.data.db.musicbrainz.AcoustidCacheContent
import com.amit152116.riffle.data.db.musicbrainz.MbArtist
import com.amit152116.riffle.data.db.musicbrainz.MbRecordingArtist
import com.amit152116.riffle.data.db.musicbrainz.MusicBrainzDao
import com.amit152116.riffle.data.db.musicbrainz.MusicBrainzMatch
import com.amit152116.riffle.data.db.playback.BlacklistedTrack
import com.amit152116.riffle.data.db.playback.FavoriteTrack
import com.amit152116.riffle.data.db.playback.PlayHistory
import com.amit152116.riffle.data.db.playback.Playlist
import com.amit152116.riffle.data.db.playback.PlaybackDao
import com.amit152116.riffle.data.db.playback.PlaylistTrack

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
        QualityFlag::class,
        MusicBrainzMatch::class,
        MbArtist::class,
        MbRecordingArtist::class,
        AcoustidCache::class,
        AcoustidCacheContent::class,
        ClusterRun::class,
        ClusterCentroid::class,
        ClusterAssignment::class,
        TrackSimilarity::class,
        PlayHistory::class,
        FavoriteTrack::class,
        BlacklistedTrack::class,
        Playlist::class,
        PlaylistTrack::class
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
    abstract fun musicBrainzDao(): MusicBrainzDao
    abstract fun clusterDao(): ClusterDao
    abstract fun playbackDao(): PlaybackDao

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
