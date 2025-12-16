########################
# --- FFmpegKit ---
########################
-keep class com.arthenica.ffmpegkit.** { *; }
-dontwarn com.arthenica.ffmpegkit.**

########################
# --- Chaquopy (Python integration) ---
########################
-keep class com.chaquo.python.** { *; }
-dontwarn com.chaquo.python.**

########################
# --- Python paketleri (yt-dlp, mutagen, requests) ---
########################
-dontwarn org.python.**
-dontwarn javax.**
-keep class org.python.** { *; }

########################
# --- Jetpack Compose ---
########################
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Compose compiler bazı refleksiyonları kullanıyor
-keep class kotlin.** { *; }
-dontwarn kotlin.**
-keep class kotlinx.** { *; }
-dontwarn kotlinx.**

########################
# --- Genel ayarlar ---
########################
# Native kod çağrıları (JNI)
-keepclasseswithmembers class * {
    native <methods>;
}

# Activity’ler & servisler silinmesin
-keep class * extends android.app.Activity
-keep class * extends android.app.Service
