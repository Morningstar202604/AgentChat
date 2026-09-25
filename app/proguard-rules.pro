# Keep Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Keep Material3
-keep class androidx.compose.material3.** { *; }

# Keep Kotlin Metadata
-keep class kotlin.Metadata { *; }

# Keep Activity
-keep public class * extends android.app.Activity

# ============================================================
# Phase 1: network / serialization / database keep rules
# ============================================================

# --- kotlinx.serialization ---
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# @Serializable generated serializers
-keep,includedescriptorclasses class com.agentchat.lite.**$$serializer { *; }
-keepclassmembers class com.agentchat.lite.** {
    *** Companion;
}
-keepclasseswithmembers class com.agentchat.lite.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- OkHttp ---
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# --- exp4j ---
-keep class net.objecthunter.exp4j.** { *; }
-dontwarn net.objecthunter.exp4j.**

# --- Room entities & DAOs ---
-keep class com.agentchat.lite.data.local.entity.** { *; }
-keep class * extends androidx.room.RoomDatabase { *; }

# --- Coroutines ---
-dontwarn kotlinx.coroutines.**

# --- Markwon ---
-keep class io.noties.markwon.** { *; }
-dontwarn io.noties.markwon.**
-keep class org.commonmark.** { *; }
-dontwarn org.commonmark.**
-keep class io.noties.prism4j.** { *; }
-dontwarn io.noties.prism4j.**
-keep,allowobfuscation,allowshrinking class io.noties.markwon.core.SpannableFactory
-keepclassmembers class * implements io.noties.markwon.MarkwonPlugin { *; }
