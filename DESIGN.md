---
name: Nocturne Audio
colors:
  surface: '#11131a'
  surface-dim: '#11131a'
  surface-bright: '#373941'
  surface-container-lowest: '#0c0e15'
  surface-container-low: '#191b22'
  surface-container: '#1d1f27'
  surface-container-high: '#282a31'
  surface-container-highest: '#33343c'
  on-surface: '#e2e2ec'
  on-surface-variant: '#c7c4d8'
  inverse-surface: '#e2e2ec'
  inverse-on-surface: '#2e3038'
  outline: '#918fa1'
  outline-variant: '#464555'
  surface-tint: '#c3c0ff'
  primary: '#c3c0ff'
  on-primary: '#1d00a5'
  primary-container: '#4f46e5'
  on-primary-container: '#dad7ff'
  inverse-primary: '#4d44e3'
  secondary: '#adc6ff'
  on-secondary: '#002e6a'
  secondary-container: '#0566d9'
  on-secondary-container: '#e6ecff'
  tertiary: '#bdc2ff'
  on-tertiary: '#131e8c'
  tertiary-container: '#4a55be'
  on-tertiary-container: '#d7d8ff'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#e2dfff'
  primary-fixed-dim: '#c3c0ff'
  on-primary-fixed: '#0f0069'
  on-primary-fixed-variant: '#3323cc'
  secondary-fixed: '#d8e2ff'
  secondary-fixed-dim: '#adc6ff'
  on-secondary-fixed: '#001a42'
  on-secondary-fixed-variant: '#004395'
  tertiary-fixed: '#e0e0ff'
  tertiary-fixed-dim: '#bdc2ff'
  on-tertiary-fixed: '#000767'
  on-tertiary-fixed-variant: '#2f3aa3'
  background: '#11131a'
  on-background: '#e2e2ec'
  surface-variant: '#33343c'
typography:
  display-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 32px
    fontWeight: '700'
    lineHeight: 40px
    letterSpacing: -0.02em
  headline-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 24px
    fontWeight: '700'
    lineHeight: 32px
    letterSpacing: -0.015em
  headline-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 20px
    fontWeight: '600'
    lineHeight: 28px
    letterSpacing: -0.01em
  title-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 18px
    fontWeight: '600'
    lineHeight: 24px
  title-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 16px
    fontWeight: '600'
    lineHeight: 22px
  body-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 15px
    fontWeight: '400'
    lineHeight: 22px
  body-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
  body-sm:
    fontFamily: Plus Jakarta Sans
    fontSize: 13px
    fontWeight: '400'
    lineHeight: 18px
  label-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 14px
    fontWeight: '600'
    lineHeight: 18px
    letterSpacing: 0.01em
  label-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
    letterSpacing: 0.02em
  label-sm:
    fontFamily: Plus Jakarta Sans
    fontSize: 11px
    fontWeight: '500'
    lineHeight: 14px
    letterSpacing: 0.04em
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  gutter: 1rem
  margin: 1.25rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 0.75rem
  space-lg: 1.25rem
  space-xl: 1.75rem
---

## Brand & Style

This design system delivers an understated, distraction-free listening environment engineered for modern Android audio applications. The aesthetic is anchored in deep matte charcoal tones, disciplined hierarchy, and purposeful high-contrast accents.

- **Brand Personality**: Sophisticated, focused, tactile, and restrained. It prioritizes the listener's relationship with cover art and sound architecture over loud interface flourishes.
- **Design Style**: Minimalist Tonal Architecture with low-contrast structural definition. Surface elevation is achieved through subtle, stepped-luminance charcoal surfaces and razor-sharp 1px boundary strokes rather than aggressive dropshadows or neon blurs.
- **Emotional Intent**: Immersive calm, tactile precision, and quiet authority. The visual weight recedes so metadata, artwork, and playback controls emerge naturally.

## Colors

The palette is rooted in absolute darkness without feeling cavernous or harsh, balancing pure blacks with graded matte slates.

### Palette Architecture
- **Canvas Base (`#0d0e12`)**: The foundational canvas layer for screens, immersive player backdrops, and navigation shells.
- **Surface Elevation 1 (`#15171e`)**: Primary card containers, list module panels, search inputs, and nested grouped controls.
- **Surface Elevation 2 (`#1c1f26`)**: Active selection states, secondary interactive buttons, popovers, and mini-player shells.
- **Stroke / Hairline Borders (`#262933`)**: Uniform boundary rule defining card edges, dividers, and pill borders at low opacity (typically 80% to 100% token intensity).
- **Primary Indigo Accent (`#4f46e5`)**: Used for primary action pills, master "Play" buttons, active scrubber track progress, and key toggles.
- **Secondary Bright Blue Accent (`#3b82f6`)**: Interactive selection highlights, focus indicators, and subtle gradient shifts on hero cards.
- **Content Hierarchy**:
  - **Primary Text (`#ffffff`)**: 100% white for track titles, prominent metrics, and active states.
  - **Secondary Text (`#9ca3af`)**: Neutral 60% cool silver for artists, album years, metadata tags, and disabled labels.
  - **Tertiary / Inactive (`#64748b`)**: Subtle timestamps, inactive bottom-bar icons, and unselected filter tags.

## Typography

The type stack is standardized on **Plus Jakarta Sans** across all levels. Its balanced geometric construction and tall x-height deliver immediate legibility in high-density data views, track tables, and dynamic playback timers.

