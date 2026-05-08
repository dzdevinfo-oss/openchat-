# Room
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>(...);
}
-keep class * extends androidx.room.Entity
-keep class * extends androidx.room.Dao
-keep class * extends androidx.room.TypeConverter

# Retrofit & OkHttp
-keepattributes Signature, InnerClasses, EnclosingMethod
-keep class retrofit2.** { *; }
-keep interface retrofit2.** { *; }
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.coroutines.android.HandlerContext {
    val handler;
}

# Gson / JSON
-keep class com.openchat.app.data.model.** { *; }
-keep class com.openchat.app.data.api.** { *; }

# Hilt / Dagger
-keep class dagger.hilt.** { *; }
-keep interface dagger.hilt.** { *; }

# Coil
-keep class coil.** { *; }

# Compose
-keep class androidx.compose.** { *; }

# Keep system classes
-keep class android.speech.** { *; }
