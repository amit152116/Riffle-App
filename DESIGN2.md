Design a complete modern Android music-library and intelligent music-discovery app called **Riffle**.

## Product concept

Riffle is a **local-first music intelligence app** for people with large personal music libraries. Instead of being only a music player, it analyzes the user's local audio collection and understands the music using audio fingerprints, acoustic features, metadata, similarity analysis, clustering, and listening behavior.

The app helps users:

- Browse and organize a large local music collection
- Identify artists, albums, genres, and recordings
- Discover duplicate and near-duplicate recordings
- Automatically understand the audio characteristics of songs
- Group songs into meaningful audio-based collections
- Find songs that sound similar
- Generate intelligent playlists and smart-shuffle sessions
- Maintain a clean, high-quality music library
- Eventually personalize recommendations from listening history

The app should feel like a combination of a **premium music player, intelligent music library manager, and music discovery engine**.

## Core application architecture

The Android application consumes a local music-analysis backend/database containing:

### Library information

- Track
- Artist
- Album
- Release
- Genre
- Artwork
- File path
- Audio format
- Duration
- Metadata

### Audio analysis

Each song can have:

- BPM
- BPM confidence
- Musical key
- Key strength
- Loudness
- Energy
- Danceability
- Spectral centroid
- Onset rate
- Dynamic complexity
- Dissonance
- Zero-crossing rate
- MFCC representation

### Music identity

The system can associate tracks with:

- AcoustID
- MusicBrainz recording
- MusicBrainz artist
- MusicBrainz release
- MusicBrainz release group

### Audio relationships

The application knows:

- Exact duplicate files
- Same-recording duplicates
- Near variants
- Similar-sounding tracks
- Audio clusters

### Recommendation

Smart Shuffle uses:

- Audio similarity
- BPM compatibility
- Harmonic/key compatibility
- Energy progression
- Artist diversity
- Duplicate suppression
- Optional listening history

The UI should expose this intelligence without making the application feel complicated.

---

# Navigation

Use a modern Android bottom navigation bar with four primary destinations:

**Home**
**Browse**
**Shuffle**
**Library**

The Now Playing player should be accessible globally through a persistent mini-player.

Use Android Material 3 principles, but create a visually distinctive premium music-product identity rather than looking like a default Material demo.

---

# Screen 1 — Home

The Home screen is the user's personalized music dashboard.

Top area:

- Riffle logo/name
- Search icon
- Settings icon

Hero card:

- **Smart Shuffle**
- Short description such as "Play music with smooth transitions"
- Large artwork
- Play button

Quick-access cards:

- Artists
- Albums
- Genres
- Audio Clusters

Sections:

- Recently Played
- Recently Added
- Favorites
- Suggested for You
- Similar to Recently Played

Each track row should show:

- Album artwork
- Track title
- Artist
- Optional metadata
- Overflow menu

The screen should immediately communicate that Riffle understands the user's music library.

---

# Screen 2 — Browse / Collection

Create a discovery-oriented collection browser.

Top:

- Search field: "Search artists, albums, tracks..."
- Filter controls

Category tabs:

- Artists
- Albums
- Genres
- Clusters

Display genres and collections as attractive cards with artwork.

Examples:

- Electronic
- Rock
- Jazz
- Ambient
- Hip Hop
- Classical
- Experimental

Allow the user to browse by:

- Metadata
- Genre
- BPM
- Key
- Energy
- Mood
- Audio similarity
- Cluster

The Browse interface should support both traditional music-library organization and AI/audio-analysis-based discovery.

---

# Screen 3 — Artist

Artist profile screen.

Large artist header artwork.

Show:

- Artist name
- Number of albums
- Number of tracks

Actions:

- Play
- Shuffle

Sections:

- Popular tracks
- Albums
- Similar artists
- Related recordings

Albums should be presented as clean cards or compact rows.

---

# Screen 4 — Album

Album detail screen.

Header:

