# Retrofit
-keepattributes Signature
-keepattributes *Annotation*
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * { @retrofit2.http.* <methods>; }
# Gson
-keep class com.gabrielsalem.openroutercredits.** { *; }
-keepclassmembers class com.gabrielsalem.openroutercredits.** { *; }
# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
