# Add project specific ProGuard rules here.

# ─── General ───
-dontobfuscate
# Keep line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ─── Hilt / Dagger ───
-keepclassmembers,allowobfuscation class * {
    @dagger.hilt.* <fields>;
    @javax.inject.* <fields>;
}

# ─── ML Kit ───
-keep class com.google.mlkit.** { *; }

# ─── Security-crypto (EncryptedSharedPreferences, EncryptedFile) ───
-keep class androidx.security.crypto.** { *; }
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.api.client.**
-dontwarn org.joda.time.**
-dontwarn com.google.crypto.tink.util.KeysDownloader

# ─── Room ───
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }

# ─── Compose: material-icons-extended ───
# R8 full mode already tree-shakes unused icon classes; this ensures the icons
# that ARE referenced survive.
-keep class androidx.compose.material.icons.** { *; }

# ─── WorkManager ───
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.CoroutineWorker { *; }
-keep class * extends androidx.work.ListenableWorker { *; }

# ─── ZXing (QR) ───
-keep class com.google.zxing.** { *; }

# ─── Remove verbose logging in release ───
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
