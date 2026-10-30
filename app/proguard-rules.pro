# Nombres legibles en los registros de error.
-dontobfuscate

# Gson/Retrofit/Room usan reflexión sobre estos modelos.
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod
-keep class com.peli.app.data.** { *; }
-keep class com.peli.app.update.** { *; }
-keep class * extends androidx.room.RoomDatabase
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement

# Reproductor de YouTube: el puente con JavaScript y los estados se resuelven por nombre.
-keep class com.pierfrancescosoffritti.androidyoutubeplayer.** { *; }
