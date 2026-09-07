# ProGuard rules for CineSubz Android TV
-keep class com.cinesubz.tv.model.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Media3 ExoPlayer keep rules
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.ui.** { *; }
