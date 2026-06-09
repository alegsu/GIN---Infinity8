# Retrofit / OkHttp
-dontwarn okhttp3.**
-keep class retrofit2.** { *; }
-keep class okhttp3.** { *; }
-keepattributes Signature
-keepattributes Exceptions

# Gson models
-keep class it.shinyup.meteoradar.data.models.** { *; }

# OSMDroid
-keep class org.osmdroid.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
