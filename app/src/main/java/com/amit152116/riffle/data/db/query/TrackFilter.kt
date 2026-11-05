package com.amit152116.riffle.data.db.query

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.RawQuery
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.amit152116.riffle.data.db.cluster.ClusterDao
import com.amit152116.riffle.data.db.library.Track
import com.amit152116.riffle.intelligence.model.EligibilityConfig

enum class TrackSort { ARTIST, TITLE, BPM, KEY, LOUDNESS, ENERGY, DANCEABILITY, GENRE, CLUSTER }
enum class SortDirection { ASC, DESC }

/**
 * Shared by Browse, playlist generation and shuffle. Operates on ELIGIBLE recordings, one row per
 * recording (its lowest-id present, non-blacklisted Track). `bpmMin` is inclusive, `bpmMax`
 * exclusive. Tag predicates (`genre`) and tag sorts use that representative Track's tags.
 */
data class TrackFilter(
    val genre: String? = null,
    val bpmMin: Double? = null,
    val bpmMax: Double? = null,
    val keyName: String? = null,
    val scale: String? = null,
    val energyMin: Double? = null,
    val energyMax: Double? = null,
    val clusterId: Int? = null,
    val audioContentIds: Set<Long>? = null,
    val sort: TrackSort = TrackSort.ARTIST,
    val direction: SortDirection = SortDirection.ASC,
    val limit: Int? = null,
    val offset: Int = 0
)

class FilteredTrack(
    @Embedded val track: Track,
    @ColumnInfo(name = "f_bpm") val bpm: Double?,
    @ColumnInfo(name = "f_keyName") val keyName: String?,
    @ColumnInfo(name = "f_scale") val scale: String?,
    @ColumnInfo(name = "f_energy") val energy: Double?,
    @ColumnInfo(name = "f_danceability") val danceability: Double?,
    @ColumnInfo(name = "f_loudnessLufs") val loudnessLufs: Double?,
    @ColumnInfo(name = "f_clusterId") val clusterId: Int?
)

class BuiltQuery(val sql: String, val args: List<Any?>)

object TrackFilterSql {
    private const val MAX_IDS = 500
    private const val BLACKLIST = "(SELECT trackId FROM BlacklistedTrack)"

    /**
     * Only values from the fixed whitelist below are ever spliced into the SQL text; every value
     * that originates from a caller (genre, key, numbers, ids, limit/offset) is a bound argument.
     */
    fun build(
        filter: TrackFilter,
        latestRunId: Long?,
        eligibility: EligibilityConfig = EligibilityConfig()
    ): BuiltQuery {
        require(filter.offset >= 0) { "offset must be >= 0" }
        filter.limit?.let { require(it >= 0) { "limit must be >= 0" } }
        val args = ArrayList<Any?>()
        val sb = StringBuilder()
        sb.append("SELECT t.*, af.bpm AS f_bpm, af.keyName AS f_keyName, af.scale AS f_scale, ")
            .append("af.energy AS f_energy, af.danceability AS f_danceability, ")
            .append("af.loudnessLufs AS f_loudnessLufs, ca.clusterId AS f_clusterId ")
            .append("FROM Track t ")
            .append("JOIN AudioFeatures af ON af.audioContentId = t.audioContentId ")
            .append("JOIN AudioContent ac ON ac.id = t.audioContentId ")
            .append("LEFT JOIN ClusterAssignment ca ON ca.audioContentId = t.audioContentId AND ca.runId = ? ")
        args.add(latestRunId ?: -1L)
        args.add(eligibility.minDurationSeconds)
        args.add(eligibility.silenceLufsFloor)
        sb.append("WHERE t.present = 1 AND t.id NOT IN $BLACKLIST ")
            .append("AND af.mfccMean IS NOT NULL AND af.energy IS NOT NULL ")
            .append("AND (ac.duration IS NULL OR ac.duration >= ?) ")
            .append("AND (af.loudnessLufs IS NULL OR af.loudnessLufs > ?) ")
            .append("AND t.id = (SELECT MIN(t2.id) FROM Track t2 WHERE t2.audioContentId = t.audioContentId ")
            .append("AND t2.present = 1 AND t2.id NOT IN $BLACKLIST) ")

        filter.genre?.let {
            sb.append("AND t.tagGenre LIKE ? ESCAPE '\\' ")
            args.add("%" + escapeLike(it) + "%")
        }
        filter.bpmMin?.let { sb.append("AND af.bpm >= ? "); args.add(it) }
        filter.bpmMax?.let { sb.append("AND af.bpm < ? "); args.add(it) }
        filter.keyName?.let { sb.append("AND af.keyName = ? "); args.add(it) }
        filter.scale?.let { sb.append("AND af.scale = ? "); args.add(it) }
        filter.energyMin?.let { sb.append("AND af.energy >= ? "); args.add(it) }
        filter.energyMax?.let { sb.append("AND af.energy <= ? "); args.add(it) }
        filter.clusterId?.let {
            if (latestRunId == null) sb.append("AND 0 ")
            else {
                sb.append("AND ca.clusterId = ? ")
                args.add(it.toLong())
            }
        }
        filter.audioContentIds?.let { ids ->
            require(ids.size <= MAX_IDS) { "at most $MAX_IDS audioContentIds per query" }
            if (ids.isEmpty()) sb.append("AND 0 ")
            else {
                sb.append("AND t.audioContentId IN (").append(ids.joinToString(",") { "?" }).append(") ")
                ids.forEach { args.add(it) }
            }
        }

        val (expr, invalid) = when (filter.sort) {
            TrackSort.ARTIST -> "t.tagArtist COLLATE NOCASE" to "t.tagArtist IS NULL OR t.tagArtist = ''"
            TrackSort.TITLE -> "t.tagTitle COLLATE NOCASE" to "t.tagTitle IS NULL OR t.tagTitle = ''"
            TrackSort.BPM -> "af.bpm" to "af.bpm IS NULL OR af.bpm <= 0"
            TrackSort.KEY -> "af.keyName" to "af.keyName IS NULL OR af.keyName = ''"
            TrackSort.LOUDNESS -> "af.loudnessLufs" to "af.loudnessLufs IS NULL"
            TrackSort.ENERGY -> "af.energy" to "af.energy IS NULL"
            TrackSort.DANCEABILITY -> "af.danceability" to "af.danceability IS NULL"
            TrackSort.GENRE -> "t.tagGenre COLLATE NOCASE" to "t.tagGenre IS NULL OR t.tagGenre = ''"
            TrackSort.CLUSTER -> "ca.clusterId" to "ca.clusterId IS NULL"
        }
        sb.append("ORDER BY CASE WHEN ($invalid) THEN 1 ELSE 0 END, $expr ${filter.direction.name}, t.id ASC ")
        sb.append("LIMIT ? OFFSET ?")
        args.add((filter.limit ?: -1).toLong())
        args.add(filter.offset.toLong())
        return BuiltQuery(sb.toString(), args)
    }

    private fun escapeLike(text: String): String =
        text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}

@Dao
interface TrackFilterDao {
    @RawQuery
    suspend fun query(query: SupportSQLiteQuery): List<FilteredTrack>
}

class TrackFilterRepository(
    private val dao: TrackFilterDao,
    private val clusterDao: ClusterDao,
    private val eligibility: EligibilityConfig = EligibilityConfig()
) {
    suspend fun query(filter: TrackFilter): List<FilteredTrack> {
        val built = TrackFilterSql.build(filter, clusterDao.getLatestRun()?.id, eligibility)
        return dao.query(SimpleSQLiteQuery(built.sql, built.args.toTypedArray()))
    }
}
