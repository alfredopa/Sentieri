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
 -keep class org.osmdroid.** { *; }
 -dontwarn org.osmdroid.**