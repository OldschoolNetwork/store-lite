-dontwarn javax.naming.**
-dontwarn java.nio.**
-dontwarn java.lang.management.**
-keepattributes Signature,InnerClasses,EnclosingMethod
-keep class com.oldschoolnetwork.lite.crypto.jcajce.provider.** { *; }
-dontwarn com.oldschoolnetwork.lite.crypto.jcajce.**

-keep class com.oldschoolnetwork.lite.crypto.jce.provider.** { *; }
