-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-keepattributes SourceFile,LineNumberTable

# Hilt
-keep class dagger.hilt.** { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keep class **_HiltComponents** { *; }
-keep class **_GeneratedInjector { *; }
-keep class **_Factory { *; }
-keep class **_MembersInjector { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-keepclassmembers class * extends androidx.room.RoomDatabase {
    abstract *;
}
-keepclassmembers @androidx.room.Dao interface * {
    abstract <methods>;
}
-dontwarn androidx.room.**

# Retrofit
-keepattributes Exceptions
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**

# OkHttp
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Gson
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# JavaMail
-keep class javax.mail.** { *; }
-keep class com.sun.mail.** { *; }
-keep class jakarta.mail.** { *; }
-dontwarn javax.mail.**
-dontwarn com.sun.mail.**
-dontwarn jakarta.mail.**
-dontwarn javax.naming.**
-dontwarn javax.net.ssl.**

# Kotlin coroutines
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}

# WorkManager
-keep class androidx.work.** { *; }
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.CoroutineWorker

# Coil
-keep class coil.** { *; }
-dontwarn coil.**

# DataStore
-keep class androidx.datastore.** { *; }

# Security
-keep class androidx.security.crypto.** { *; }

# App models
-keep class com.filevault.pro.domain.model.** { *; }
-keep class com.filevault.pro.data.local.entity.** { *; }
-keep class com.filevault.pro.data.remote.telegram.** { *; }

# Keep Enum names
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    **[] $VALUES;
}

# Keep Parcelables
-keep class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Keep R class
-keep class **.R
-keep class **.R$* {
    <fields>;
}

# Suppress warnings for known issues
-dontwarn com.google.errorprone.annotations.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
-dontwarn org.checkerframework.**
-dontnote sun.**
-dontwarn sun.**
