-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

-keep class xyz.gianlu.librespot.** { *; }
-keep class com.google.protobuf.** { *; }
-dontwarn xyz.gianlu.librespot.**
-dontwarn com.google.protobuf.**
