/**
 * mrboto - JNI bridge for embedding mruby 3.4.0 in Android
 *
 * Provides native methods for:
 *   - Opening/closing an mruby VM state
 *   - Evaluating Ruby source code strings
 *   - Loading and executing precompiled .mrb bytecode
 *
 * Thread safety: Each mrb_state is NOT thread-safe. The Kotlin wrapper
 * ensures serial access per instance.
 */

#include <jni.h>
#include <android/log.h>
#include <mruby.h>
#include <mruby/compile.h>
#include <mruby/dump.h>
#include <mruby/error.h>
#include <mruby/string.h>
#include <mruby/array.h>
#include <mruby/variable.h>
#include <mruby/data.h>

#include <string.h>

#define LOG_TAG "mrboto"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

/* ── Helper: safe exception message extraction ─────────────────────── */

/* Context stored inside an mrb_value via CPTR */
typedef struct {
    mrb_value exc;
    mrb_value result;
} exc_msg_ctx_t;

static mrb_value safe_exc_message(mrb_state *mrb, mrb_value self) {
    (void)self;
    exc_msg_ctx_t *ctx = (exc_msg_ctx_t *)mrb_cptr(self);
    return mrb_funcall(mrb, ctx->exc, "message", 0);
}

static jstring safe_extract_error(JNIEnv *env, mrb_state *mrb) {
    exc_msg_ctx_t ctx;
    ctx.exc = mrb_obj_value(mrb->exc);
    ctx.result = mrb_nil_value();

    mrb_bool error = FALSE;
    mrb_value data = mrb_cptr_value(mrb, &ctx);
    mrb_protect(mrb, safe_exc_message, data, &error);

    const char *msg_str = NULL;
    if (mrb_string_p(ctx.result)) {
        msg_str = mrb_string_value_cstr(mrb, &ctx.result);
    }
    mrb->exc = NULL;

    if (msg_str != NULL) {
        return (*env)->NewStringUTF(env, msg_str);
    }
    return (*env)->NewStringUTF(env, "Unknown Ruby error");
}

/* ── Helper: convert an mruby value to a Java String ────────────────── */

/**
 * Converts an mrb_value to a jstring.
 * Handles all basic types: nil, boolean, integer, float, string, symbol.
 * For complex types, calls Ruby's inspect method.
 * If an exception is pending, returns NULL (caller should check).
 */
static jstring
mrb_value_to_jstring(JNIEnv *env, mrb_state *mrb, mrb_value val)
{
    const char *str = NULL;
    jstring result;

    if (mrb->exc) {
        /* Extract exception message safely */
        return safe_extract_error(env, mrb);
    } else if (mrb_nil_p(val)) {
        str = "nil";
    } else if (mrb_true_p(val)) {
        str = "true";
    } else if (mrb_false_p(val)) {
        str = "false";
    } else if (mrb_integer_p(val)) {
        char buf[32];
        snprintf(buf, sizeof(buf), "%lld", (long long)mrb_integer(val));
        return (*env)->NewStringUTF(env, buf);
    }
#ifndef MRB_NO_FLOAT
    else if (mrb_float_p(val)) {
        char buf[64];
        snprintf(buf, sizeof(buf), "%.15g", (double)mrb_float(val));
        return (*env)->NewStringUTF(env, buf);
    }
#endif
    else if (mrb_string_p(val)) {
        str = mrb_string_value_cstr(mrb, &val);
    } else if (mrb_symbol_p(val)) {
        str = mrb_sym_name(mrb, mrb_symbol(val));
    } else {
        /* Complex object: use Ruby's inspect */
        mrb_value inspected = mrb_inspect(mrb, val);
        if (!mrb->exc && mrb_string_p(inspected)) {
            str = mrb_string_value_cstr(mrb, &inspected);
        } else {
            mrb->exc = NULL;
            str = "<object>";
        }
    }

    if (str == NULL) {
        str = "<null result>";
    }

    result = (*env)->NewStringUTF(env, str);
    return result;
}

/**
 * Extracts an exception message from the current mrb_state as a jstring.
 * Clears the exception afterward so the VM remains usable.
 */
