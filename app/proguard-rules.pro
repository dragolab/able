# Able App Proguard rules
-keep class com.dragolabs.able.core.model.** { *; }
-keepclassmembers class * {
    @androidx.room.* *;
}
