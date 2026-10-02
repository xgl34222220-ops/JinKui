# SQLCipher 通过 JNI 回调 Java 层，类名与成员不能被混淆或裁剪。
-keep class net.zetetic.database.** { *; }
-keep class net.zetetic.database.sqlcipher.** { *; }
-dontwarn net.zetetic.database.**