static jstring
extract_error_message(JNIEnv *env, mrb_state *mrb)
{
    if (mrb->exc) {
        return safe_extract_error(env, mrb);
    }
    return (*env)->NewStringUTF(env, "Unknown Ruby error");
}

/* ── JNI Methods ────────────────────────────────────────────────────── */

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Open a new mruby VM state.
 * @return Native pointer (as jlong) to mrb_state, or 0 on failure.
 */
JNIEXPORT jlong JNICALL
Java_moe_bemly_mrboto_MRuby_nativeOpen(JNIEnv *env, jobject thiz)
{
    (void)thiz;

    mrb_state *mrb = mrb_open();
    if (mrb == NULL) {
        LOGE("mrb_open() returned NULL - out of memory");
        return 0;
    }

    LOGI("mruby VM opened: mrb=%p", (void *)mrb);
    return (jlong)(intptr_t)mrb;
}

/**
 * Close and free an mruby VM state.
 * @param mrbPtr Native pointer from nativeOpen.
 */
JNIEXPORT void JNICALL
Java_moe_bemly_mrboto_MRuby_nativeClose(JNIEnv *env, jobject thiz, jlong mrbPtr)
{
    (void)env;
    (void)thiz;

    if (mrbPtr == 0) {
        LOGW("nativeClose called with null pointer");
        return;
    }

    mrb_state *mrb = (mrb_state *)(intptr_t)mrbPtr;
    LOGI("mruby VM closing: mrb=%p", (void *)mrb);
    mrb_close(mrb);
}

/**
 * Evaluate a Ruby source code string.
 * @param mrbPtr Native pointer to mrb_state.
 * @param code   UTF-8 Ruby source code.
 * @return Result as a Java String, or an error message string if evaluation fails.
 */
JNIEXPORT jstring JNICALL
Java_moe_bemly_mrboto_MRuby_nativeEvalString(JNIEnv *env, jobject thiz,
                                       jlong mrbPtr, jstring code)
{
    (void)thiz;

    if (mrbPtr == 0) {
        LOGE("nativeEvalString: null mrb_state");
        return (*env)->NewStringUTF(env, "Error: mruby VM not initialized");
    }

    mrb_state *mrb = (mrb_state *)(intptr_t)mrbPtr;
    if (code == NULL) {
        LOGE("nativeEvalString: null code string");
        return (*env)->NewStringUTF(env, "Error: null code string");
    }

    const char *c_code = (*env)->GetStringUTFChars(env, code, NULL);
    if (c_code == NULL) {
        return (*env)->NewStringUTF(env, "Error: failed to get string UTF chars");
    }

    /* Save the GC arena index so we can restore it after evaluation */
    int ai = mrb_gc_arena_save(mrb);

    /* Clear any pre-existing exception before loading new code.
     * mrb_load_string will crash if mrb->exc is already set from a
     * previous failed eval, because it tries to compile and execute
     * new code while the VM is in an error state. */
    mrb->exc = NULL;

    LOGI("nativeEvalString: about to mrb_load_string, code len=%zu", strlen(c_code));

    /*
     * mrb_load_string compiles and executes the Ruby code.
     * If compilation or execution fails, mrb->exc is set and
     * the return value is mrb_nil_value().
     */
    mrb_value result = mrb_load_string(mrb, c_code);

    LOGI("nativeEvalString: mrb_load_string returned, exc=%p", (void *)mrb->exc);

    (*env)->ReleaseStringUTFChars(env, code, c_code);

    /* Check for exceptions BEFORE restoring GC arena.
     * mrb->exc is a pointer into GC-managed memory — if we restore
     * the arena first, the exception object may be freed and
     * mrb_funcall on it will crash. */
    if (mrb->exc) {
        LOGD("Ruby evaluation error");
        jstring jerr = extract_error_message(env, mrb);
        mrb_gc_arena_restore(mrb, ai);
        return jerr;
    }

    jstring jresult = mrb_value_to_jstring(env, mrb, result);

    /* Restore the GC arena after creating the result string */
    mrb_gc_arena_restore(mrb, ai);

    return jresult;
}

