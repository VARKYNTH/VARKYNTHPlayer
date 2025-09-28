# Включаем оптимизацию и уменьшение
-dontoptimize
-dontpreverify
-dontnote
-dontwarn
-optimizationpasses 8

# Убираем неиспользуемые классы, методы, ресурсы
-dontskipnonpubliclibraryclasses
-dontskipnonpubliclibraryclassmembers
-allowaccessmodification
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*

# Убираем дебаг-инфу и строки
-renamesourcefileattribute SourceFile
-keepattributes SourceFile, LineNumberTable

# Не трогаем Activity, Service, BroadcastReceiver, ContentProvider
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# Не трогаем классы с рефлексией
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepclassmembers class * {
   public <init>(android.content.Context, android.util.AttributeSet);
}

-keep class android.media.** { *; }
-keep class android.webkit.** { *; }
-keep class com.google.android.exoplayer2.** { *; }

# Меняем имена всего, что можно
#-obfuscationdictionary proguard_dict.txt
#-classobfuscationdictionary proguard_dict.txt
#-packageobfuscationdictionary proguard_dict.txt

# Удаляем строки (частичная защита)

-keepclasseswithmembernames class * {
    native <methods>;
}


-repackageclasses ''