package com.amit152116.riffle.data.model

enum class SongSortOrder(val label: String) {
    TITLE_A_Z("Title (A-Z)"),
    TITLE_Z_A("Title (Z-A)"),
    ARTIST("Artist"),
    ALBUM("Album"),
    DURATION_DESC("Duration (Longest first)"),
    DURATION_ASC("Duration (Shortest first)"),
    DATE_ADDED_DESC("Date Added (Newest)"),
    DATE_ADDED_ASC("Date Added (Oldest)")
}

enum class AlbumSortOrder(val label: String) {
    ALBUM_A_Z("Album (A-Z)"),
    ALBUM_Z_A("Album (Z-A)"),
    ARTIST("Artist"),
    NUM_SONGS_DESC("Number of Songs (Most)")
}

enum class ArtistSortOrder(val label: String) {
    ARTIST_A_Z("Artist (A-Z)"),
    ARTIST_Z_A("Artist (Z-A)"),
    NUM_SONGS_DESC("Number of Songs (Most)")
}

enum class FolderSortOrder(val label: String) {
    FOLDER_A_Z("Folder (A-Z)"),
    FOLDER_Z_A("Folder (Z-A)"),
    NUM_SONGS_DESC("Number of Tracks (Most)")
}

enum class AlbumSongSortOrder(val label: String) {
    TITLE_A_Z("Title (A-Z)"),
    TITLE_Z_A("Title (Z-A)"),
    DURATION_DESC("Duration")
}
