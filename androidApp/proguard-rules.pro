# Jauge: R8 rules of the release build. Most libraries ship their own consumer rules (Retrofit, OkHttp,
# kotlinx.serialization, Koin, RevenueCat, Firebase, Lottie, Compose); only what they miss is listed here.

# Keep the line numbers of crash reports readable, without the original source file names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# kotlinx.serialization: payloads of the API, Supabase rows and type safe navigation routes.
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keepclassmembers @kotlinx.serialization.Serializable class io.jauge.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class io.jauge.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit with suspend functions: generic signatures of Continuation and Response must survive full mode.
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# Supabase (supabase-kt) and Ktor: serializable models are covered above; optional platform classes are absent.
-keep class io.github.jan.supabase.** { kotlinx.serialization.KSerializer serializer(...); }
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**
-dontwarn io.ktor.util.debug.**

# No verbose, debug or info logs in the release build (warnings and errors stay).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
