# Able App Proguard rules
-keep class com.dragolabs.able.core.model.** { *; }
-keepclassmembers class * {
    @androidx.room.* *;
}

# Able's own code is small; keep it whole so reflection, app_process entry points
# (HotspotShellMain via Shizuku), Glance receivers and serialized routes keep working.
-keep class com.dragolabs.able.** { *; }
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod, SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# Shizuku: newProcess and remote process helpers are looked up by name.
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }

# Hidden framework APIs are reached by reflection; nothing to keep on the app side, but do not warn.
-dontwarn android.**
-dontwarn com.android.**
