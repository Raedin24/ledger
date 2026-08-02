# Keep Room/SQLCipher and domain model names stable through R8.
# net.zetetic.database.** covers the modern sqlcipher-android JNI classes whose
# native methods are registered by name; renaming them breaks the native bridge.
-keep class net.zetetic.database.** { *; }
-keep class com.ledger.domain.model.** { *; }
-keepclassmembers class * { @androidx.room.* <methods>; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * { @kotlinx.serialization.Serializable <methods>; }
