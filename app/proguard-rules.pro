# ==============================================================================
# CrewMate Security & R8 Optimization Rules (Zero-Regression / Anti-False-Positive)
# ==============================================================================

# 1. Avoid heuristic false positives (Google Play Protect / Ikarus) while
#    allowing R8 optimization passes to inline and strip XHR endpoint constants.
-dontobfuscate
-dontshrink
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod

# 2. Keep Xcrew Remote DTO Models & Parsers
-keep class com.example.crewmate.data.remote.xcrew.** { *; }
-keepclassmembers class com.example.crewmate.data.remote.xcrew.** { *; }

# 3. Keep Room Database Entities, DAOs, and TypeConverters
-keep class com.example.crewmate.database.** { *; }
-keepclassmembers class com.example.crewmate.database.** { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Database class * { *; }

# 4. Keep Schedule & Alarm Domain Models
-keep class com.example.crewmate.schedule.WorkSchedule { *; }
-keep class com.example.crewmate.schedule.WorkScheduleMonth { *; }
-keep class com.example.crewmate.alarm.WorkAlarmItem { *; }
-keep class com.example.crewmate.alarm.WorkAlarmSettings { *; }

# 5. Keep WebView JavaScriptInterface Bridge Methods & WebView Callbacks
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes JavascriptInterface
-keepclassmembers class * extends android.webkit.WebViewClient {
    public void *(android.webkit.WebView, java.lang.String, android.graphics.Bitmap);
    public void *(android.webkit.WebView, java.lang.String);
    public boolean *(android.webkit.WebView, android.webkit.WebResourceRequest);
    public boolean *(android.webkit.WebView, java.lang.String);
}

# 6. Strip verbose debug/test logs in Release builds (Preserve Info/Warn/Error)
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}
