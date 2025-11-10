# --- Generic Android rules ---
# Keep debugging info for stack traces
-keepattributes SourceFile,LineNumberTable
# Keep attributes necessary for reflection, serialization, and annotations
-keepattributes Exceptions,Signature,InnerClasses,*Annotation*

# --- osmdroid ---
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# --- MPAndroidChart ---
-keep class com.github.mikephil.charting.** { *; }
-dontwarn com.github.mikephil.charting.**

# --- GeoPackage, ORMLite, and related dependencies (Comprehensive Rules) ---

# Keep all classes in the NGA (GeoPackage) and ORMLite namespaces.
# This is a broad rule to prevent any class from being removed or obfuscated.
-keep class mil.nga.** { *; }
-keep class com.j256.ormlite.** { *; }

# CRITICAL: Keep the default, no-argument constructors for any class
# that is used as a database entity. ORMLite uses reflection to create
# instances of these classes, and R8 will remove the constructor if it's not
# called directly in your code.
-keepclassmembers,allowobfuscation class * {
    @com.j256.ormlite.field.DatabaseField <fields>;
    public <init>();
}

# Keep other specific ORMLite classes that are extended in the code.
-keep public class * extends com.j256.ormlite.dao.BaseDaoImpl

# --- Suppress Warnings for Optional/Server-Side Dependencies ---
# These rules are based on the 'missing_rules.txt' output. They tell R8
# that it's OK if these classes are missing, as they are optional
# dependencies of the libraries that are not used on Android.
-dontwarn com.j256.ormlite.**
-dontwarn mil.nga.**
-dontwarn java.awt.**
-dontwarn java.beans.**
-dontwarn javax.persistence.**
-dontwarn jsqlite.**
-dontwarn org.sqlite.**
-dontwarn org.slf4j.**
-dontwarn org.apache.log4j.**
-dontwarn org.apache.logging.log4j.**