- Large album artwork
- Album title
- Artist
- Year
- Number of tracks

Actions:

- Play
- Shuffle

Track list:

1. Track title — duration
2. Track title — duration
3. Track title — duration

Each row should support:

- Play
- Queue
- Add to playlist
- Track information
- Similar tracks

Persistent mini-player at bottom.

---

# Screen 5 — Now Playing

Create a premium immersive Now Playing screen.

Large album artwork dominates the upper/middle portion.

Information:

- Track title
- Artist
- Album

Playback:

- Seek bar
- Current time
- Duration
- Previous
- Play/Pause
- Next

Additional controls:

- Favorite
- Queue
- Shuffle
- Repeat
- More

Add quick intelligent actions:

- Similar
- Go to Artist
- Go to Album
- Audio Features

The screen should feel elegant and focused rather than overloaded.

---

# Screen 6 — Similar Tracks

Purpose: discover music based on actual audio similarity.

Header:
"Similar Tracks"

Show a list of tracks ranked by similarity.

Each row:

- Artwork
- Track
- Artist
- Similarity score
- Overflow menu

Provide:

- Auto-play similar toggle
- Play Similar Mix button

The UI should visually communicate that these aren't merely genre matches; they are songs with similar audio characteristics.

---

# Screen 7 — Smart Shuffle Setup

Create a dedicated Smart Shuffle configuration screen.

Title:
"Smart Shuffle"

Description:
"Create a continuous playlist using your library's audio characteristics."

Shuffle modes:

- By Similarity
- By Cluster
- By Genre
- By Artist
- By Mood
- Custom

Allow configuration of:

- Starting song
- Genre
- BPM range
- Energy
- Key compatibility
- Artist repetition
- Duplicate exclusion
- Recently played exclusion

Use clear cards, chips, toggles, sliders, and bottom-sheet controls.

Primary CTA:
**Start Shuffle**

---

# Screen 8 — Shuffle Now Playing

Create a Now Playing experience specifically for Smart Shuffle.

Show:

- Current track artwork
- Track/artist
- Progress
- Playback controls

Also show why the next transition was selected.

Example:

"Smooth transition"

- Similar sound
- BPM: 118 → 121
- Key: A minor → E minor
- Energy: 0.68 → 0.72

Use small metadata chips such as:

- Similar
- BPM
- Key
- Energy

This screen should make the recommendation engine transparent.

---

# Screen 9 — Audio Clusters

Create a page showing automatically discovered music groups based on audio characteristics rather than manually assigned genres.

Example clusters:

- Chill Electronic — 234 tracks
- Deep House — 187 tracks
- Ambient — 156 tracks
- Downtempo — 142 tracks
- Melodic Techno — 98 tracks
- Experimental — 87 tracks

Use large artwork cards.

Each cluster should show:

- Cluster name
- Track count
- Representative artwork
- Optional dominant BPM
- Optional dominant energy
- Optional dominant key characteristics

Allow opening a cluster into a track collection.

---

# Screen 10 — Search + Advanced Filters

Create a powerful music-library search screen.

Search across:

- Track
- Artist
- Album
- Genre
- Metadata

Audio-analysis filters:

- BPM range
- Key
- Energy
- Loudness
- Danceability
- Cluster
- Similarity

Library filters:

- Lossless only
- Duplicate-free
- Recently added
- Favorites
- Missing metadata
- High-quality files

Include an "Exclude duplicate recordings" option.

Make the filter interface easy to use on a phone.

---

# Screen 11 — Track Audio Analysis

Create a technical but visually understandable audio-information screen.

Title:
"Audio Features"

Show:

BPM
118

Key
A minor

Key Strength
0.82

Loudness
-12.3 LUFS

Danceability
0.64

Energy
0.71

Spectral Centroid
3,421 Hz

Onset Rate
4.2 / sec

Dynamic Complexity
0.38

Dissonance
0.12

Zero Crossing Rate
0.08

MFCC
13-dimensional representation

