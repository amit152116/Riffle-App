package com.amit152116.riffle.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.amit152116.riffle.data.db.analysis.AnalysisDao
import com.amit152116.riffle.data.db.cluster.ClusterAssignment
import com.amit152116.riffle.data.db.cluster.ClusterCentroid
import com.amit152116.riffle.data.db.cluster.ClusterDao
import com.amit152116.riffle.data.db.cluster.ClusterRun
import com.amit152116.riffle.data.db.cluster.ContentSimilarity
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
import com.amit152116.riffle.data.db.playback.entities.BlacklistedTrack
import com.amit152116.riffle.data.db.playback.entities.BrowseEvent
import com.amit152116.riffle.data.db.playback.entities.FavoriteTrack
import com.amit152116.riffle.data.db.playback.entities.PlaybackAction
import com.amit152116.riffle.data.db.playback.PlaybackDao
import com.amit152116.riffle.data.db.playback.entities.PlaybackEvent
import com.amit152116.riffle.data.db.playback.entities.Playlist
import com.amit152116.riffle.data.db.playback.entities.PlaylistTrack
import com.amit152116.riffle.data.db.playback.entities.QueueEvent
import com.amit152116.riffle.data.db.playback.entities.RecommendationAction
import com.amit152116.riffle.data.db.playback.entities.RecommendationImpression
import com.amit152116.riffle.data.db.playback.entities.RecommendationSlate
import com.amit152116.riffle.data.db.playback.entities.SearchEvent
import com.amit152116.riffle.data.db.playback.entities.ShuffleDecision
import com.amit152116.riffle.data.db.playback.entities.ShuffleSession
import com.amit152116.riffle.data.db.playback.TelemetryDao
import com.amit152116.riffle.data.db.query.TrackFilterDao
import com.amit152116.riffle.data.db.playback.entities.TrackInteraction

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
        ContentSimilarity::class,
        PlaybackEvent::class,
        PlaybackAction::class,
        TrackInteraction::class,
        FavoriteTrack::class,
        BlacklistedTrack::class,
        Playlist::class,
        PlaylistTrack::class,
        QueueEvent::class,
        SearchEvent::class,
        BrowseEvent::class,
        ShuffleSession::class,
        ShuffleDecision::class,
        RecommendationSlate::class,
        RecommendationImpression::class,
        RecommendationAction::class
    ],
    version = 4,
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
    abstract fun telemetryDao(): TelemetryDao
    abstract fun trackFilterDao(): TrackFilterDao

    companion object {
        @Volatile private var instance: RiffleDatabase? = null

        // Adds Track.dateAdded (MediaStore's real DATE_ADDED, epoch seconds) - see the
        // column's kdoc in LibraryEntities.kt for why this replaced ordering "last added"
        // by Room's own autoincrement id. Existing rows get NULL until their next rescan
        // backfills it (LibraryScanner.syncTrackMetadata runs on every launch).
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Track ADD COLUMN dateAdded INTEGER")
            }
        }

        // Adds Track.displayName/relativePath (MediaStore's DISPLAY_NAME/RELATIVE_PATH) -
        // see their kdoc in LibraryEntities.kt: `path` is a content:// Uri whose last segment
        // is only MediaStore's numeric _ID, not a real file name or folder location.
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Track ADD COLUMN displayName TEXT")
                db.execSQL("ALTER TABLE Track ADD COLUMN relativePath TEXT")
            }
        }

        // Similarity moves from per-Track (TrackSimilarity, never populated) to per-recording
        // (ContentSimilarity), and the cluster tables gain run metadata and structured labels.
        // TrackSimilarity has no production readers/writers, so dropping it loses nothing.
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `TrackSimilarity`")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `ContentSimilarity` (" +
                        "`audioContentId` INTEGER NOT NULL, `neighborContentId` INTEGER NOT NULL, " +
                        "`mfccDistance` REAL NOT NULL, `bpmDistance` REAL, `keyDistance` REAL, " +
                        "`energyDistance` REAL, `combinedScore` REAL NOT NULL, " +
                        "PRIMARY KEY(`audioContentId`, `neighborContentId`), " +
                        "FOREIGN KEY(`audioContentId`) REFERENCES `AudioContent`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE NO ACTION , " +
                        "FOREIGN KEY(`neighborContentId`) REFERENCES `AudioContent`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE NO ACTION )"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_ContentSimilarity_audioContentId_combinedScore` " +
                        "ON `ContentSimilarity` (`audioContentId`, `combinedScore`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_ContentSimilarity_neighborContentId` " +
                        "ON `ContentSimilarity` (`neighborContentId`)"
                )
                db.execSQL("ALTER TABLE `ClusterRun` ADD COLUMN `configHash` TEXT")
                db.execSQL("ALTER TABLE `ClusterRun` ADD COLUMN `inputHash` TEXT")
                db.execSQL("ALTER TABLE `ClusterRun` ADD COLUMN `silhouette` REAL")
                db.execSQL("ALTER TABLE `ClusterRun` ADD COLUMN `kScores` TEXT")
                db.execSQL("ALTER TABLE `ClusterRun` ADD COLUMN `durationMs` INTEGER")
                db.execSQL("ALTER TABLE `ClusterRun` ADD COLUMN `nextClusterId` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `ClusterCentroid` ADD COLUMN `tempoBand` TEXT")
                db.execSQL("ALTER TABLE `ClusterCentroid` ADD COLUMN `energyBand` TEXT")
                db.execSQL("ALTER TABLE `ClusterCentroid` ADD COLUMN `mode` TEXT")
                db.execSQL("ALTER TABLE `ClusterCentroid` ADD COLUMN `timbre` TEXT")
                db.execSQL("ALTER TABLE `ClusterCentroid` ADD COLUMN `matchDistance` REAL")
            }
        }

        fun getInstance(context: Context): RiffleDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    RiffleDatabase::class.java,
                    "riffle.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build().also { instance = it }
            }
    }
}
