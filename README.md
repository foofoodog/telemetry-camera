# Telemetry Camera

> **A tactical field camera that records and overlays real-time aerospace telemetry, GPS, and orientation onto every photo.**

[![GitHub Release](https://img.shields.io/github/v/release/foofoodog/telemetry-camera?style=flat-square&color=00e5ff)](https://github.com/foofoodog/telemetry-camera/releases/latest)
[![Google Play Beta](https://img.shields.io/badge/Google_Play-Closed_Beta-414141?style=flat-square&logo=google-play&logoColor=white)](https://play.google.com/apps/testing/com.codewarren.telemetry.camera)
[![Platform](https://img.shields.io/badge/platform-Android-3DDC84.svg?style=flat-square)](https://www.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF.svg?style=flat-square)](https://kotlinlang.org/)
[![License](https://img.shields.io/badge/license-MIT-blue.svg?style=flat-square)](LICENSE)

A rugged, native Android camera application built with **Kotlin**, **Jetpack Compose**, and **CameraX** engineered for fieldwork, surveying, search and rescue, outdoor navigation, and technical photography. It captures, renders, and persists rich real-time physical, environmental, and spatial telemetry directly alongside high-resolution photos.

[📥 **Download Latest Signed APK**](https://github.com/foofoodog/telemetry-camera/releases/latest) • [🧪 **Join Closed Beta**](https://play.google.com/apps/testing/com.codewarren.telemetry.camera) • [📖 **Visual User Guide**](docs/userguide.md)

---

### 🧪 Join the Google Play Closed Beta

We are actively seeking field testers! You can join the testing track and install Telemetry Camera directly via Google Play:

* 📱 **Join on Android**: [https://play.google.com/store/apps/details?id=com.codewarren.telemetry.camera](https://play.google.com/store/apps/details?id=com.codewarren.telemetry.camera)
* 🌐 **Join on the Web**: [https://play.google.com/apps/testing/com.codewarren.telemetry.camera](https://play.google.com/apps/testing/com.codewarren.telemetry.camera)

---

## Key Features & Capabilities

### 1. Spatial Attitude & Horizon HUD
* **Optical Line-of-Sight Elevation (Pitch)**: True inclination angle of the camera lens from ground (-90°) to zenith (+90°), calibrated to the optical center.
* **Compass Heading / Azimuth**: Precision heading (0°–359°) with 16-point cardinal direction formatting (e.g., `293° WNW`).
* **Horizon Tilt / Roll Angle**: Real-time roll indicator (`ROLL: +0.3°`) for accurate camera leveling.
* **Artificial Horizon Instrument**: Primary flight display (PFD) avionics instrument featuring a dynamic pitch ladder with 10° rungs and 5° graduation bars, roll-stabilized 0° horizon line, top bank angle arc (-60° to +60°) with roll pointer, and center aircraft waterline boresight (`──┐ • ┌──`) with automatic green leveling highlight.

### 2. Environmental & Atmospheric Sensing
* **Barometric Air Pressure**: Live atmospheric pressure displayed in inches of mercury (`inHg`) or hectopascals (`hPa`) via the device's onboard barometer sensor.
* **Ambient Air Temperature**: Live ambient temperature in Fahrenheit (`°F`) or Celsius (`°C`) on supported hardware.

### 3. Kinematics & GPS Navigation
* **Flexible Coordinate Formats**:
  * **Both**: Simultaneous Decimal Degrees and Degrees, Minutes, Seconds (DMS).
  * **Decimal Only**: `37.81993° N, 122.47825° W`
  * **DMS Only**: `37°49'11.7"N, 122°28'41.7"W`
  * **Hidden / Off**
* **Movement Speed**: Ground speed in miles per hour (`mph`) or kilometers per hour (`km/h`).
* **Track Bearing**: Course-over-ground travel direction (e.g., `TRACK: 142° SE`).
* **True Altitude**: Elevation above mean sea level in feet (`ft`) or meters (`m`).
* **GPS Accuracy**: Live horizontal positioning precision radius (`±9.8 ft` / `±3.0 m`).

### 4. Dynamic & Responsive Layout
* **Adaptive Orientation**: Seamlessly adapts between Portrait and Landscape:
  * **Portrait**: Top Telemetry Banner, central reticle, stacked Motion Card above Cockpit Card at the bottom.
  * **Landscape**: Left-side Cockpit Card (Heading, Roll, Elevation) and Right-side Motion Card (Speed, Track) to preserve full camera view without blocking the subject.
* **Physically Upright Burning**: Saved photos are automatically normalized so watermarked telemetry is always right-side-up regardless of device orientation at the moment of capture.
* **Auto-Collapsing Cards**: When individual metrics are toggled off in settings, card layouts dynamically adjust and cleanly auto-collapse when all contained items are disabled.

### 5. Flexible Capture Modes & Watermarks
* **Clean Capture with 100% Embedded Metadata (Default)**: Fast, non-blocking zero-lag shutter saves pristine original photos instantly while embedding 100% of raw sensor telemetry inside standard EXIF and structured JSON metadata.
* **On-Demand Burn-in Copies**: Generate burned-in duplicate photos directly from the gallery at any time, permanently stamping cyber-cyan HUD banners, avionics artificial horizon instrument, and cockpit telemetry cards onto photo pixels at full sensor resolution.
* **Watermark Badge**: Burned copies include a sleek `⌖ TELEMETRY` corner badge to quickly identify telemetry photos in external photo viewers.

### 6. Interactive Telemetry Gallery Viewer
* **Horizontal Swipe Pager**: Smoothly swipe between captured photos with smooth page transitions and beyond-viewport caching.
* **Matching HUD Toggle (`Layers` icon)**: Seamlessly toggle the telemetry overlay on both the live camera viewfinder and in the gallery viewer via the unified Layers icon button.
* **Burn & Save Copy**: Tap the burn icon in the gallery to generate and save a burned-overlay copy from a clean photo at any time.
* **EXIF Metadata Inspector**: Built-in bottom sheet displays every raw metric, coordinate, date/time, and sensor reading stored in the photo.
* **Safe Deletion**: Built-in delete confirmation dialog to prevent accidental removal of field photos.
* **System Share**: Share photos with external apps directly from the gallery toolbar.

### 7. Advanced Field Survey Tools (Opt-in in Settings)
* **Magnetic Declination (True North vs. Magnetic North)**: Computes local magnetic declination via NOAA/WMM algorithms (`GeomagneticField`) to allow true-geographic or magnetic heading.
* **Sun & Moon Ephemeris**: Calculates real-time solar/lunar azimuth and elevation coordinates.
* **Angular Ranging Grid**: Displays milliradian and degree tick marks on the reticle to estimate distant object heights ($h = d \cdot \tan\theta$).

### 8. Synchronized Telemetry Video Recording (.SRT)
* Seamlessly toggle between **[ PHOTO | VIDEO ]** modes right above the shutter button.
* High-definition video recording with CameraX `VideoCapture` saved to `Movies/TelemetryCamera/`.
* Automatically writes a companion `.srt` subtitle file containing per-second Heading, Pitch, Roll, Altitude, Speed, and GPS coordinates synchronized with the video frames:
  ```text
  1
  00:00:00,000 --> 00:00:01,000
  HDG: 284° WNW T | PITCH: +3.7° | ROLL: +0.0°
  ALT: Alt: 5280ft (±10ft) | SPD: 15.2 mph | 37.81993° N, 122.47826° W
  ```
* **VLC / Media Player Ready**: Automatically displays real-time telemetry captions during playback while keeping the original video footage pristine.
* **FFmpeg Hardcoded Stamping**: Burn telemetry directly into video frames for client reports:
  ```bash
  ffmpeg -i TELEMETRY_VIDEO_xxx.mp4 -vf "subtitles=TELEMETRY_VIDEO_xxx.srt:force_style='Fontname=Monospace,FontSize=16,PrimaryColour=&H00FFFF,BorderStyle=3,MarginV=25'" -c:a copy BURNED.mp4
  ```
* **Video Editor Compatibility**: Drop the companion `.srt` directly into DaVinci Resolve, Adobe Premiere, or Final Cut Pro for custom graphic HUD styling.

### 9. Interactive OpenStreetMap (OSM) & Professional GIS Export
* **Built-in OpenStreetMap Survey Map**: Full-screen interactive map powered by `osmdroid` with zero proprietary API key lock-in. Features high-contrast tactical cyber styling, live metric/imperial scale bar, magnetic/true north compass overlay, and smooth bounding box navigation.
* **Multi-Layer Tile Switcher**: Toggle dynamically between Standard OSM Mapnik, OpenTopoMap (contour terrain with elevation profiles), and USGS Satellite imagery.
* **Directional Survey Markers & Track Polylines**: Plots chronological camera capture paths connected by polyline tracks. Each photo waypoint displays custom orientation pins indicating the camera's optical heading azimuth, with bottom inspection cards linking back to the gallery.
* **External GPX/KML/KMZ Survey Import**: Import external field tracks, flood survey paths, or drone flight logs from device storage with automatic coordinate parsing, bounding box calculation, and geodesic distance summation.
* **Google Earth KMZ 3D Bundles**: Package entire surveys into standalone `.kmz` archives embedding full-resolution photos, 3D `<Camera>` virtual viewports (recreating the exact photographer 6-DoF position, pitch, roll, and heading in Google Earth 3D terrain), and interactive HTML metadata popup cards.
* **Google Earth KML & Waypoints GPX**: Standard GPX 1.1 waypoint files and KML placemarks with 3D camera angles for handheld GPS units, QGIS, and ArcGIS.
* **Sidecar JSON**: Export companion `<photo_name>.json` files containing all raw numeric telemetry metrics.

### 10. Built-in Searchable Glossary & Offline Field Manual
* **In-App Telemetry Glossary**: Search across all sensors, HUD toggles, and underlying mathematical equations ($h = d \cdot \tan\theta$, true heading declination), with one-tap deep links that jump directly to and highlight corresponding settings.
* **Offline Field User Guide**: Complete phototheodolite manual and 11 real-world field workflows bundled directly into the app—accessible 100% offline in remote wilderness and offshore zones with zero cellular connectivity.

### 11. System Image Association (Open With / Share)
* Associated as a system image viewer (`ACTION_VIEW` and `ACTION_SEND` intent filters).
* When browsing photos in Google Photos, Files, or other galleries, tap **"Open with..."** or **"Share"** and select **Telemetry Camera** to inspect the photo's embedded sensor telemetry within the app's interactive playback HUD.

---

## Telemetry Metadata Specification

All telemetry data is saved with 100% fidelity into every photo, regardless of whether individual UI elements are hidden on screen.

### 1. Structured JSON in `ExifInterface.TAG_USER_COMMENT`
```json
{
  "elevation": -12.45,
  "heading": 284.18,
  "roll": 1.82,
  "lat": 37.819928,
  "lon": -122.478255,
  "alt": 67.06,
  "acc": 3.05,
  "baro": 1013.25,
  "temp": 18.5,
  "speed": 1.25,
  "declination": 7.41,
  "sunAz": 83.4,
  "sunEl": -30.1,
  "moonAz": 130.2,
  "moonEl": 67.2,
  "timestamp": 1793483155515,
  "isBurned": false
}
```

### 2. Standard EXIF Tags
* `TAG_GPS_LATITUDE` / `TAG_GPS_LATITUDE_REF`
* `TAG_GPS_LONGITUDE` / `TAG_GPS_LONGITUDE_REF`
* `TAG_GPS_ALTITUDE` / `TAG_GPS_ALTITUDE_REF`
* `TAG_GPS_IMG_DIRECTION` (Bearing in degrees)
* `TAG_GPS_IMG_DIRECTION_REF` (`M` for Magnetic)
* `TAG_GPS_SPEED` / `TAG_GPS_SPEED_REF`
* `TAG_GPS_TRACK` / `TAG_GPS_TRACK_REF`
* `TAG_DATETIME_ORIGINAL` (ISO 8601 timestamp)
* `TAG_IMAGE_DESCRIPTION` (Human-readable summary of all telemetry metrics)
* `TAG_SOFTWARE`: `TelemetryCamera 1.0`

---

## Project Structure

```
telemetry-camera/
├── .github/workflows/
│   └── clean-history.yml         # CI workflow for repository health
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml   # App manifest, permissions & intent filters
│   │   ├── assets/               # Bundled offline user guide HTML & WebP graphics
│   │   ├── java/com/telemetry/camera/
│   │   │   ├── MainActivity.kt               # Lifecycle, permissions, intent dispatch & routing
│   │   │   ├── camera/
│   │   │   │   ├── CameraCaptureManager.kt   # CameraX capture, orientation matrix & background saving
│   │   │   │   └── ImageOverlayProcessor.kt  # High-res Canvas rasterization & avionics HUD burning
│   │   │   ├── data/
│   │   │   │   ├── AppSettings.kt            # Settings data model, UnitSystem, Enums & Preferences
│   │   │   │   ├── ExifTelemetryHelper.kt    # EXIF UserComment JSON serializer & tag reader/writer
│   │   │   │   ├── GalleryRepository.kt      # MediaStore querying, batch deletion & photo management
│   │   │   │   ├── GisExportHelper.kt        # GeoJSON, GPX, KML, CSV & 3D Google Earth KMZ exporter
│   │   │   │   ├── SurveyPathParser.kt       # GPX, KML, KMZ stream parser & geodesic distance math
│   │   │   │   └── TelemetryData.kt          # Metric formatting (DMS, Cardinal, Altitude, Speed, etc.)
│   │   │   ├── sensors/
│   │   │   │   ├── EnvironmentTracker.kt     # Barometer and ambient temperature sensor listeners
│   │   │   │   ├── EphemerisCalculator.kt    # Solar & lunar celestial azimuth/elevation math
│   │   │   │   ├── LocationTracker.kt        # FusedLocationProviderClient (Lat, Lon, Alt, Speed, Bearing)
│   │   │   │   └── OrientationTracker.kt     # Rotation vector sensor & optical-axis pitch/roll math
│   │   │   └── ui/
│   │   │       ├── CameraScreen.kt           # Viewfinder, HUD layer, shutter button & mode toggles
│   │   │       ├── GalleryViewerScreen.kt    # Photo/video pager, EXIF HUD playback, GIS tools & burn
│   │   │       ├── GlossaryHelpDialog.kt     # Searchable sensor mathematics & settings deep-link modal
│   │   │       ├── MapViewerScreen.kt        # Interactive OSM viewer, tile switcher, track & marker overlay
│   │   │       ├── SettingsScreen.kt         # Comprehensive settings cards with live toggles
│   │   │       ├── UserGuideDialog.kt        # 100% offline illustrated field user guide webview dialog
│   │   │       ├── components/
│   │   │       │   └── HudOverlay.kt         # Vector HUD composables (artificial horizon, cards, reticle)
│   │   │       └── theme/                    # Cyber/tactical HUD color palette and typography
│   │   └── res/                              # Vector drawables, mipmaps, strings, and XML styles
│   └── build.gradle.kts                      # Module build configuration, dependencies & ProGuard rules
├── docs/
│   ├── index.html                # Product landing page & web showcase
│   ├── privacy.html              # Privacy policy for Google Play & web
│   ├── userguide.md              # Illustrated field user guide & phototheodolite workflows
│   ├── keystore.properties.example # Signing credentials template
│   ├── screenshots/              # High-resolution viewfinder & UI captures
│   └── store_assets/             # App icons, feature graphics & store listing text
├── gradle/
│   ├── libs.versions.toml        # Version catalog for dependencies and plugins
│   └── wrapper/                  # Gradle Wrapper JAR and properties
├── scripts/
│   └── generate_offline_guide.py # Compiles userguide.md into bundled offline HTML assets
├── build.gradle.kts              # Root project build configuration
├── gradle.properties             # JVM arguments & AndroidX flags
├── gradlew                       # Gradle wrapper shell script (Linux/macOS)
├── gradlew.bat                   # Gradle wrapper batch script (Windows)
├── settings.gradle.kts           # Root project name, subprojects & repository declarations
├── LICENSE                       # MIT Open Source License
├── .gitignore                    # Git exclusions (keystores, build outputs, local properties)
└── README.md                     # Project overview and technical documentation
```

---

## Building and Installation

### Prerequisites
* Android Studio Iguana / Jellyfish (or newer)
* Android SDK 35 (compileSdk 35, minSdk 26)
* JDK 17 or JDK 21

### 1. Build Debug APK
```bash
./gradlew assembleDebug
```
Output: `app/build/outputs/apk/debug/app-debug.apk`

### 2. Build Signed Release APK
1. Copy `docs/keystore.properties.example` to `keystore.properties`:
   ```bash
   cp docs/keystore.properties.example keystore.properties
   ```
2. Fill in your keystore path, alias, and passwords.
3. Build the release APK:
   ```bash
   ./gradlew assembleRelease
   ```
Output: `app/build/outputs/apk/release/app-release.apk`

### 3. Install to Connected Device
```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

---

## License

MIT License. See [LICENSE](LICENSE) for details.