Use cards and compact visual indicators rather than presenting raw technical data as a large table.

This screen is aimed at advanced users and should feel like a professional audio-analysis dashboard.

---

# Screen 12 — Library Health

Add a library-maintenance dashboard.

Show:

- Total tracks
- Unique recordings
- Duplicate groups
- Unidentified tracks
- Missing metadata
- Missing artwork
- Corrupt files
- Suspicious low-quality encodes
- Recently modified files

Example:

12,481 Tracks
10,932 Unique Recordings
731 Duplicate Groups
384 Metadata Issues
217 Missing Artwork

Include actions such as:

- Review duplicates
- Fix metadata
- Find missing artwork
- Review low-quality files

This should make Riffle feel like a complete music-library management system.

---

# Screen 13 — Duplicate / Recording View

Create a duplicate-review interface.

Show grouped recordings:

"Example Song"

Possible versions:

- FLAC — 24 bit
- MP3 — 320 kbps
- AAC — 256 kbps
- Radio Edit
- Remaster

For each version show evidence:

- Similarity
- Coverage
- Offset
- Mean bit error
- Recording relationship

Allow the user to choose:
**Keep**
**Review**
**Move to Quarantine**

Never show destructive deletion as the default action.

---

# Screen 14 — Settings

Sections:

### Playback

- Crossfade
- Gapless playback
- Normalize volume
- Repeat
- Shuffle behavior

### Smart Shuffle

- Avoid recent tracks
- Avoid repeated artists
- Maximum BPM jump
- Prefer harmonic transitions
- Exclude duplicate recordings

### Library

- Scan directories
- Rescan
- Metadata enrichment
- Feature analysis
- Rebuild similarity index

### Appearance

- Dark/light mode
- Accent color
- Artwork behavior

### Advanced

- Audio analysis version
- Database information
- Cache
- Diagnostics

---

# Visual design language

Create a **premium dark-first Android music application**.

Style:

- Modern
- Minimal
- Technical but approachable
- Premium
- Immersive
- Data-rich without being cluttered

Use:

- Dark charcoal/near-black backgrounds
- Subtle surface elevation
- Soft rounded cards
- Large high-quality album artwork
- Strong typography hierarchy
- Bright purple/indigo primary accent
- Subtle blue/purple gradients for hero areas
- White primary text
- Muted gray secondary text
- Minimal borders
- Soft shadows/glows
- Material 3 inspired components

Typography should be clean and modern, similar in spirit to **Inter / Roboto / SF-style UI typography**.

Use generous spacing and large touch targets suitable for Android phones.

---

# Interaction design

Important interactions:

- Persistent mini-player above bottom navigation
- Smooth shared-element transition from track → Now Playing
- Bottom sheets for queue, filters, and track actions
- Swipeable album artwork where appropriate
- Animated play/pause states
- Animated progress indicators
- Skeleton loading states
- Empty states for unscanned libraries
- Clear loading state while audio analysis is running
- Confirmation UI before moving duplicate files to quarantine
- Non-destructive workflows by default

---

# Design philosophy

The most important product principle is:

**Riffle should feel like an intelligent music library, not just a file browser.**

Traditional music apps organize music by:

Artist → Album → Song.

Riffle additionally organizes music by:

Audio identity → Audio characteristics → Similarity → Clusters → Listening context.

The interface should progressively reveal this intelligence without overwhelming normal users.

Create the Figma design as a **coherent Android design system**, not as unrelated individual screens. Use consistent components, spacing, typography, cards, buttons, bottom navigation, chips, artwork treatments, sliders, dialogs, and mini-player behavior across all screens.

Design the primary flows for a **phone-sized Android device**, with reusable components that can later adapt to tablets and larger screens.

For the Figma implementation, I would make **Home, Browse, Now Playing, Smart Shuffle, Audio Clusters, Duplicate Review, and Library Health** the seven core screens first. They communicate the unique value of Riffle much better than designing a generic Spotify-style clone.