/**
 * Load and execute precompiled .mrb bytecode from a byte array.
 * @param mrbPtr Native pointer to mrb_state.
 * @param bytecode  Java byte array containing .mrb bytecode.
 * @return Result as a Java String, or an error message string if execution fails.
 */
JNIEXPORT jstring JNICALL
Java_moe_bemly_mrboto_MRuby_nativeEvalBytecode(JNIEnv *env, jobject thiz,
                                         jlong mrbPtr, jbyteArray bytecode)
{
    (void)thiz;

    if (mrbPtr == 0) {
        LOGE("nativeEvalBytecode: null mrb_state");
        return (*env)->NewStringUTF(env, "Error: mruby VM not initialized");
    }

    mrb_state *mrb = (mrb_state *)(intptr_t)mrbPtr;
    if (bytecode == NULL) {
        LOGE("nativeEvalBytecode: null bytecode");
        return (*env)->NewStringUTF(env, "Error: null bytecode");
    }

    jsize len = (*env)->GetArrayLength(env, bytecode);
    if (len <= 0) {
        return (*env)->NewStringUTF(env, "Error: empty bytecode");
    }

    jbyte *bytes = (*env)->GetByteArrayElements(env, bytecode, NULL);
    if (bytes == NULL) {
        return (*env)->NewStringUTF(env, "Error: failed to get bytecode array");
    }

    /* Save the GC arena */
    int ai = mrb_gc_arena_save(mrb);

    /* Clear any pre-existing exception before loading bytecode */
    mrb->exc = NULL;

    /*
     * mrb_load_irep_buf reads the .mrb bytecode buffer and executes it.
     * This is simpler than manually parsing the irep and creating a proc.
     */
    mrb_value result = mrb_load_irep_buf(mrb, (const void *)bytes, (size_t)len);

    (*env)->ReleaseByteArrayElements(env, bytecode, bytes, JNI_ABORT);

    /* Restore the GC arena */
    mrb_gc_arena_restore(mrb, ai);

    /* Check for exceptions */
    if (mrb->exc) {
        LOGD("Bytecode execution error");
        return extract_error_message(env, mrb);
    }

    jstring jresult = mrb_value_to_jstring(env, mrb, result);
    mrb_gc_arena_restore(mrb, ai);

    return jresult;
}

/**
 * Get the mruby version string.
 * @return Version string like "3.4.0".
 */
JNIEXPORT jstring JNICALL
Java_moe_bemly_mrboto_MRuby_nativeVersion(JNIEnv *env, jobject thiz, jlong mrbPtr)
{
    (void)thiz;

    if (mrbPtr == 0) {
        return (*env)->NewStringUTF(env, "unknown");
    }

    /*
     * mruby 3.x does not expose a direct version string API.
     * We evaluate MRUBY_VERSION constant.
     */
    mrb_state *mrb = (mrb_state *)(intptr_t)mrbPtr;
    int ai = mrb_gc_arena_save(mrb);

    mrb_value ver = mrb_const_get(mrb, mrb_obj_value(mrb->object_class),
                                  mrb_intern_lit(mrb, "MRUBY_VERSION"));

    const char *ver_str = "unknown";
    if (!mrb->exc && mrb_string_p(ver)) {
        ver_str = mrb_string_value_cstr(mrb, &ver);
    }

    jstring result = (*env)->NewStringUTF(env, ver_str);
    mrb_gc_arena_restore(mrb, ai);
    return result;
}

/**
 * Run a full garbage collection cycle.
 */
JNIEXPORT void JNICALL
Java_moe_bemly_mrboto_MRuby_nativeGC(JNIEnv *env, jobject thiz, jlong mrbPtr)
{
    (void)env;
    (void)thiz;

    if (mrbPtr == 0) return;

    mrb_state *mrb = (mrb_state *)(intptr_t)mrbPtr;
    mrb_full_gc(mrb);
    LOGD("Full GC completed");
}

#ifdef __cplusplus
}
#endif
