package com.amit152116.riffle.data.db.playback.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.amit152116.riffle.data.db.BrowseEntityType
import com.amit152116.riffle.data.db.SearchResultType
import com.amit152116.riffle.data.db.library.Track

// One row per completed search (on result selection or leaving SearchScreen,
// not per keystroke - the query/filter LaunchedEffect fires far more often
// than that). No Artist/Album table exists yet - routes are artist/{name},
// album/{name} - so there's no id to store for those; selectedKey holds the
// name. selectedTrackId is the only case with a real FK.
@Entity(
    foreignKeys = [ForeignKey(Track::class, ["id"], ["selectedTrackId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("selectedTrackId"), Index("timestamp")]
)
data class SearchEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val query: String,
    val resultCount: Int,
    val selectedType: SearchResultType,
    val selectedKey: String? = null,
    val selectedTrackId: Long? = null
)

// entityKey is the route argument (artist/album/genre name, or a track path)
// since there's no Artist/Album table to hold an id yet - not the doc's
// entity_id. No `action` column: every current capture site is a navigate-in,
// so it would always be OPENED; add it back only once a second action exists.
@Entity(indices = [Index("timestamp")])
data class BrowseEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val entityType: BrowseEntityType,
    val entityKey: String
)
