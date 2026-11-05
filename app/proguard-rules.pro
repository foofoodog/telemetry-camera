# Proguard rules for TelemetryCamera
-keep class com.telemetry.camera.data.TelemetryData { *; }

# osmdroid OpenStreetMap rules
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**
