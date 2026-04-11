# ProGuard rules for mrboto

# Keep the MRuby class and its native methods
-keep class com.mrboto.MRuby {
    *;
}

# Keep native method signatures
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