### Hierarchy Guidelines
- **Headlines & Display**: Crisp, high-contrast, semi-bold to bold weights. Headlines utilize negative letter spacing to feel tight and architectural on mobile displays.
- **Track & Item Labels**: `title-md` and `body-md` are paired together throughout list views—white text for the primary item name, stacked over neutral gray metadata.
- **Technical & Metric Indicators**: Audio metrics (LUFS, BPM, sample rate, duration) use `label-md` or `label-sm` with tabular numerical alignment to prevent jitter during real-time updates.

## Layout & Spacing

Layout geometry follows an explicit, fluid 4-column structure on mobile devices, expanding to 8 columns on foldables/tablets and 12 columns in expanded horizontal dashboard modes.

### Structural Parameters
- **Screen Margins**: Uniform `1.25rem` (20px) horizontal padding provides clean clearance on mobile viewports while preserving maximum real estate for artwork cards.
- **Inter-Item Gutters**: `1rem` (16px) between multi-column genre and cluster cards.
- **Vertical Rhythm**: Content blocks (e.g., Recently Played, Browse Categories, Cluster Carousels) are separated by `space-xl` (28px). Internal card padding standardizes on `space-md` (12px) to `space-lg` (20px).
- **Navigation Insets**: Bottom sheets and floating navigation bars leave a persistent 64px dock area plus device-specific safe-area insets.

## Elevation & Depth

Visual separation relies exclusively on layered luminance and precise hairline strokes rather than diffuse, colored drop-shadows.

### Layering Principles
- **Tier 0 (Base Canvas)**: `#0d0e12`. The background canvas for main views and screen roots.
- **Tier 1 (Surface Cards & Lists)**: `#15171e`. Elevated components framed with a 1px border stroke of `#262933`.
- **Tier 2 (Interactive Floating Modules & Modals)**: `#1c1f26`. Applied to bottom player drawers, action menus, and segmented control backgrounds, outlined with `#262933`.
- **Accents & Focus**: Active components (like the now-playing scrubber head or primary play pill) utilize an inner micro-highlight (0.5px solid rgba(255, 255, 255, 0.15)) to provide tactile crispness without blur.

## Shapes

The design system adopts a refined, rounded geometric identity that softens the dark aesthetic while retaining modular structure.

### Radius System
- **Pills / Dynamic Floating UI (`rounded-full`)**: Used strictly for primary action buttons (e.g., "Play", "Apply Filters"), segmented switch pills, and filter chips.
- **Content Cards & Containers (`rounded-xl` / 1.5rem / 24px)**: Applied to album artwork showcases, audio cluster cards, and hero feature cards (e.g., Smart Shuffle).
- **Standard Surfaces & Nested Tiles (`rounded-lg` / 1rem / 16px)**: Applied to grid cards, category tiles, dialogs, and bottom mini-player containers.
- **Micro Elements (`rounded-md` / 0.5rem / 8px)**: Applied to inline album thumbs, checkboxes, metadata tags, and track progress track thumbs.

## Components

### Buttons & Pills
- **Primary Action Pill**: Solid `#4f46e5` fill with pure white typography, `rounded-full` contour, and `12px 24px` padding. Icon and text are centered with `8px` spacing.
- **Secondary Action Pill**: `#1c1f26` background, 1px `#262933` border stroke, white text, and `rounded-full` contour.
- **Transport Controls**: Primary play/pause button is a circle (`56px` to `64px`) in pure white or indigo with high-contrast glyphs. Secondary transport controls (skip, previous, shuffle, repeat) use minimal transparent backgrounds with `#9ca3af` glyphs transitioning to `#ffffff` when active.

### Chips & Filter Tags
- **Filter Chips**: Compact height (`34px`), `rounded-full` structure, `#15171e` background with 1px `#262933` stroke.
- **Active State**: Solid `#4f46e5` fill with `#ffffff` text, or subtle indigo border highlight with white label text.

### Track Lists & Media Rows
- **Track Row**: Height `56px` to `64px`. Contains track index or leading artwork thumbnail (`44px` with `8px` radius), vertical title/artist stack, optional trailing duration text (`#9ca3af`), and an icon button (`24px`) for the context overflow menu.
- **Active / Playing State**: Title text switches to `#818cf8` or `#ffffff` accompanied by an inline equalizer micro-indicator.

### Inputs & Sliders
- **Search Fields**: Fully pill-shaped or `rounded-xl` container with `#15171e` surface fill, `#262933` border, leading search icon in `#9ca3af`, and light placeholder text.
- **Audio Progress Scrubber**: Track height `4px` in `#262933`, active fill in `#4f46e5`, and a subtle `12px` circular thumb indicator with zero drop shadow.

### Cards & Clusters
- **Audio Cluster & Genre Cards**: `16px` to `20px` corner radius, displaying rich atmospheric imagery overlaid with a bottom-to-top gradient (`rgba(13, 14, 18, 0.85)` to `transparent`) ensuring high contrast for white titles and gray track count tags.

### Mini Player & Bottom Navigation
- **Docked Mini Player**: Anchored directly above the navigation bar, rendered in `#1c1f26` with a top 1px `#262933` border, `12px` padding, inline album art, scrolling track metadata, and simplified play/skip actions.
- **Bottom Navigation**: Persistent `#0d0e12` surface, 4 to 5 icons with label text in `label-sm`. Active item highlighted in `#3b82f6` or `#ffffff`, inactive items in `#64748b`.