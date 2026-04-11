# Keep JNI methods
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# Keep MRuby public API
-keep public class com.mrboto.MRuby { *; }
