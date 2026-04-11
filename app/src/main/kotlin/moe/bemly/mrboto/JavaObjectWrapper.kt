package moe.bemly.mrboto

/**
 * Kotlin-side helper for the JNI reference registry.
 *
 * The actual registry lives in C (android-jni-bridge.c).
 * This object provides Kotlin-facing methods that call
 * back into the C registry via native methods on MRuby.
 *
 * Usage:
 *   val id = mruby.registerJavaObject(someObject)
 *   // id is now visible in mruby as an integer
 */
object JavaObjectWrapper {
    // All operations go through MRuby's native methods:
    //   mruby.registerJavaObject(obj) -> Int (registry ID)
    //
    // The C side stores JNI GlobalRefs and returns integer IDs.
    // mruby wraps these IDs in Mrboto::JavaObject Data objects.
}
