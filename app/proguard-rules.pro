# Pixel Call Recorder - ProGuard Configuration
# 最適化とコード難読化の設定

# デバッグ情報の保持（クラッシュレポート用）
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# 最適化設定
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification
-dontpreverify

# 警告の抑制
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn kotlin.jvm.internal.**

# Android基本クラスの保持
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# Keep Room database classes
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *

# Keep data classes used with Room
-keep class com.callrecorder.pixel.data.** { *; }

# Keep service classes
-keep class com.callrecorder.pixel.service.** { *; }

# Keep audio processing classes
-keep class com.callrecorder.pixel.audio.** { *; }

# Keep Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepnames class kotlinx.coroutines.android.AndroidExceptionPreHandler {}
-keepnames class kotlinx.coroutines.android.AndroidDispatcherFactory {}

# Keep Kotlin metadata
-keep class kotlin.Metadata { *; }

# アプリケーション固有のクラス保持
-keep class com.callrecorder.pixel.MainActivity { *; }
-keep class com.callrecorder.pixel.ui.** { *; }

# 権限管理クラスの保持
-keep class com.callrecorder.pixel.permission.** { *; }

# エラーハンドリングクラスの保持
-keep class com.callrecorder.pixel.error.** { *; }
-keep class com.callrecorder.pixel.crash.** { *; }

# ログ機能の保持
-keep class com.callrecorder.pixel.logging.** { *; }

# 設定管理の保持
-keep class com.callrecorder.pixel.settings.** { *; }

# ファイル管理の保持
-keep class com.callrecorder.pixel.storage.** { *; }

# Parcelable実装の保持
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# Serializable実装の保持
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Enum クラスの保持
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ネイティブメソッドの保持
-keepclasseswithmembernames class * {
    native <methods>;
}

# ViewBinding の保持
-keep class * extends androidx.viewbinding.ViewBinding {
    public static *** inflate(...);
    public static *** bind(...);
}

# MediaRecorder関連の保持
-keep class android.media.** { *; }
-keep class android.telecom.** { *; }
-keep class android.telephony.** { *; }

# 音声処理ライブラリの保持
-keep class androidx.media.** { *; }

# リフレクション使用クラスの保持
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}

# デバッグビルド用の設定
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}