Here is a detailed breakdown of the functionality provided by **Jetpack Media3 / ExoPlayer** and the **Android Audio Ecosystem** that can still be implemented in **Riffle**:

---

### 1. Sound Quality & Audio Processing (DSP)

- **Volume Normalization (ReplayGain / LUFS)**: Using Android's `LoudnessEnhancer` or `AudioProcessor` API to equalize playback volume across tracks recorded at different master levels.
- **Equalizer & Audio Effects**: Integrating Android’s built-in `Equalizer`, `BassBoost`, and `Virtualizer` APIs to let users adjust frequency bands.
- **Crossfade & Smooth Fade**: Implementing custom `AudioProcessor` or volume-ramping transitions to crossfade between tracks during transitions.
- **Playback Speed & Pitch Adjustment**: Supporting variable playback speeds (e.g. `0.8x`, `1.25x`, `2.0x`) and pitch correction via ExoPlayer's `PlaybackParameters`.
- **Gapless Playback Optimization**: Trimming lead-in silence/encoder delays (MP3/AAC padding) for seamless gapless album listening.
- **Spatial Audio**: Utilizing Android 13+ `Spatializer` APIs for immersive audio output.

---

### 2. Queue & State Persistence

- **Queue Persistence (Resume on Launch)**: Saving the current queue, track, and playback position (ms) to `DataStore` or `Room` so when the app is reopened, the exact player state is restored.
- **Up Next vs. User Queue**: Separating the active queue into **User Queued Tracks** ("Play Next", "Add to Queue") and **Automated Playlist Tracks**.
- **Drag-and-Drop Queue Reordering**: Allowing users to drag, remove, or reorder tracks in the Now Playing queue UI in real-time.
- **Smart Shuffle Queue Reordering**: Dynamically ordering upcoming tracks based on BPM compatibility and acoustic key harmony (as outlined in `DESIGN2.md`).

---

### 3. System & External Integrations

- **Android Auto & Automotive OS**: Extending `MediaSessionService` to `MediaLibraryService` (`onGetLibraryRoot` / `onGetChildren`) so users can browse artists, albums, and playlists directly on their car's dashboard.
- **Custom Notification Actions**: Adding custom buttons to the system notification and lock screen controls (e.g., a **Heart/Favorite** button or **Smart Shuffle** toggle).
- **Google Assistant & Voice Commands**: Responding to voice requests like _"Play Tycho on Riffle"_ via `MediaSession` media search callbacks.
- **Chromecast / Remote Audio Streaming**: Adding `media3-cast` support to stream local music over Wi-Fi to Google Nest speakers or Chromecast devices.
- **Wear OS Companion / Tiles**: Exposing media session controls to Android smartwatches.

---

### 4. Advanced Metadata & Lyrics

- **Synced Lyrics (.LRC / SYLT)**: Parsing `.lrc` lyric files or embedded metadata to render real-time time-synced scrolling lyrics on `PlayerScreen`.
- **Embedded Metadata & Tag Extraction**: Reading custom ID3v2/FLAC Vorbis tags (including ReplayGain, BPM tags, and high-res embedded artwork) directly from local file headers using `MediaMetadataRetriever`.
- **Real-time Audio Visualizer**: Capturing PCM audio samples via ExoPlayer `AudioProcessor` or `Visualizer` API to draw animated spectrum waveforms or frequency bars.

---

### 5. Riffle-Specific Local Music Intelligence (from `DESIGN2.md`)

- **Local Audio Feature Analysis Engine**: Running lightweight DSP algorithms (or TFLite) to compute real BPM, Musical Key, Danceability, Spectral Centroid, and Energy directly on the device.
- **AcoustID Audio Fingerprinting**: Generating acoustic fingerprints from local audio files to detect duplicate or near-variant tracks regardless of filenames or missing metadata.
