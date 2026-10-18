# Transparent Build Settings (Avoid false positive packer heuristics)
-dontobfuscate
-dontshrink

# Keep GSON DTO Models & Data Classes
-keep class com.example.crewmate.data.remote.xcrew.** { *; }
-keepclassmembers class com.example.crewmate.data.remote.xcrew.** { *; }

# Keep Room Database Entities & DAOs
-keep class com.example.crewmate.database.** { *; }
-keepclassmembers class com.example.crewmate.database.** { *; }

# Keep Domain Models
-keep class com.example.crewmate.schedule.WorkSchedule { *; }
-keep class com.example.crewmate.schedule.WorkScheduleMonth { *; }
-keep class com.example.crewmate.alarm.WorkAlarmItem { *; }
-keep class com.example.crewmate.alarm.WorkAlarmSettings { *; }
