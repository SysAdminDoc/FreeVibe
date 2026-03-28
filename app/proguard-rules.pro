# Moshi
-keep class com.freevibe.data.remote.** { *; }
-keepclassmembers class com.freevibe.data.remote.** { *; }
-keep class com.freevibe.service.FavoriteExportItem { *; }
-keep @com.squareup.moshi.JsonClass class * { *; }

# Retrofit
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *

# NewPipe Extractor (Rhino JS engine)
-dontwarn javax.script.ScriptEngineFactory

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
