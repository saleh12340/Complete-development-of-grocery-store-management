# Gson reads backup DTOs and Room entities reflectively during encrypted backup/restore.
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod
-keep class com.saleh.alazzi.grocery.data.backup.BackupPayload { *; }
-keep class com.saleh.alazzi.grocery.data.backup.BackupEnvelope { *; }
-keep class com.saleh.alazzi.grocery.data.backup.BackupInspection { *; }
-keep class com.saleh.alazzi.grocery.data.backup.BackupResult { *; }
-keep class com.saleh.alazzi.grocery.data.local.entity.** { *; }
-keep class com.saleh.alazzi.grocery.security.** { *; }

# Keep Kotlin metadata used by reflection-based serializers.
-keep class kotlin.Metadata { *; }
