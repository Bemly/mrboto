/*
 * jni-registry.c — Global JNI reference registry and JavaObject wrapping
 *
 * Provides:
 *   - GlobalRef registry (4096-slot table)
 *   - JavaVM caching
 *   - JavaObject mruby Data type
 */

#include <jni.h>
#include <android/log.h>
#include <stdlib.h>
#include <mruby.h>
#include <mruby/data.h>
#include <mruby/string.h>

#include "android-jni-bridge.h"

#define LOG_TAG "mrboto-registry"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

/* ── Global State ─────────────────────────────────────────────────── */

static JavaVM *g_java_vm = NULL;
struct RClass *g_java_object_class = NULL;
static mrboto_ref_registry_t g_registry = { {0}, {0}, 1 };

mrboto_ref_registry_t *mrboto_registry(void) { return &g_registry; }
JavaVM *mrboto_get_java_vm(void) { return g_java_vm; }

JNIEnv *mrboto_get_env(void) {
    if (g_java_vm == NULL) return NULL;
    JNIEnv *env = NULL;
    (*g_java_vm)->GetEnv(g_java_vm, (void **)&env, JNI_VERSION_1_6);
    return env;
}

/* ── JNI_OnLoad ───────────────────────────────────────────────────── */

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    (void)reserved;
    g_java_vm = vm;
    LOGI("JNI_OnLoad: JavaVM cached");
    return JNI_VERSION_1_6;
}

/* ── Reference Registry ───────────────────────────────────────────── */

int mrboto_register_ref(JNIEnv *env, jobject obj) {
    if (obj == NULL) return 0;

    mrboto_ref_registry_t *r = &g_registry;
    for (int i = 1; i < MRBOTO_REGISTRY_MAX; i++) {
        if (!r->used[i]) {
            r->refs[i] = (*env)->NewGlobalRef(env, obj);
            r->used[i] = 1;
            if (i >= r->next_id) r->next_id = i + 1;
            return i;
        }
    }
    LOGE("Registry full (max %d)", MRBOTO_REGISTRY_MAX);
    return 0;
}

jobject mrboto_lookup_ref(JNIEnv *env, int id) {
    (void)env;
    if (id <= 0 || id >= MRBOTO_REGISTRY_MAX || !g_registry.used[id]) {
        LOGE("Invalid registry ID: %d", id);
        return NULL;
    }
    return g_registry.refs[id];
}

void mrboto_unregister_ref(JNIEnv *env, int id) {
    if (id <= 0 || id >= MRBOTO_REGISTRY_MAX || !g_registry.used[id]) return;
    (*env)->DeleteGlobalRef(env, g_registry.refs[id]);
    g_registry.refs[id] = NULL;
    g_registry.used[id] = 0;
}

/* Clear ALL used registry entries. Called between test runs to prevent
 * GlobalRef table bloat from causing memory pressure and test hangs. */
void mrboto_clear_registry(JNIEnv *env) {
    for (int i = 1; i < MRBOTO_REGISTRY_MAX; i++) {
        if (g_registry.used[i]) {
            (*env)->DeleteGlobalRef(env, g_registry.refs[i]);
            g_registry.refs[i] = NULL;
            g_registry.used[i] = 0;
        }
    }
    g_registry.next_id = 1;
}
/* ── mruby JavaObject Data Type ───────────────────────────────────── */

static const mrb_data_type mrboto_java_object_type = {
    "Mrboto::JavaObject", mrb_free
};


mrb_value mrboto_wrap_java_object(mrb_state *mrb, JNIEnv *env, jobject obj) {
    int id = mrboto_register_ref(env, obj);
    if (id == 0) return mrb_nil_value();

    mrboto_data_t *data = (mrboto_data_t *)mrb_malloc(mrb, sizeof(mrboto_data_t));
    data->registry_id = id;

    /* Wrap as Ruby Data object */
    struct RData *rdata = mrb_data_object_alloc(mrb, g_java_object_class, data, &mrboto_java_object_type);
    mrb_value val = mrb_obj_value(rdata);

    /* Set @_registry_id instance variable so Ruby attr_reader works.
     * JavaObject.call_java_method reads @_registry_id, but the C Data
     * struct stores registry_id separately. Without this iv_set, wrappers
     * created by mrboto_wrap_java_object (e.g. from call_java_method return
     * values) have nil @_registry_id, causing "nil cannot be converted to
     * Integer" when chaining calls like settings.call_java_method(...). */
    mrb_iv_set(mrb, val, mrb_intern_lit(mrb, "@_registry_id"), mrb_fixnum_value(id));

    return val;
}

jobject mrboto_unwrap_java_object(mrb_state *mrb, mrb_value val, JNIEnv **out_env) {
    JNIEnv *env = mrboto_get_env();
    if (out_env) *out_env = env;
    if (mrb_nil_p(val)) return NULL;

    mrboto_data_t *data = (mrboto_data_t *)mrb_data_get_ptr(mrb, val, &mrboto_java_object_type);
    if (data == NULL) {
        LOGE("Expected JavaObject, got different type");
        return NULL;
    }
    return mrboto_lookup_ref(env, data->registry_id);
}

int mrboto_register_java_object(mrb_state *mrb, JNIEnv *env, jobject obj) {
    mrb_value java_obj = mrboto_wrap_java_object(mrb, env, obj);
    if (mrb_nil_p(java_obj)) return 0;

    mrboto_data_t *data = (mrboto_data_t *)DATA_PTR(java_obj);
    return data->registry_id;
}

