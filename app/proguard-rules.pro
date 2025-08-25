# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
-dontwarn javax.persistence.Basic
-dontwarn javax.persistence.Column
-dontwarn javax.persistence.Entity
-dontwarn javax.persistence.EnumType
-dontwarn javax.persistence.Enumerated
-dontwarn javax.persistence.FetchType
-dontwarn javax.persistence.GeneratedValue
-dontwarn javax.persistence.Id
-dontwarn javax.persistence.JoinColumn
-dontwarn javax.persistence.ManyToOne
-dontwarn javax.persistence.OneToMany
-dontwarn javax.persistence.OneToOne
-dontwarn javax.persistence.Table
-dontwarn javax.persistence.Version
-dontwarn org.apache.log4j.Level
-dontwarn org.apache.log4j.Logger
-dontwarn org.apache.log4j.Priority
-dontwarn org.apache.logging.log4j.LogManager
-dontwarn org.apache.logging.log4j.Logger
-dontwarn org.slf4j.ILoggerFactory
-dontwarn org.slf4j.Logger
-dontwarn org.slf4j.LoggerFactory
-dontwarn org.slf4j.impl.StaticLoggerBinder
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**
# Mantiene tutte le classi pubbliche, interfacce, enum e i loro membri pubblici/protetti
# all'interno del package mil.nga.geopackage e dei suoi sottopackage.
-keep public class mil.nga.geopackage.** {
      public protected *;
}
-keep public interface mil.nga.geopackage.** {
      public protected *;
}
-keep enum mil.nga.geopackage.** {
      public protected *;
}

# Se la libreria usa JNI (codice nativo) per le proiezioni, potresti dover
# mantenere i nomi dei metodi nativi e le classi che li contengono.
# Questo è un esempio generico, i nomi specifici andrebbero identificati.
# -keepclasseswithmembernames class * {
#    native <methods>;
# }

# Sopprime gli avvisi per questa libreria se ancora presenti,
# anche se con regole di keep più forti potrebbero non essercene.
-dontwarn mil.nga.geopackage.**
-dontwarn org.sqlite.** # GeoPackage usa SQLite, che a volte genera warning
-dontwarn jsqlite.**   # Una vecchia dipendenza a volte tirata dentro
-keep public class * extends com.j256.ormlite.dao.BaseDaoImpl
-keep public class * extends com.j256.ormlite.stmt.StatementBuilder
# Molto importante per i generics e le collezioni!
-keepattributes Signature,RuntimeVisibleAnnotations,AnnotationDefault
-keep class com.j256.ormlite.** { *; }
-keep interface com.j256.ormlite.** { *; }
-keep enum com.j256.ormlite.** { *; }
-keepclassmembers class * {
    @com.j256.ormlite.field.DatabaseField <fields>;
}
# Keep specific ORMLite annotations if the above is not enough
-keep @com.j256.ormlite.field.DatabaseField class *
-keep @com.j256.ormlite.table.DatabaseTable class *

# Keep annotations for ORMLite classes themselves, and any class that uses ORMLite annotations
-keepclassmembers enum * {
    @com.j256.ormlite.field.DatabaseField *;
}
-keepclassmembers interface * {
    @com.j256.ormlite.field.DatabaseField *;
}
# Keep constructors of entity classes (important for ORMLite)
-keepclassmembers class mil.nga.geopackage.** { # Adjust if entities are in sub-packages
    public <init>(...);
}
