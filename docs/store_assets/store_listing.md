# Google Play Store Listing — Telemetry Camera

---

## 1. App Title & Descriptions

### App Name (Max 30 characters)
```
Telemetry Camera
```
*(Or alternate: `Telemetry Camera: Optical HUD`)*

### Short Description (Max 80 characters)
```
Tactical optical HUD camera with GPS, attitude, 3D Google Earth KMZ & ephemeris.
```
*(78 characters — complies with Google Play 80-character limit)*

**Alternative Options (Max 80 characters):**
* Option A (Survey & GIS Focus):
  `Field survey camera with GPS, attitude, 3D Google Earth KMZ & offline manual.` (77 chars)
* Option B (Phototheodolite Focus):
  `Digital phototheodolite & HUD camera with true heading, tilt & 3D KMZ export.` (77 chars)
* Option C (Original):
  `Tactical optical HUD camera with real-time GPS, attitude, altitude & ephemeris.` (79 chars)

---

### Full Description (Play Store clean plain text)

```
Telemetry Camera is a professional optical survey and field camera featuring a real-time avionics Heads-Up Display (HUD) projected over the live viewfinder. Built for surveyors, engineers, first responders, inspectors, and outdoor enthusiasts, Telemetry Camera combines precision hardware sensor fusion with non-destructive metadata preservation.

Every photo preserves 100% of raw sensor metrics inside standard EXIF tags, structured companion JSON sidecars, and Google Earth 3D KMZ bundles—ready for archival verification and GIS analysis.

KEY CAPABILITIES

REAL-TIME AVIONICS HUD

• Artificial Horizon & Pitch Ladder: Real-time elevation and roll indicators with dynamic leveling.

• Center Reticle & Ranging Grid: Milliradian and degree graduations for distance and height estimation.

• Top Telemetry Banner: Microsecond-accurate UTC/local timestamp, barometric altitude, GNSS accuracy, and air pressure.

• Cockpit Dashboard: Ground speed, track bearing, heading, elevation angle, and lateral tilt.

• One-Tap HUD Toggle: Quickly reveal or collapse the live HUD overlay.

TRUE NORTH GEOMAGNETIC SURVEYING

• NOAA Declination: Calculates local magnetic declination via the World Magnetic Model (WMM).

• Dual North Reference: Switch seamlessly between True Geographic North and Magnetic North.

• Multi-Mode Compass: Degrees & Cardinal, Degrees Only, Cardinal Only, or Hidden.

GOOGLE EARTH 3D KMZ & GIS EXPORTS

• Standalone KMZ Survey Bundles: Package entire surveys into a single .KMZ file with full-res photos, interactive HTML popups, and 3D camera viewports that reorient Google Earth to your exact physical viewpoint, pitch, and heading.

• Lightweight KML & GPX: Export clean placemarks and Garmin-compatible GPX 1.1 waypoints for QGIS, ArcGIS, and Gaia GPS.

• Companion JSON: Export 35+ precision telemetry metrics per photo.

SOLAR & LUNAR EPHEMERIS

• Astronomical Tracking: Real-time azimuth and elevation tracking for the Sun and Moon.

• Sky Reticle Markers: Visual indicators appear directly in the sky reticle when pointing toward the Sun or Moon.

SYNCHRONIZED VIDEO RECORDING (.SRT)

• HD Video Capture: Continuous CameraX sensor logging.

• Companion Subtitles: Synchronized per-second .SRT subtitle files tracking heading, pitch, roll, altitude, speed, and GPS coordinates during playback in VLC.

BUILT-IN OFFLINE FIELD MANUAL

• 100% Offline Guide: Digital phototheodolite manual with 11 field use cases (triangulation, telecom, solar, forestry, marine, disaster response).

• Searchable Glossary: Instant search across sensors, HUD metrics, and formulas with one-tap deep links to settings.

PRIVACY FIRST & 100% ON-DEVICE

• 100% Offline: All sensor fusion and camera frames remain strictly on your physical device.

• Zero Analytics: No trackers, no analytics SDKs, and no cloud uploads.

• Ad-Free: No banner ads, no popups, and no paywalls.

PERMISSIONS

• Camera: Viewfinder preview and image capture.

• Location: Coordinates, altitude, heading, and declination.

• Microphone: Audio capture during video recording.
```

---

## 2. Store Categorization & Tags

* **Application Category**: `Photography` (or `Tools`)
* **Tags**: `Camera`, `GPS`, `Surveying`, `Compass`, `Photography`, `Tools`, `Navigation`

---

## 3. Data Safety Form Answers (Play Console)

When completing the **Data Safety** section in Google Play Console:

| Question / Data Type | Answer | Explanation |
| :--- | :--- | :--- |
| **Data Collection** | **No data is collected** | All data remains on the user's device and is never transmitted off-device. |
| **Location Data** | Collected on-device only | Processed locally for metadata tagging and HUD display. Not shared with third parties. |
| **Photos and Videos** | Stored on-device only | User-generated photos saved directly to device storage. |
| **Audio Recordings** | Recorded on-device only | Captured only during video recording, stored locally. |
| **User Tracking / Ad IDs** | **No** | The app contains no advertising or analytics SDKs. |

---

## 4. Content Rating (IARC)

* **Violence, Sexual Content, Offensive Language, Controlled Substances**: None.
* **User-to-User Interaction**: None.
* **Shares Physical Location with other users**: **No** (Location is strictly local).
* **Expected Rating**: **Everyone / PEGI 3 / ESRB E**.

---

## 5. Graphic Assets Reference

* **App Icon**: `docs/store_assets/icon_512.png` (512 × 512 px PNG)
* **Feature Graphic**: `docs/store_assets/feature_graphic_1024x500.png` (1024 × 500 px PNG)
* **Screenshots**: Available in `docs/screenshots/`:
  - `01_camera_screen.png` (Camera Viewfinder with HUD)
  - `02_gallery_screen.png` (Gallery Photo Viewer)
  - `02b_gallery_tools.png` (GIS & Metadata Export Menu)
  - `03_settings_screen.png` (Configuration & Survey Tools)
  - `04_glossary_dialog.png` (Searchable Telemetry Glossary)

---

## 6. App Support & Developer Details (Play Console)

* **Support Email**: `telemetrycamera@codewarren.com`
* **Website**: `https://telemetrycamera.com`
* **Privacy Policy URL**: `https://telemetrycamera.com/privacy.html`
* **Issue Tracker**: `https://github.com/foofoodog/telemetry-camera/issues`
