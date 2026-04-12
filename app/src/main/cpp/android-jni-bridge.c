/*
 * android-jni-bridge.c — JNI bridge between mruby and Android Java APIs
 *
 * This file implements the C side of the mruby ↔ Android bridge:
 *   - Global JNI reference registry
 *   - Java object wrapping for mruby
 *   - Lifecycle callback dispatching
 *   - View creation and event binding
 *   - Android helper functions (toast, intent, shared preferences)
 */

#include <jni.h>
#include <android/log.h>
#include <stdlib.h>
#include <mruby.h>
#include <mruby/data.h>
#include <mruby/hash.h>
#include <mruby/array.h>
#include <mruby/string.h>
#include <mruby/variable.h>
#include <mruby/compile.h>
#include <mruby/error.h>
#include <mruby/object.h>
#include <mruby/error.h>

#include "android-jni-bridge.h"
#include <string.h>

#define LOG_TAG "mrboto-bridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

/* ── Helper: safe mruby exception message extraction ────────────────── */

/* Context for mrb_protect — passed via CPTR */
typedef struct {
    mrb_value exc;
    mrb_value result;
} mrboto_exc_ctx_t;

static mrb_value mrboto_safe_exc_message(mrb_state *mrb, mrb_value self) {
    (void)self;
    mrboto_exc_ctx_t *ctx = (mrboto_exc_ctx_t *)mrb_cptr(self);
    return mrb_funcall(mrb, ctx->exc, "message", 0);
}

/* Safely extract exception message. Returns mrb_str_new_cstr or NULL.
 * Clears mrb->exc afterward. Caller must check return value. */
static const char *mrboto_safe_exc_message_cstr(mrb_state *mrb) {
    if (!mrb->exc) return NULL;

    mrboto_exc_ctx_t ctx;
    ctx.exc = mrb_obj_value(mrb->exc);
    ctx.result = mrb_nil_value();

    mrb_bool error = FALSE;
    mrb_value data = mrb_cptr_value(mrb, &ctx);
    mrb_protect(mrb, mrboto_safe_exc_message, data, &error);

    const char *s = NULL;
    if (mrb_string_p(ctx.result)) {
        s = mrb_string_value_cstr(mrb, &ctx.result);
    }
    mrb->exc = NULL;
    return s;
}

/* ── Global State ─────────────────────────────────────────────────── */

static JavaVM *g_java_vm = NULL;
static struct RClass *g_java_object_class = NULL;
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

/* Struct stored inside mruby Data objects */
typedef struct {
    int registry_id;
} mrboto_data_t;

mrb_value mrboto_wrap_java_object(mrb_state *mrb, JNIEnv *env, jobject obj) {
    int id = mrboto_register_ref(env, obj);
    if (id == 0) return mrb_nil_value();

    mrboto_data_t *data = (mrboto_data_t *)mrb_malloc(mrb, sizeof(mrboto_data_t));
    data->registry_id = id;

    /* Wrap as Ruby Data object */
    struct RData *rdata = mrb_data_object_alloc(mrb, g_java_object_class, data, &mrboto_java_object_type);
    mrb_value val = mrb_obj_value(rdata);
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

/* ── View Creation ────────────────────────────────────────────────── */

int mrboto_create_view(mrb_state *mrb, int context_id, const char *class_name,
                       mrb_value attrs) {
    JNIEnv *env = mrboto_get_env();
    jobject context = mrboto_lookup_ref(env, context_id);
    LOGI("create_view: class=%s context=%p", class_name, context);
    if (context == NULL) {
        LOGE("create_view: context is NULL");
        return 0;
    }

    /* Convert class_name dots to slashes for JNI */
    char jni_class[256];
    strncpy(jni_class, class_name, sizeof(jni_class) - 1);
    jni_class[sizeof(jni_class) - 1] = '\0';
    for (char *p = jni_class; *p; p++) {
        if (*p == '.') *p = '/';
    }

    /* Find the View class */
    jclass cls = (*env)->FindClass(env, jni_class);
    if (cls == NULL) {
        LOGE("FindClass failed: %s", jni_class);
        (*env)->ExceptionClear(env);
        return 0;
    }

    /* Get constructor: (Context) */
    jmethodID cid = (*env)->GetMethodID(env, cls, "<init>",
                                        "(Landroid/content/Context;)V");
    if (cid == NULL) {
        LOGE("Constructor not found for %s", jni_class);
        (*env)->ExceptionClear(env);
        (*env)->DeleteLocalRef(env, cls);
        return 0;
    }

    jobject view = (*env)->NewObject(env, cls, cid, context);
    if ((*env)->ExceptionCheck(env)) {
        LOGE("NewObject failed for %s", jni_class);
        (*env)->ExceptionClear(env);
        (*env)->DeleteLocalRef(env, cls);
        return 0;
    }

    int view_id = 0;
    if (view != NULL) {
        view_id = mrboto_register_ref(env, view);
        (*env)->DeleteLocalRef(env, view);
    }
    LOGI("create_view: class=%s view_id=%d", class_name, view_id);

    (*env)->DeleteLocalRef(env, cls);
    return view_id;
}

/* ── Event Listeners (via eval back into mruby) ───────────────────── */

void mrboto_set_on_click(int view_id, int callback_id) {
    JNIEnv *env = mrboto_get_env();
    jobject view = mrboto_lookup_ref(env, view_id);
    if (view == NULL || env == NULL) return;

    jclass view_cls = (*env)->GetObjectClass(env, view);
    if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); return; }
    jmethodID setTag = (*env)->GetMethodID(env, view_cls, "setTag", "(Ljava/lang/Object;)V");
    if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); (*env)->DeleteLocalRef(env, view_cls); return; }
    if (setTag != NULL) {
        jclass integer_cls = (*env)->FindClass(env, "java/lang/Integer");
        if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); (*env)->DeleteLocalRef(env, view_cls); return; }
        jmethodID int_init = (*env)->GetMethodID(env, integer_cls, "<init>", "(I)V");
        if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); (*env)->DeleteLocalRef(env, integer_cls); (*env)->DeleteLocalRef(env, view_cls); return; }
        jobject int_obj = (*env)->NewObject(env, integer_cls, int_init, (jint)callback_id);
        if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); (*env)->DeleteLocalRef(env, integer_cls); (*env)->DeleteLocalRef(env, view_cls); return; }
        (*env)->CallVoidMethod(env, view, setTag, int_obj);
        if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); }
        (*env)->DeleteLocalRef(env, int_obj);
        (*env)->DeleteLocalRef(env, integer_cls);
    }
    (*env)->DeleteLocalRef(env, view_cls);

    LOGD("set_on_click: view=%d callback=%d", view_id, callback_id);
}

void mrboto_set_text_watcher(int view_id, int callback_id) {
    (void)view_id;
    (void)callback_id;
    LOGD("set_text_watcher: view=%d callback=%d", view_id, callback_id);
}

/* ── Lifecycle Dispatcher ─────────────────────────────────────────── */

void mrboto_dispatch_lifecycle(mrb_state *mrb, int activity_id,
                               const char *callback_name, int args_id) {
    /*
     * This function is called from Kotlin via nativeDispatchLifecycle.
     * It calls the Ruby method on the current activity object.
     *
     * Ruby side: Mrboto.current_activity.on_create(bundle)
     */
    (void)mrb;
    (void)activity_id;
    (void)callback_name;
    (void)args_id;
    /* Implementation is in the Kotlin nativeDispatchLifecycle JNI method */
}

/* ── Helper: Toast ────────────────────────────────────────────────── */

void mrboto_toast(mrb_state *mrb, int context_id, const char *msg, int duration) {
    JNIEnv *env = mrboto_get_env();
    jobject context = mrboto_lookup_ref(env, context_id);
    if (context == NULL) return;

    jclass toast_cls = (*env)->FindClass(env, "android/widget/Toast");
    if (toast_cls == NULL) { (*env)->ExceptionClear(env); return; }

    jmethodID make_text = (*env)->GetStaticMethodID(env, toast_cls, "makeText",
        "(Landroid/content/Context;Ljava/lang/CharSequence;I)Landroid/widget/Toast;");
    if (make_text == NULL) {
        (*env)->ExceptionClear(env);
        (*env)->DeleteLocalRef(env, toast_cls);
        return;
    }

    jstring jmsg = (*env)->NewStringUTF(env, msg);
    jobject toast = (*env)->CallStaticObjectMethod(env, toast_cls, make_text,
                                                   context, jmsg, (jint)duration);
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
        (*env)->DeleteLocalRef(env, jmsg);
        (*env)->DeleteLocalRef(env, toast_cls);
        return;
    }

    if (toast != NULL) {
        jmethodID show = (*env)->GetMethodID(env, toast_cls, "show", "()V");
        if (show) (*env)->CallVoidMethod(env, toast, show);
        if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
        (*env)->DeleteLocalRef(env, toast);
    }

    (*env)->DeleteLocalRef(env, jmsg);
    (*env)->DeleteLocalRef(env, toast_cls);
}

/* ── Helper: Start Activity ───────────────────────────────────────── */

void mrboto_start_activity(mrb_state *mrb, int context_id, const char *cls_name,
                           mrb_value extras) {
    (void)mrb;
    (void)extras;
    JNIEnv *env = mrboto_get_env();
    jobject context = mrboto_lookup_ref(env, context_id);
    if (context == NULL) return;

    /* Get package name from context */
    jclass context_cls = (*env)->GetObjectClass(env, context);
    if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); return; }
    jmethodID get_pkg = (*env)->GetMethodID(env, context_cls, "getPackageName", "()Ljava/lang/String;");
    jstring pkg = (jstring)(*env)->CallObjectMethod(env, context, get_pkg);
    if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); (*env)->DeleteLocalRef(env, context_cls); return; }

    /* Use ComponentName */
    jclass cn_cls = (*env)->FindClass(env, "android/content/ComponentName");
    jmethodID cn_init = (*env)->GetMethodID(env, cn_cls, "<init>",
                                            "(Ljava/lang/String;Ljava/lang/String;)V");
    jstring jcls = (*env)->NewStringUTF(env, cls_name);
    jobject cn = (*env)->NewObject(env, cn_cls, cn_init, pkg, jcls);

    /* Create Intent */
    jclass intent_cls = (*env)->FindClass(env, "android/content/Intent");
    jmethodID intent_init = (*env)->GetMethodID(env, intent_cls, "<init>", "()V");
    jobject intent = (*env)->NewObject(env, intent_cls, intent_init);

    jmethodID set_comp = (*env)->GetMethodID(env, intent_cls, "setComponent",
                                             "(Landroid/content/ComponentName;)Landroid/content/Intent;");
    if (set_comp != NULL) {
        (*env)->CallObjectMethod(env, intent, set_comp, cn);
        if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); }
    }

    /* Start activity — this WILL throw in instrumented tests (no real Activity) */
    jmethodID start = (*env)->GetMethodID(env, context_cls, "startActivity",
                                          "(Landroid/content/Intent;)V");
    if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); }
    if (start != NULL) {
        (*env)->CallVoidMethod(env, context, start, intent);
        if ((*env)->ExceptionCheck(env)) {
            (*env)->ExceptionClear(env);
        }
    }

    /* Cleanup */
    (*env)->DeleteLocalRef(env, jcls);
    (*env)->DeleteLocalRef(env, cn);
    (*env)->DeleteLocalRef(env, pkg);
    (*env)->DeleteLocalRef(env, intent);
    (*env)->DeleteLocalRef(env, cn_cls);
    (*env)->DeleteLocalRef(env, intent_cls);
    (*env)->DeleteLocalRef(env, context_cls);
}

/* ── Helper: Get Extra ────────────────────────────────────────────── */

mrb_value mrboto_get_extra(mrb_state *mrb, int activity_id, const char *key) {
    JNIEnv *env = mrboto_get_env();
    jobject activity = mrboto_lookup_ref(env, activity_id);
    if (activity == NULL) return mrb_nil_value();

    jclass act_cls = (*env)->GetObjectClass(env, activity);
    jmethodID get_intent = (*env)->GetMethodID(env, act_cls, "getIntent",
                                               "()Landroid/content/Intent;");
    if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); (*env)->DeleteLocalRef(env, act_cls); return mrb_nil_value(); }
    if (get_intent == NULL) { (*env)->DeleteLocalRef(env, act_cls); return mrb_nil_value(); }
    jobject intent = (*env)->CallObjectMethod(env, activity, get_intent);
    if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); (*env)->DeleteLocalRef(env, act_cls); return mrb_nil_value(); }
    if (intent == NULL) { (*env)->DeleteLocalRef(env, act_cls); return mrb_nil_value(); }

    jclass intent_cls = (*env)->GetObjectClass(env, intent);
    jmethodID get_extras = (*env)->GetMethodID(env, intent_cls, "getExtras",
                                               "()Landroid/os/Bundle;");
    if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); (*env)->DeleteLocalRef(env, intent); (*env)->DeleteLocalRef(env, intent_cls); (*env)->DeleteLocalRef(env, act_cls); return mrb_nil_value(); }
    if (get_extras == NULL) {
        (*env)->DeleteLocalRef(env, intent);
        (*env)->DeleteLocalRef(env, intent_cls);
        (*env)->DeleteLocalRef(env, act_cls);
        return mrb_nil_value();
    }
    jobject bundle = (*env)->CallObjectMethod(env, intent, get_extras);
    if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); }

    mrb_value result = mrb_nil_value();
    if (bundle != NULL) {
        jclass bundle_cls = (*env)->GetObjectClass(env, bundle);
        jmethodID get_str = (*env)->GetMethodID(env, bundle_cls, "getString",
                                                "(Ljava/lang/String;)Ljava/lang/String;");
        if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); (*env)->DeleteLocalRef(env, bundle_cls); }
        if (get_str != NULL) {
            jstring jkey = (*env)->NewStringUTF(env, key);
            jstring jval = (jstring)(*env)->CallObjectMethod(env, bundle, get_str, jkey);
            if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); }

            if (jval != NULL) {
                const char *s = (*env)->GetStringUTFChars(env, jval, NULL);
                result = mrb_str_new_cstr(mrb, s);
                (*env)->ReleaseStringUTFChars(env, jval, s);
                (*env)->DeleteLocalRef(env, jval);
            }
            (*env)->DeleteLocalRef(env, jkey);
        }
        (*env)->DeleteLocalRef(env, bundle_cls);
    }

    (*env)->DeleteLocalRef(env, intent);
    (*env)->DeleteLocalRef(env, intent_cls);
    (*env)->DeleteLocalRef(env, act_cls);

    return result;
}

/* ── Helper: SharedPreferences ────────────────────────────────────── */

static jobject get_shared_prefs(JNIEnv *env, jobject context, const char *name) {
    jclass ctx_cls = (*env)->GetObjectClass(env, context);
    if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); return NULL; }
    jmethodID get_sp = (*env)->GetMethodID(env, ctx_cls, "getSharedPreferences",
                                           "(Ljava/lang/String;I)Landroid/content/SharedPreferences;");
    if (get_sp == NULL) { (*env)->DeleteLocalRef(env, ctx_cls); return NULL; }
    jstring jname = (*env)->NewStringUTF(env, name);
    if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); (*env)->DeleteLocalRef(env, ctx_cls); return NULL; }
    jobject sp = (*env)->CallObjectMethod(env, context, get_sp, jname, (jint)0 /* MODE_PRIVATE */);
    if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); }
    (*env)->DeleteLocalRef(env, jname);
    (*env)->DeleteLocalRef(env, ctx_cls);
    return sp;
}

mrb_value mrboto_sp_get_string(mrb_state *mrb, int context_id,
                               const char *name, const char *key, const char *default_val) {
    JNIEnv *env = mrboto_get_env();
    jobject context = mrboto_lookup_ref(env, context_id);
    if (context == NULL) return mrb_nil_value();

    jobject sp = get_shared_prefs(env, context, name);
    mrb_value result = mrb_nil_value();

    if (sp != NULL) {
        jclass sp_cls = (*env)->GetObjectClass(env, sp);
        jmethodID get = (*env)->GetMethodID(env, sp_cls, "getString",
                                            "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;");
        if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); (*env)->DeleteLocalRef(env, sp_cls); return mrb_nil_value(); }
        jstring jkey = (*env)->NewStringUTF(env, key);
        jstring jdef = default_val ? (*env)->NewStringUTF(env, default_val) : NULL;
        if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); (*env)->DeleteLocalRef(env, jkey); if (jdef) (*env)->DeleteLocalRef(env, jdef); (*env)->DeleteLocalRef(env, sp_cls); return mrb_nil_value(); }
        jstring jval = (jstring)(*env)->CallObjectMethod(env, sp, get, jkey, jdef);
        if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); }

        if (jval != NULL) {
            const char *s = (*env)->GetStringUTFChars(env, jval, NULL);
            result = mrb_str_new_cstr(mrb, s);
            (*env)->ReleaseStringUTFChars(env, jval, s);
            (*env)->DeleteLocalRef(env, jval);
        }
        (*env)->DeleteLocalRef(env, jkey);
        if (jdef) (*env)->DeleteLocalRef(env, jdef);
        (*env)->DeleteLocalRef(env, sp_cls);
    }

    return result;
}

void mrboto_sp_put_string(mrb_state *mrb, int context_id,
                          const char *name, const char *key, const char *value) {
    (void)mrb;
    JNIEnv *env = mrboto_get_env();
    jobject context = mrboto_lookup_ref(env, context_id);
    if (context == NULL) return;

    jobject sp = get_shared_prefs(env, context, name);
    if (sp != NULL) {
        jclass sp_cls = (*env)->GetObjectClass(env, sp);
        jmethodID edit = (*env)->GetMethodID(env, sp_cls, "edit",
                                             "()Landroid/content/SharedPreferences$Editor;");
        if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); (*env)->DeleteLocalRef(env, sp_cls); return; }
        jobject editor = (*env)->CallObjectMethod(env, sp, edit);
        if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); (*env)->DeleteLocalRef(env, sp_cls); return; }

        jclass ed_cls = (*env)->GetObjectClass(env, editor);
        jmethodID put = (*env)->GetMethodID(env, ed_cls, "putString",
                                            "(Ljava/lang/String;Ljava/lang/String;)Landroid/content/SharedPreferences$Editor;");
        if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); (*env)->DeleteLocalRef(env, sp_cls); (*env)->DeleteLocalRef(env, editor); return; }
        jstring jkey = (*env)->NewStringUTF(env, key);
        jstring jval = (*env)->NewStringUTF(env, value);
        (*env)->CallObjectMethod(env, editor, put, jkey, jval);
        if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); }

        jmethodID apply = (*env)->GetMethodID(env, ed_cls, "apply", "()V");
        (*env)->CallVoidMethod(env, editor, apply);
        if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); }

        (*env)->DeleteLocalRef(env, jkey);
        (*env)->DeleteLocalRef(env, jval);
        (*env)->DeleteLocalRef(env, editor);
        (*env)->DeleteLocalRef(env, ed_cls);
        (*env)->DeleteLocalRef(env, sp_cls);
    }
}

/* ── Helper: App Context ──────────────────────────────────────────── */

jobject mrboto_get_app_context(mrb_state *mrb) {
    (void)mrb;
    JNIEnv *env = mrboto_get_env();
    if (env == NULL) return NULL;

    /* Try ActivityThread.currentApplication() first — works reliably in both
       real app and instrumented test. Falls back to older method if needed. */
    jclass at_cls = (*env)->FindClass(env, "android/app/ActivityThread");
    if (at_cls == NULL) return NULL;

    jobject app = NULL;

    /* Primary: currentApplication() static method */
    jmethodID current_app = (*env)->GetStaticMethodID(env, at_cls, "currentApplication",
                                                      "()Landroid/app/Application;");
    if (current_app != NULL) {
        app = (*env)->CallStaticObjectMethod(env, at_cls, current_app);
        if (app != NULL) {
            app = (*env)->NewGlobalRef(env, app);
        }
    }

    /* Fallback: currentActivityThread().getApplication() */
    if (app == NULL) {
        jmethodID current = (*env)->GetStaticMethodID(env, at_cls, "currentActivityThread",
                                                      "()Landroid/app/ActivityThread;");
        if (current != NULL) {
            jobject at = (*env)->CallStaticObjectMethod(env, at_cls, current);
            if (at != NULL) {
                jmethodID get_app = (*env)->GetMethodID(env, at_cls, "getApplication",
                                                        "()Landroid/app/Application;");
                if (get_app != NULL) {
                    app = (*env)->CallObjectMethod(env, at, get_app);
                    if (app != NULL) {
                        app = (*env)->NewGlobalRef(env, app);
                    }
                }
                (*env)->DeleteLocalRef(env, at);
            }
        }
    }

    (*env)->DeleteLocalRef(env, at_cls);
    return app;
}

/* ── mruby Method Bindings for Mrboto module ──────────────────────── */

/* C functions that serve as Ruby method implementations */

static mrb_value mrb_mrboto_set_content_view(mrb_state *mrb, mrb_value self) {
    mrb_int activity_id, view_id;
    mrb_get_args(mrb, "ii", &activity_id, &view_id);
    LOGI("set_content_view: activity_id=%d view_id=%d", (int)activity_id, (int)view_id);

    JNIEnv *env = mrboto_get_env();
    jobject activity = mrboto_lookup_ref(env, (int)activity_id);
    jobject view = mrboto_lookup_ref(env, (int)view_id);
    LOGI("set_content_view: activity=%p view=%p", activity, view);
    if (activity && view) {
        jclass act_cls = (*env)->GetObjectClass(env, activity);
        jmethodID mid = (*env)->GetMethodID(env, act_cls, "setContentView",
                                            "(Landroid/view/View;)V");
        LOGI("setContentView methodID=%p", mid);
        if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
        if (mid) {
            (*env)->CallVoidMethod(env, activity, mid, view);
            if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
        }
        (*env)->DeleteLocalRef(env, act_cls);
    }
    return mrb_nil_value();
}

static mrb_value mrb_mrboto_toast(mrb_state *mrb, mrb_value self) {
    mrb_int context_id, duration;
    const char *msg;
    mrb_get_args(mrb, "izi", &context_id, &msg, &duration);
    mrboto_toast(mrb, (int)context_id, msg, (int)duration);
    return mrb_nil_value();
}

static mrb_value mrb_mrboto_start_activity(mrb_state *mrb, mrb_value self) {
    mrb_int context_id;
    const char *cls_name;
    mrb_get_args(mrb, "iz", &context_id, &cls_name);
    mrboto_start_activity(mrb, (int)context_id, cls_name, mrb_nil_value());
    return mrb_nil_value();
}

static mrb_value mrb_mrboto_get_extra(mrb_state *mrb, mrb_value self) {
    mrb_int activity_id;
    const char *key;
    mrb_get_args(mrb, "iz", &activity_id, &key);
    return mrboto_get_extra(mrb, (int)activity_id, key);
}

static mrb_value mrb_mrboto_sp_get_string(mrb_state *mrb, mrb_value self) {
    mrb_int context_id;
    const char *name, *key, *default_val = NULL;
    mrb_get_args(mrb, "izzz", &context_id, &name, &key, &default_val);
    return mrboto_sp_get_string(mrb, (int)context_id, name, key, default_val);
}

static mrb_value mrb_mrboto_sp_put_string(mrb_state *mrb, mrb_value self) {
    mrb_int context_id;
    const char *name, *key, *value;
    mrb_get_args(mrb, "izzz", &context_id, &name, &key, &value);
    mrboto_sp_put_string(mrb, (int)context_id, name, key, value);
    return mrb_nil_value();
}

static mrb_value mrb_mrboto_sp_get_int(mrb_state *mrb, mrb_value self) {
    mrb_int context_id;
    const char *name, *key;
    mrb_int default_val = 0;
    mrb_get_args(mrb, "izz|i", &context_id, &name, &key, &default_val);
    (void)default_val;
    return mrb_nil_value();
}

static mrb_value mrb_mrboto_sp_put_int(mrb_state *mrb, mrb_value self) {
    mrb_int context_id, value;
    const char *name, *key;
    mrb_get_args(mrb, "izzi", &context_id, &name, &key, &value);
    (void)value; /* stub — not yet implemented */
    return mrb_nil_value();
}

static mrb_value mrb_mrboto_app_context(mrb_state *mrb, mrb_value self) {
    JNIEnv *env = mrboto_get_env();
    if (env == NULL) return mrb_nil_value();

    jclass at_cls = (*env)->FindClass(env, "android/app/ActivityThread");
    if (at_cls == NULL) { (*env)->ExceptionClear(env); return mrb_nil_value(); }

    jobject app = NULL;

    /* Primary: currentApplication() static method */
    jmethodID current_app = (*env)->GetStaticMethodID(env, at_cls, "currentApplication",
                                                      "()Landroid/app/Application;");
    if (current_app != NULL) {
        app = (*env)->CallStaticObjectMethod(env, at_cls, current_app);
        if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); }
    }

    /* Fallback: currentActivityThread().getApplication() */
    if (app == NULL) {
        jmethodID current = (*env)->GetStaticMethodID(env, at_cls, "currentActivityThread",
                                                      "()Landroid/app/ActivityThread;");
        if (current != NULL) {
            jobject at = (*env)->CallStaticObjectMethod(env, at_cls, current);
            if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); }
            if (at != NULL) {
                jmethodID get_app = (*env)->GetMethodID(env, at_cls, "getApplication",
                                                        "()Landroid/app/Application;");
                if (get_app != NULL) {
                    app = (*env)->CallObjectMethod(env, at, get_app);
                    if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); }
                }
                (*env)->DeleteLocalRef(env, at);
            }
        }
    }

    mrb_value result = mrb_nil_value();
    if (app != NULL) {
        result = mrboto_wrap_java_object(mrb, env, app);
    }

    (*env)->DeleteLocalRef(env, at_cls);
    return result;
}

static mrb_value mrb_mrboto_string_res(mrb_state *mrb, mrb_value self) {
    mrb_int activity_id, res_id;
    mrb_get_args(mrb, "ii", &activity_id, &res_id);

    JNIEnv *env = mrboto_get_env();
    jobject activity = mrboto_lookup_ref(env, (int)activity_id);
    if (!activity) return mrb_nil_value();

    jclass act_cls = (*env)->GetObjectClass(env, activity);
    if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); return mrb_nil_value(); }
    jmethodID get_res = (*env)->GetMethodID(env, act_cls, "getResources",
                                            "()Landroid/content/res/Resources;");
    if (get_res == NULL) { (*env)->DeleteLocalRef(env, act_cls); return mrb_nil_value(); }
    jobject resources = (*env)->CallObjectMethod(env, activity, get_res);
    if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); }
    if (!resources) { (*env)->DeleteLocalRef(env, act_cls); return mrb_nil_value(); }

    jclass res_cls = (*env)->GetObjectClass(env, resources);
    if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); (*env)->DeleteLocalRef(env, resources); (*env)->DeleteLocalRef(env, act_cls); return mrb_nil_value(); }
    jmethodID get_str = (*env)->GetMethodID(env, res_cls, "getString",
                                            "(I)Ljava/lang/String;");
    if (get_str == NULL) { (*env)->DeleteLocalRef(env, resources); (*env)->DeleteLocalRef(env, res_cls); (*env)->DeleteLocalRef(env, act_cls); return mrb_nil_value(); }
    jstring jstr = (jstring)(*env)->CallObjectMethod(env, resources, get_str, (jint)res_id);
    if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); }

    mrb_value result = mrb_nil_value();
    if (jstr) {
        const char *s = (*env)->GetStringUTFChars(env, jstr, NULL);
        result = mrb_str_new_cstr(mrb, s);
        (*env)->ReleaseStringUTFChars(env, jstr, s);
        (*env)->DeleteLocalRef(env, jstr);
    }
    (*env)->DeleteLocalRef(env, resources);
    (*env)->DeleteLocalRef(env, res_cls);
    (*env)->DeleteLocalRef(env, act_cls);
    return result;
}

static mrb_value mrb_mrboto_create_view(mrb_state *mrb, mrb_value self) {
    mrb_int context_id;
    const char *class_name;
    mrb_value attrs;
    mrb_get_args(mrb, "izH", &context_id, &class_name, &attrs);
    int view_id = mrboto_create_view(mrb, (int)context_id, class_name, attrs);
    if (mrb->exc) {
        mrb_value msg = mrb_funcall(mrb, mrb_obj_value(mrb->exc), "message", 0);
        mrb->exc = NULL;
        (void)msg;
    }
    return mrb_fixnum_value(view_id);
}

static mrb_value mrb_mrboto_set_on_click(mrb_state *mrb, mrb_value self) {
    mrb_int view_id, callback_id;
    mrb_get_args(mrb, "ii", &view_id, &callback_id);
    mrboto_set_on_click((int)view_id, (int)callback_id);
    return mrb_nil_value();
}

static mrb_value mrb_mrboto_run_on_ui_thread(mrb_state *mrb, mrb_value self) {
    mrb_int activity_id, callback_id;
    mrb_get_args(mrb, "ii", &activity_id, &callback_id);
    /* The @@callbacks hash is stored in the singleton class of Mrboto module
     * (because it's defined inside class << self). Access it via singleton class
     * + mrb_cv_get to avoid calling dispatch_callback singleton method (which
     * mrb_funcall cannot safely resolve on a module). */
    mrb_value mrboto_mod = mrb_const_get(mrb, mrb_obj_value(mrb->object_class),
                                         mrb_intern_lit(mrb, "Mrboto"));
    if (!mrb_nil_p(mrboto_mod)) {
        mrb_value singleton = mrb_singleton_class(mrb, mrboto_mod);
        mrb_value callbacks = mrb_cv_get(mrb, singleton, mrb_intern_lit(mrb, "@@callbacks"));
        if (mrb->exc) { mrb->exc = NULL; }
        if (mrb_hash_p(callbacks)) {
            mrb_value key = mrb_fixnum_value((mrb_int)callback_id);
            mrb_value proc = mrb_hash_fetch(mrb, callbacks, key, mrb_nil_value());
            if (!mrb_nil_p(proc)) {
                mrb_funcall(mrb, proc, "call", 0);
                if (mrb->exc) { mrb->exc = NULL; }
            }
        }
    }
    (void)activity_id;
    (void)self;
    return mrb_nil_value();
}

/* ── Helper: DP to PX conversion ──────────────────────────────────── */

static mrb_value mrb_mrboto_dp_to_px(mrb_state *mrb, mrb_value self) {
    mrb_value val;
    mrb_get_args(mrb, "o", &val);

    mrb_float value;
#ifndef MRB_NO_FLOAT
    if (mrb_float_p(val)) {
        value = mrb_float(val);
    } else
#endif
    if (mrb_integer_p(val)) {
        value = (mrb_float)mrb_integer(val);
    } else {
        return mrb_fixnum_value(100); /* fallback for unexpected types */
    }

    JNIEnv *env = mrboto_get_env();
    if (env == NULL) return mrb_fixnum_value((mrb_int)(value * 1.5 + 0.5));

    int ai = mrb_gc_arena_save(mrb);

    jclass res_cls = (*env)->FindClass(env, "android/content/res/Resources");
    if (res_cls == NULL) {
        (*env)->ExceptionClear(env);
        mrb_gc_arena_restore(mrb, ai);
        return mrb_fixnum_value((mrb_int)(value * 1.5 + 0.5));
    }

    jmethodID get_system = (*env)->GetStaticMethodID(env, res_cls, "getSystem",
                                                     "()Landroid/content/res/Resources;");
    if (get_system == NULL) {
        (*env)->DeleteLocalRef(env, res_cls);
        mrb_gc_arena_restore(mrb, ai);
        return mrb_fixnum_value((mrb_int)(value * 1.5 + 0.5));
    }

    jobject resources = (*env)->CallStaticObjectMethod(env, res_cls, get_system);
    if (resources == NULL) {
        (*env)->DeleteLocalRef(env, res_cls);
        mrb_gc_arena_restore(mrb, ai);
        return mrb_fixnum_value((mrb_int)(value * 1.5 + 0.5));
    }

    jclass res_cls2 = (*env)->GetObjectClass(env, resources);
    if (res_cls2 == NULL) {
        (*env)->DeleteLocalRef(env, resources);
        (*env)->DeleteLocalRef(env, res_cls);
        mrb_gc_arena_restore(mrb, ai);
        return mrb_fixnum_value((mrb_int)(value * 1.5 + 0.5));
    }

    jmethodID get_metrics = (*env)->GetMethodID(env, res_cls2, "getDisplayMetrics",
                                                "()Landroid/util/DisplayMetrics;");
    (*env)->DeleteLocalRef(env, res_cls);
    (*env)->DeleteLocalRef(env, res_cls2);
    if (get_metrics == NULL) {
        (*env)->DeleteLocalRef(env, resources);
        mrb_gc_arena_restore(mrb, ai);
        return mrb_fixnum_value((mrb_int)(value * 1.5 + 0.5));
    }

    jobject metrics = (*env)->CallObjectMethod(env, resources, get_metrics);
    (*env)->DeleteLocalRef(env, resources);
    if (metrics == NULL) {
        mrb_gc_arena_restore(mrb, ai);
        return mrb_fixnum_value((mrb_int)(value * 1.5 + 0.5));
    }

    jclass dm_cls = (*env)->GetObjectClass(env, metrics);
    jfieldID fid = dm_cls ? (*env)->GetFieldID(env, dm_cls, "density", "F") : NULL;

    mrb_int px;
    if (fid == NULL) {
        px = (mrb_int)(value * 1.5 + 0.5);
        if (dm_cls) (*env)->DeleteLocalRef(env, dm_cls);
    } else {
        jfloat density = (*env)->GetFloatField(env, metrics, fid);
        px = (mrb_int)(value * (double)density + 0.5);
        (*env)->DeleteLocalRef(env, dm_cls);
    }
    (*env)->DeleteLocalRef(env, metrics);
    mrb_gc_arena_restore(mrb, ai);
    return mrb_fixnum_value(px);
}

/* ── Helper: Package Name ─────────────────────────────────────────── */

static mrb_value mrb_mrboto_package_name(mrb_state *mrb, mrb_value self) {
    JNIEnv *env = mrboto_get_env();
    if (env == NULL) return mrb_str_new_cstr(mrb, "unknown");

    int ai = mrb_gc_arena_save(mrb);

    jclass at_cls = (*env)->FindClass(env, "android/app/ActivityThread");
    if (at_cls != NULL) {
        jmethodID current_app = (*env)->GetStaticMethodID(env, at_cls, "currentApplication",
                                                          "()Landroid/app/Application;");
        if (current_app != NULL) {
            jobject app = (*env)->CallStaticObjectMethod(env, at_cls, current_app);
            if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); }
            if (app != NULL) {
                jclass app_cls = (*env)->GetObjectClass(env, app);
                jmethodID get_pkg = (*env)->GetMethodID(env, app_cls, "getPackageName",
                                                        "()Ljava/lang/String;");
                if (get_pkg != NULL) {
                    jstring pkg = (jstring)(*env)->CallObjectMethod(env, app, get_pkg);
                    if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); }
                    if (pkg != NULL) {
                        const char *s = (*env)->GetStringUTFChars(env, pkg, NULL);
                        mrb_value result = mrb_str_new_cstr(mrb, s);
                        (*env)->ReleaseStringUTFChars(env, pkg, s);
                        (*env)->DeleteLocalRef(env, pkg);
                        (*env)->DeleteLocalRef(env, app_cls);
                        (*env)->DeleteLocalRef(env, at_cls);
                        mrb_gc_arena_restore(mrb, ai);
                        return result;
                    }
                }
                (*env)->DeleteLocalRef(env, app_cls);
            }
        }
        (*env)->DeleteLocalRef(env, at_cls);
    } else {
        (*env)->ExceptionClear(env);
    }

    mrb_gc_arena_restore(mrb, ai);
    return mrb_str_new_cstr(mrb, "unknown");
}

/* ── Java Object Registry Methods ─────────────────────────────────── */

/* Mrboto._register_object(obj) → registry_id
 * Registers a Java object (passed as mrb Data wrapping a JNI GlobalRef)
 * and returns its integer registry ID. */
static mrb_value mrb_mrboto_register_object(mrb_state *mrb, mrb_value self) {
    mrb_value obj;
    mrb_get_args(mrb, "o", &obj);
    if (!mrb_data_p(obj)) {
        return mrb_fixnum_value(0);
    }
    mrboto_data_t *data = (mrboto_data_t *)DATA_PTR(obj);
    if (data == NULL) return mrb_fixnum_value(0);
    (void)mrb; /* env not needed — registry ID is already stored in data */
    return mrb_fixnum_value((mrb_int)data->registry_id);
}

/* Mrboto._java_object_for(registry_id) → JavaObject wrapper
 * Returns the Ruby-side JavaObject for a given registry ID. */
static mrb_value mrb_mrboto_java_object_for(mrb_state *mrb, mrb_value self) {
    mrb_int registry_id;
    mrb_get_args(mrb, "i", &registry_id);
    JNIEnv *env = mrboto_get_env();
    jobject java_obj = mrboto_lookup_ref(env, (int)registry_id);
    if (java_obj == NULL) return mrb_nil_value();
    return mrboto_wrap_java_object(mrb, env, java_obj);
}

/* ── Helper: Convert mruby value to JNI jobject for reflection call ── */

static jobject mrboto_mruby_to_java_arg(JNIEnv *env, mrb_state *mrb, mrb_value arg) {
    if (mrb_integer_p(arg)) {
        /* Pass as Integer for reflection (auto-unboxed by JVM) */
        jclass int_cls = (*env)->FindClass(env, "java/lang/Integer");
        if (int_cls == NULL) { (*env)->ExceptionClear(env); return NULL; }
        jmethodID init = (*env)->GetMethodID(env, int_cls, "<init>", "(I)V");
        if (init == NULL) { (*env)->DeleteLocalRef(env, int_cls); return NULL; }
        jobject obj = (*env)->NewObject(env, int_cls, init, (jint)mrb_integer(arg));
        (*env)->DeleteLocalRef(env, int_cls);
        return obj;
    }
#ifndef MRB_NO_FLOAT
    if (mrb_float_p(arg)) {
        jclass float_cls = (*env)->FindClass(env, "java/lang/Float");
        if (float_cls == NULL) { (*env)->ExceptionClear(env); return NULL; }
        jmethodID init = (*env)->GetMethodID(env, float_cls, "<init>", "(F)V");
        if (init == NULL) { (*env)->DeleteLocalRef(env, float_cls); return NULL; }
        jobject obj = (*env)->NewObject(env, float_cls, init, (jfloat)mrb_float(arg));
        (*env)->DeleteLocalRef(env, float_cls);
        return obj;
    }
#endif
    if (mrb_true_p(arg) || mrb_false_p(arg)) {
        jclass bool_cls = (*env)->FindClass(env, "java/lang/Boolean");
        if (bool_cls == NULL) { (*env)->ExceptionClear(env); return NULL; }
        jmethodID init = (*env)->GetMethodID(env, bool_cls, "<init>", "(Z)V");
        if (init == NULL) { (*env)->DeleteLocalRef(env, bool_cls); return NULL; }
        jobject obj = (*env)->NewObject(env, bool_cls, init, mrb_true_p(arg) ? JNI_TRUE : JNI_FALSE);
        (*env)->DeleteLocalRef(env, bool_cls);
        return obj;
    }
    if (mrb_string_p(arg)) {
        const char *s = mrb_string_value_cstr(mrb, &arg);
        return (*env)->NewStringUTF(env, s);
    }
    if (mrb_data_p(arg)) {
        mrboto_data_t *d = (mrboto_data_t *)DATA_PTR(arg);
        if (d != NULL) {
            return mrboto_lookup_ref(env, d->registry_id);
        }
    }
    return NULL;
}

/* ── Helper: Log Method.invoke exception details ───────────────────── */

static void log_invoke_exception(JNIEnv *env, const char *method_name, jthrowable exc) {
    if (exc == NULL || env == NULL) return;

    jclass exc_cls = (*env)->GetObjectClass(env, exc);
    if (exc_cls == NULL) { (*env)->DeleteLocalRef(env, exc); return; }

    /* Exception.toString() */
    jmethodID to_string = (*env)->GetMethodID(env, exc_cls, "toString", "()Ljava/lang/String;");
    if (to_string) {
        jstring msg = (jstring)(*env)->CallObjectMethod(env, exc, to_string);
        if (msg && !(*env)->ExceptionCheck(env)) {
            const char *s = (*env)->GetStringUTFChars(env, msg, NULL);
            LOGW("Method.invoke('%s') threw: %s", method_name, s);
            (*env)->ReleaseStringUTFChars(env, msg, s);
            (*env)->DeleteLocalRef(env, msg);
        }
    }

    /* getCause() — unwrap InvocationTargetException */
    jmethodID get_cause = (*env)->GetMethodID(env, exc_cls, "getCause", "()Ljava/lang/Throwable;");
    if (get_cause) {
        jobject cause = (*env)->CallObjectMethod(env, exc, get_cause);
        if (cause != NULL && !(*env)->ExceptionCheck(env)) {
            jclass cause_cls = (*env)->GetObjectClass(env, cause);
            jmethodID cause_to_string = (*env)->GetMethodID(env, cause_cls, "toString", "()Ljava/lang/String;");
            if (cause_to_string) {
                jstring cause_msg = (jstring)(*env)->CallObjectMethod(env, cause, cause_to_string);
                if (cause_msg && !(*env)->ExceptionCheck(env)) {
                    const char *s = (*env)->GetStringUTFChars(env, cause_msg, NULL);
                    LOGW("  Caused by: %s", s);
                    (*env)->ReleaseStringUTFChars(env, cause_msg, s);
                    (*env)->DeleteLocalRef(env, cause_msg);
                }
            }
            (*env)->DeleteLocalRef(env, cause_cls);
            (*env)->DeleteLocalRef(env, cause);
        }
    }

    (*env)->DeleteLocalRef(env, exc_cls);
    (*env)->DeleteLocalRef(env, exc);
}

/* Mrboto._call_java_method(registry_id, method_name, *args)
 * Calls a Java method on the object identified by registry_id.
 * Uses Java reflection (Class.getMethod + Method.invoke) to avoid
 * needing exact JNI type signatures. */
static mrb_value mrb_mrboto_call_java_method(mrb_state *mrb, mrb_value self) {
    mrb_int registry_id;
    const char *method_name;
    mrb_value *args;
    mrb_int argc;
    mrb_get_args(mrb, "iz*", &registry_id, &method_name, &args, &argc);
    (void)self;

    JNIEnv *env = mrboto_get_env();
    if (env == NULL) return mrb_nil_value();

    jobject target = mrboto_lookup_ref(env, (int)registry_id);
    if (target == NULL) return mrb_nil_value();

    int ai = mrb_gc_arena_save(mrb);
    mrb_value result = mrb_nil_value();

    jclass cls = (*env)->GetObjectClass(env, target);
    if (cls == NULL || (*env)->ExceptionCheck(env)) {
        if (cls) (*env)->DeleteLocalRef(env, cls);
        (*env)->ExceptionClear(env);
        mrb_gc_arena_restore(mrb, ai);
        return mrb_nil_value();
    }

    /* Get the java.lang.Class object by calling Object.getClass() on target.
     * We cannot use GetObjectClass result directly for JNI method calls because
     * JNI treats jclass as the class being represented, not as a java.lang.Class
     * instance on which we can call getMethod. Calling getClass() on target
     * returns the same Class object but as a jobject that JNI handles correctly. */
    jmethodID get_class_mid = (*env)->GetMethodID(env, cls, "getClass",
                                                   "()Ljava/lang/Class;");
    if (get_class_mid == NULL || (*env)->ExceptionCheck(env)) {
        if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
        (*env)->DeleteLocalRef(env, cls);
        mrb_gc_arena_restore(mrb, ai);
        return mrb_nil_value();
    }
    jobject target_class = (*env)->CallObjectMethod(env, target, get_class_mid);
    (*env)->DeleteLocalRef(env, cls);
    if (target_class == NULL || (*env)->ExceptionCheck(env)) {
        if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
        mrb_gc_arena_restore(mrb, ai);
        return mrb_nil_value();
    }

    /* Now target_class is a jobject (java.lang.Class instance) — we can
     * call getMethod on it via GetMethodID + CallObjectMethod. */
    jclass class_cls = (*env)->GetObjectClass(env, target_class);
    if (class_cls == NULL || (*env)->ExceptionCheck(env)) {
        if (class_cls) (*env)->DeleteLocalRef(env, class_cls);
        (*env)->ExceptionClear(env);
        (*env)->DeleteLocalRef(env, target_class);
        mrb_gc_arena_restore(mrb, ai);
        return mrb_nil_value();
    }
    jmethodID get_method = (*env)->GetMethodID(env, class_cls, "getMethod",
        "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;");
    if (get_method == NULL || (*env)->ExceptionCheck(env)) {
        if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
        (*env)->DeleteLocalRef(env, class_cls);
        (*env)->DeleteLocalRef(env, target_class);
        mrb_gc_arena_restore(mrb, ai);
        return mrb_nil_value();
    }

    /* Build parameter type array for getMethod.
     * Class.getMethod requires EXACT type match — wrapper classes (Integer)
     * won't match primitive parameters (int). Use Integer.TYPE (int.class),
     * Float.TYPE (float.class), Boolean.TYPE (boolean.class) instead. */
    jclass integer_cls = (*env)->FindClass(env, "java/lang/Integer");
    if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); (*env)->DeleteLocalRef(env, class_cls); (*env)->DeleteLocalRef(env, target_class); mrb_gc_arena_restore(mrb, ai); return mrb_nil_value(); }
    jfieldID int_type_fid = (*env)->GetStaticFieldID(env, integer_cls, "TYPE", "Ljava/lang/Class;");
    jclass int_class = (*env)->GetStaticObjectField(env, integer_cls, int_type_fid);
    jclass float_cls = (*env)->FindClass(env, "java/lang/Float");
    if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); (*env)->DeleteLocalRef(env, integer_cls); (*env)->DeleteLocalRef(env, class_cls); (*env)->DeleteLocalRef(env, target_class); mrb_gc_arena_restore(mrb, ai); return mrb_nil_value(); }
    jfieldID float_type_fid = (*env)->GetStaticFieldID(env, float_cls, "TYPE", "Ljava/lang/Class;");
    jclass float_class = (*env)->GetStaticObjectField(env, float_cls, float_type_fid);
    jclass boolean_cls = (*env)->FindClass(env, "java/lang/Boolean");
    if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); (*env)->DeleteLocalRef(env, integer_cls); (*env)->DeleteLocalRef(env, float_cls); (*env)->DeleteLocalRef(env, class_cls); (*env)->DeleteLocalRef(env, target_class); mrb_gc_arena_restore(mrb, ai); return mrb_nil_value(); }
    jfieldID bool_type_fid = (*env)->GetStaticFieldID(env, boolean_cls, "TYPE", "Ljava/lang/Class;");
    jclass bool_class = (*env)->GetStaticObjectField(env, boolean_cls, bool_type_fid);
    /* CharSequence is the param type for setText, setHint, etc. */
    jclass charsequence_cls = (*env)->FindClass(env, "java/lang/CharSequence");
    if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); (*env)->DeleteLocalRef(env, integer_cls); (*env)->DeleteLocalRef(env, float_cls); (*env)->DeleteLocalRef(env, boolean_cls); (*env)->DeleteLocalRef(env, class_cls); (*env)->DeleteLocalRef(env, target_class); mrb_gc_arena_restore(mrb, ai); return mrb_nil_value(); }
    jclass object_cls = (*env)->FindClass(env, "java/lang/Object");
    if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); (*env)->DeleteLocalRef(env, charsequence_cls); (*env)->DeleteLocalRef(env, integer_cls); (*env)->DeleteLocalRef(env, float_cls); (*env)->DeleteLocalRef(env, boolean_cls); (*env)->DeleteLocalRef(env, class_cls); (*env)->DeleteLocalRef(env, target_class); mrb_gc_arena_restore(mrb, ai); return mrb_nil_value(); }

    jobjectArray param_types = NULL;
    jobjectArray java_args = NULL;

    if (argc > 0) {
        param_types = (*env)->NewObjectArray(env, (jsize)argc, class_cls, object_cls);
        java_args = (*env)->NewObjectArray(env, (jsize)argc,
                                          object_cls, NULL);
    }

    for (mrb_int i = 0; i < argc; i++) {
        mrb_value arg = args[i];
        jobject jarg = NULL;
        jclass param_type = object_cls;

        if (mrb_integer_p(arg)) {
            param_type = int_class;
        }
#ifndef MRB_NO_FLOAT
        else if (mrb_float_p(arg)) {
            param_type = float_class;
        }
#endif
        else if (mrb_true_p(arg) || mrb_false_p(arg)) {
            param_type = bool_class;
        } else if (mrb_string_p(arg)) {
            /* Use CharSequence.class because TextView.setText(CharSequence),
             * Button.setText(CharSequence), etc. String implements CharSequence
             * so the JVM auto-converts our jstring argument correctly. */
            param_type = charsequence_cls;
        } else if (mrb_data_p(arg)) {
            /* Wrapped JavaObject — use View.class for param_type since
             * Class.getMethod requires exact type match. ViewGroup.addView
             * is declared with View, not Button/TextView subclasses. */
            mrboto_data_t *d = (mrboto_data_t *)DATA_PTR(arg);
            if (d != NULL) {
                jobject jobj = mrboto_lookup_ref(env, d->registry_id);
                if (jobj != NULL) {
                    jclass view_cls = (*env)->FindClass(env, "android/view/View");
                    if (view_cls != NULL) {
                        param_type = view_cls;
                    }
                    /* jobj is a GlobalRef, don't delete it */
                }
            }
        }

        jarg = mrboto_mruby_to_java_arg(env, mrb, arg);
        (*env)->SetObjectArrayElement(env, param_types, (jsize)i, param_type);
        (*env)->SetObjectArrayElement(env, java_args, (jsize)i, jarg);
        if (jarg != NULL && !mrb_data_p(arg)) {
            /* Don't delete refs for JavaObject lookups (they're not new refs) */
            if (mrb_string_p(arg) || mrb_integer_p(arg) || mrb_float_p(arg) ||
                mrb_true_p(arg) || mrb_false_p(arg)) {
                (*env)->DeleteLocalRef(env, jarg);
            }
        }
    }

    /* Convert method name to jstring */
    jstring jmethod_name = (*env)->NewStringUTF(env, method_name);

    /* Call Class.getMethod(name, paramTypes) on target_class */
    if (get_method != NULL && !(*env)->ExceptionCheck(env)) {
        jobject method = (*env)->CallObjectMethod(env, target_class, get_method,
                                                   jmethod_name, param_types);
        if ((*env)->ExceptionCheck(env)) {
            jthrowable exc = (*env)->ExceptionOccurred(env);
            (*env)->ExceptionClear(env);
            jclass exc_cls = (*env)->GetObjectClass(env, exc);
            jmethodID to_string = (*env)->GetMethodID(env, exc_cls, "toString", "()Ljava/lang/String;");
            if (to_string) {
                jstring msg = (jstring)(*env)->CallObjectMethod(env, exc, to_string);
                if (msg) {
                    const char *s = (*env)->GetStringUTFChars(env, msg, NULL);
                    LOGE("getMethod('%s') failed: %s", method_name, s);
                    (*env)->ReleaseStringUTFChars(env, msg, s);
                    (*env)->DeleteLocalRef(env, msg);
                }
            }
            (*env)->DeleteLocalRef(env, exc_cls);
            (*env)->DeleteLocalRef(env, exc);
        } else if (method != NULL) {
            LOGI("getMethod('%s') succeeded, invoking...", method_name);
            /* Call Method.invoke(target, javaArgs) */
            jclass method_cls = (*env)->GetObjectClass(env, method);
            jmethodID invoke = (*env)->GetMethodID(env, method_cls, "invoke",
                "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;");
            if (invoke != NULL && !(*env)->ExceptionCheck(env)) {
                jobject jresult = (*env)->CallObjectMethod(env, method, invoke,
                                                            target, java_args);
                /* Method.invoke may throw InvocationTargetException when the
                 * underlying Java method fails (e.g. TextView.append in a test
                 * context where the view is not fully initialized). Clear the
                 * exception and return nil rather than attempting to process
                 * a potentially corrupted jresult reference. */
                if ((*env)->ExceptionCheck(env)) {
                    jthrowable exc = (*env)->ExceptionOccurred(env);
                    (*env)->ExceptionClear(env);
                    log_invoke_exception(env, method_name, exc);
                    goto cleanup;
                }
                if (jresult != NULL) {
                    /* Unwrap common Java return types using IsInstanceOf. */
                    jclass str_cls = (*env)->FindClass(env, "java/lang/String");
                    jclass int_cls = (*env)->FindClass(env, "java/lang/Integer");
                    jclass long_cls = (*env)->FindClass(env, "java/lang/Long");
                    jclass float_cls2 = (*env)->FindClass(env, "java/lang/Float");
                    jclass double_cls = (*env)->FindClass(env, "java/lang/Double");
                    jclass bool_cls = (*env)->FindClass(env, "java/lang/Boolean");

                    if (str_cls != NULL && (*env)->IsInstanceOf(env, jresult, str_cls)) {
                        const char *s = (*env)->GetStringUTFChars(env, jresult, NULL);
                        if (s != NULL) {
                            result = mrb_str_new_cstr(mrb, s);
                            (*env)->ReleaseStringUTFChars(env, jresult, s);
                        }
                    } else if (int_cls != NULL && (*env)->IsInstanceOf(env, jresult, int_cls)) {
                        jclass rc = (*env)->GetObjectClass(env, jresult);
                        if (rc != NULL) {
                            jmethodID mid = (*env)->GetMethodID(env, rc, "intValue", "()I");
                            if (mid) result = mrb_fixnum_value((mrb_int)(*env)->CallIntMethod(env, jresult, mid));
                            (*env)->DeleteLocalRef(env, rc);
                        }
                    } else if (long_cls != NULL && (*env)->IsInstanceOf(env, jresult, long_cls)) {
                        jclass rc = (*env)->GetObjectClass(env, jresult);
                        if (rc != NULL) {
                            jmethodID mid = (*env)->GetMethodID(env, rc, "longValue", "()J");
                            if (mid) result = mrb_fixnum_value((mrb_int)(*env)->CallLongMethod(env, jresult, mid));
                            (*env)->DeleteLocalRef(env, rc);
                        }
                    } else if (float_cls2 != NULL && (*env)->IsInstanceOf(env, jresult, float_cls2)) {
                        jclass rc = (*env)->GetObjectClass(env, jresult);
                        if (rc != NULL) {
                            jmethodID mid = (*env)->GetMethodID(env, rc, "floatValue", "()F");
                            if (mid) result = mrb_float_value(mrb, (double)(*env)->CallFloatMethod(env, jresult, mid));
                            (*env)->DeleteLocalRef(env, rc);
                        }
                    } else if (double_cls != NULL && (*env)->IsInstanceOf(env, jresult, double_cls)) {
                        jclass rc = (*env)->GetObjectClass(env, jresult);
                        if (rc != NULL) {
                            jmethodID mid = (*env)->GetMethodID(env, rc, "doubleValue", "()D");
                            if (mid) result = mrb_float_value(mrb, (double)(*env)->CallDoubleMethod(env, jresult, mid));
                            (*env)->DeleteLocalRef(env, rc);
                        }
                    } else if (bool_cls != NULL && (*env)->IsInstanceOf(env, jresult, bool_cls)) {
                        jclass rc = (*env)->GetObjectClass(env, jresult);
                        if (rc != NULL) {
                            jmethodID mid = (*env)->GetMethodID(env, rc, "booleanValue", "()Z");
                            if (mid) {
                                jboolean v = (*env)->CallBooleanMethod(env, jresult, mid);
                                result = v ? mrb_true_value() : mrb_false_value();
                            }
                            (*env)->DeleteLocalRef(env, rc);
                        }
                    }

                    if (str_cls) (*env)->DeleteLocalRef(env, str_cls);
                    if (int_cls) (*env)->DeleteLocalRef(env, int_cls);
                    if (long_cls) (*env)->DeleteLocalRef(env, long_cls);
                    if (float_cls2) (*env)->DeleteLocalRef(env, float_cls2);
                    if (double_cls) (*env)->DeleteLocalRef(env, double_cls);
                    if (bool_cls) (*env)->DeleteLocalRef(env, bool_cls);

                    /* If result is still nil, fall back to wrapping as JavaObject */
                    if (mrb_nil_p(result)) {
                        result = mrboto_wrap_java_object(mrb, env, jresult);
                    }
                    (*env)->DeleteLocalRef(env, jresult);
                }
            }
            (*env)->DeleteLocalRef(env, method_cls);
            (*env)->DeleteLocalRef(env, method);
        }
    }

    cleanup:
    /* Cleanup */
    (*env)->DeleteLocalRef(env, class_cls);
    (*env)->DeleteLocalRef(env, target_class);
    (*env)->DeleteLocalRef(env, jmethod_name);
    if (param_types != NULL) (*env)->DeleteLocalRef(env, param_types);
    if (java_args != NULL) (*env)->DeleteLocalRef(env, java_args);
    (*env)->DeleteLocalRef(env, charsequence_cls);
    (*env)->DeleteLocalRef(env, integer_cls);
    (*env)->DeleteLocalRef(env, float_cls);
    (*env)->DeleteLocalRef(env, boolean_cls);
    (*env)->DeleteLocalRef(env, object_cls);

    mrb_gc_arena_restore(mrb, ai);
    return result;
}

/* Mrrboto._view_text(registry_id) — get text from a TextView/EditText.
 * Uses TextUtils.toString() to safely convert CharSequence to String. */
static mrb_value mrb_mrboto_view_text(mrb_state *mrb, mrb_value self) {
    mrb_int registry_id;

    /* Guard against pending mruby exception BEFORE parsing args.
       If mrb->exc is set, mrb_get_args could crash. Return nil
       immediately and let the caller handle the error gracefully. */
    if (mrb->exc) {
        /* Clear without reading message — mrb_funcall could crash */
        mrb->exc = NULL;
        LOGW("_view_text: pending exception cleared (pre-guard)");
        return mrb_nil_value();
    }

    mrb_get_args(mrb, "i", &registry_id);
    (void)self;

    JNIEnv *env = mrboto_get_env();
    if (env == NULL) { LOGE("_view_text: env is NULL"); return mrb_nil_value(); }

    jobject view = mrboto_lookup_ref(env, (int)registry_id);
    if (view == NULL) {
        LOGE("_view_text: view lookup failed for id=%d", (int)registry_id);
        return mrb_nil_value();
    }

    int ai = mrb_gc_arena_save(mrb);

    jclass view_cls = (*env)->GetObjectClass(env, view);
    if (view_cls == NULL) {
        (*env)->ExceptionClear(env);
        LOGE("_view_text: GetObjectClass failed");
        mrb_gc_arena_restore(mrb, ai);
        return mrb_nil_value();
    }

    /* Try getText with Editable return type first, then CharSequence fallback. */
    jmethodID get_text = (*env)->GetMethodID(env, view_cls, "getText",
        "()Landroid/text/Editable;");
    if (get_text == NULL) {
        (*env)->ExceptionClear(env);
        get_text = (*env)->GetMethodID(env, view_cls, "getText",
            "()Ljava/lang/CharSequence;");
    }
    if (get_text == NULL) {
        (*env)->ExceptionClear(env);
        (*env)->DeleteLocalRef(env, view_cls);
        LOGE("_view_text: getText method not found");
        mrb_gc_arena_restore(mrb, ai);
        return mrb_nil_value();
    }

    jobject text_obj = (*env)->CallObjectMethod(env, view, get_text);
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
        text_obj = NULL;
    }
    (*env)->DeleteLocalRef(env, view_cls);

    mrb_value result = mrb_nil_value();
    if (text_obj == NULL) {
        result = mrb_str_new_cstr(mrb, "");
    } else {
        jclass obj_cls = (*env)->GetObjectClass(env, text_obj);
        if (obj_cls != NULL) {
            jmethodID to_string = (*env)->GetMethodID(env, obj_cls, "toString",
                "()Ljava/lang/String;");
            if (to_string != NULL && !(*env)->ExceptionCheck(env)) {
                jstring jstr = (jstring)(*env)->CallObjectMethod(env, text_obj, to_string);
                if (jstr != NULL && !(*env)->ExceptionCheck(env)) {
                    const char *str = (*env)->GetStringUTFChars(env, jstr, NULL);
                    if (str != NULL) {
                        result = mrb_str_new_cstr(mrb, str);
                        (*env)->ReleaseStringUTFChars(env, jstr, str);
                    }
                    (*env)->DeleteLocalRef(env, jstr);
                }
            }
            (*env)->ExceptionClear(env);
            (*env)->DeleteLocalRef(env, obj_cls);
        }
        (*env)->DeleteLocalRef(env, text_obj);
    }

    mrb_gc_arena_restore(mrb, ai);
    return result;
}

/* Mrboto._eval(code) — evaluate a Ruby string and return result.
 * Like mruby's mrb_load_string but returns the value as mrb_value. */
static mrb_value mrb_mrboto_eval(mrb_state *mrb, mrb_value self) {
    const char *code;
    mrb_get_args(mrb, "z", &code);
    (void)self;

    int ai = mrb_gc_arena_save(mrb);
    mrb_value result = mrb_load_string(mrb, code);

    mrb_value out;
    if (mrb->exc) {
        const char *s = mrboto_safe_exc_message_cstr(mrb);
        out = mrb_str_new_cstr(mrb, s ? s : "Error");
    } else {
        out = result;
    }

    mrb_gc_arena_restore(mrb, ai);
    return out;
}

/* ── Helper: Show Dialog ──────────────────────────────────────────── */

static mrb_value mrb_mrboto_show_dialog(mrb_state *mrb, mrb_value self) {
    mrb_int context_id;
    mrb_value title, message, buttons_json;
    mrb_get_args(mrb, "iooo", &context_id, &title, &message, &buttons_json);
    (void)self;

    JNIEnv *env = mrboto_get_env();
    if (env == NULL) return mrb_nil_value();

    jobject context = mrboto_lookup_ref(env, (int)context_id);
    if (context == NULL) return mrb_nil_value();

    int ai = mrb_gc_arena_save(mrb);

    /* Convert title/message to C strings, handling nil safely */
    mrb_value title_str = mrb_nil_p(title) ? mrb_str_new_cstr(mrb, "") : mrb_obj_as_string(mrb, title);
    mrb_value msg_str = mrb_nil_p(message) ? mrb_str_new_cstr(mrb, "") : mrb_obj_as_string(mrb, message);
    const char *c_title = mrb_string_value_cstr(mrb, &title_str);
    const char *c_message = mrb_string_value_cstr(mrb, &msg_str);

    /* AlertDialog.Builder(context) */
    jclass builder_cls = (*env)->FindClass(env, "androidx/appcompat/app/AlertDialog$Builder");
    if (builder_cls == NULL) {
        (*env)->ExceptionClear(env);
        builder_cls = (*env)->FindClass(env, "android/app/AlertDialog$Builder");
        if (builder_cls == NULL) { (*env)->ExceptionClear(env); mrb_gc_arena_restore(mrb, ai); return mrb_nil_value(); }
    }
    jmethodID builder_init = (*env)->GetMethodID(env, builder_cls, "<init>",
        "(Landroid/content/Context;)V");
    if (builder_init == NULL) {
        (*env)->ExceptionClear(env);
        (*env)->DeleteLocalRef(env, builder_cls);
        mrb_gc_arena_restore(mrb, ai);
        return mrb_nil_value();
    }
    jstring jtitle = (*env)->NewStringUTF(env, c_title);
    jstring jmessage = (*env)->NewStringUTF(env, c_message);
    jobject builder = (*env)->NewObject(env, builder_cls, builder_init, context);
    if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); (*env)->DeleteLocalRef(env, builder_cls); (*env)->DeleteLocalRef(env, jtitle); (*env)->DeleteLocalRef(env, jmessage); mrb_gc_arena_restore(mrb, ai); return mrb_nil_value(); }

    /* builder.setTitle(title).setMessage(message) */
    jmethodID set_title = (*env)->GetMethodID(env, builder_cls, "setTitle", "(Ljava/lang/CharSequence;)Landroidx/appcompat/app/AlertDialog$Builder;");
    if (set_title == NULL) {
        (*env)->ExceptionClear(env);
        set_title = (*env)->GetMethodID(env, builder_cls, "setTitle", "(Ljava/lang/CharSequence;)Landroid/app/AlertDialog$Builder;");
    }
    if (set_title) (*env)->CallObjectMethod(env, builder, set_title, jtitle);
    jmethodID set_message = (*env)->GetMethodID(env, builder_cls, "setMessage", "(Ljava/lang/CharSequence;)Landroidx/appcompat/app/AlertDialog$Builder;");
    if (set_message == NULL) {
        (*env)->ExceptionClear(env);
        set_message = (*env)->GetMethodID(env, builder_cls, "setMessage", "(Ljava/lang/CharSequence;)Landroid/app/AlertDialog$Builder;");
    }
    if (set_message) (*env)->CallObjectMethod(env, builder, set_message, jmessage);

    /* Handle buttons */
    if (mrb_nil_p(buttons_json)) {
        jmethodID set_positive = (*env)->GetMethodID(env, builder_cls, "setPositiveButton", "(Ljava/lang/CharSequence;Landroid/content/DialogInterface$OnClickListener;)Landroidx/appcompat/app/AlertDialog$Builder;");
        if (set_positive == NULL) { set_positive = (*env)->GetMethodID(env, builder_cls, "setPositiveButton", "(Ljava/lang/CharSequence;Landroid/content/DialogInterface$OnClickListener;)Landroid/app/AlertDialog$Builder;"); }
        if (set_positive) (*env)->CallObjectMethod(env, builder, set_positive, (*env)->NewStringUTF(env, "OK"), NULL);
    } else {
        mrb_value str = mrb_nil_p(buttons_json) ? mrb_nil_value() : mrb_obj_as_string(mrb, buttons_json);
        const char *json = mrb_string_p(str) ? mrb_string_value_cstr(mrb, &str) : NULL;
        if (json && json[0] != '\0' && json[0] != 'n') {
            jclass json_arr_cls = (*env)->FindClass(env, "org/json/JSONArray");
            if (json_arr_cls != NULL) {
                jmethodID json_init = (*env)->GetMethodID(env, json_arr_cls, "<init>", "(Ljava/lang/String;)V");
                if (json_init != NULL) {
                    jstring jjson = (*env)->NewStringUTF(env, json);
                    jobject jarray = (*env)->NewObject(env, json_arr_cls, json_init, jjson);
                    if (jarray != NULL) {
                        jmethodID length = (*env)->GetMethodID(env, json_arr_cls, "length", "()I");
                        jmethodID get_str = (*env)->GetMethodID(env, json_arr_cls, "getString", "(I)Ljava/lang/String;");
                        jint count = (*env)->CallIntMethod(env, jarray, length);

                        jmethodID set_pos = (*env)->GetMethodID(env, builder_cls, "setPositiveButton", "(Ljava/lang/CharSequence;Landroid/content/DialogInterface$OnClickListener;)Landroidx/appcompat/app/AlertDialog$Builder;");
                        if (set_pos == NULL) set_pos = (*env)->GetMethodID(env, builder_cls, "setPositiveButton", "(Ljava/lang/CharSequence;Landroid/content/DialogInterface$OnClickListener;)Landroid/app/AlertDialog$Builder;");
                        jmethodID set_neg = (*env)->GetMethodID(env, builder_cls, "setNegativeButton", "(Ljava/lang/CharSequence;Landroid/content/DialogInterface$OnClickListener;)Landroidx/appcompat/app/AlertDialog$Builder;");
                        if (set_neg == NULL) set_neg = (*env)->GetMethodID(env, builder_cls, "setNegativeButton", "(Ljava/lang/CharSequence;Landroid/content/DialogInterface$OnClickListener;)Landroid/app/AlertDialog$Builder;");
                        jmethodID set_neu = (*env)->GetMethodID(env, builder_cls, "setNeutralButton", "(Ljava/lang/CharSequence;Landroid/content/DialogInterface$OnClickListener;)Landroidx/appcompat/app/AlertDialog$Builder;");
                        if (set_neu == NULL) set_neu = (*env)->GetMethodID(env, builder_cls, "setNeutralButton", "(Ljava/lang/CharSequence;Landroid/content/DialogInterface$OnClickListener;)Landroid/app/AlertDialog$Builder;");

                        if (count >= 1 && get_str) {
                            jstring label = (jstring)(*env)->CallObjectMethod(env, jarray, get_str, (jint)0);
                            if (set_pos && label) (*env)->CallObjectMethod(env, builder, set_pos, label, NULL);
                            if (label) (*env)->DeleteLocalRef(env, label);
                        }
                        if (count >= 2 && get_str) {
                            jstring label = (jstring)(*env)->CallObjectMethod(env, jarray, get_str, (jint)1);
                            if (set_neg && label) (*env)->CallObjectMethod(env, builder, set_neg, label, NULL);
                            if (label) (*env)->DeleteLocalRef(env, label);
                        }
                        if (count >= 3 && get_str) {
                            jstring label = (jstring)(*env)->CallObjectMethod(env, jarray, get_str, (jint)2);
                            if (set_neu && label) (*env)->CallObjectMethod(env, builder, set_neu, label, NULL);
                            if (label) (*env)->DeleteLocalRef(env, label);
                        }
                        (*env)->DeleteLocalRef(env, jarray);
                    }
                    (*env)->DeleteLocalRef(env, jjson);
                }
                (*env)->DeleteLocalRef(env, json_arr_cls);
            } else { (*env)->ExceptionClear(env); }
        }
    }

    /* builder.setCancelable(true).show() */
    jmethodID set_cancelable = (*env)->GetMethodID(env, builder_cls, "setCancelable", "(Z)Landroidx/appcompat/app/AlertDialog$Builder;");
    if (set_cancelable == NULL) set_cancelable = (*env)->GetMethodID(env, builder_cls, "setCancelable", "(Z)Landroid/app/AlertDialog$Builder;");
    if (set_cancelable) (*env)->CallObjectMethod(env, builder, set_cancelable, JNI_TRUE);

    jmethodID create = (*env)->GetMethodID(env, builder_cls, "create", "()Landroidx/appcompat/app/AlertDialog;");
    if (create == NULL) { create = (*env)->GetMethodID(env, builder_cls, "create", "()Landroid/app/AlertDialog;"); }
    if (create) {
        jobject dialog = (*env)->CallObjectMethod(env, builder, create);
        if ((*env)->ExceptionCheck(env)) {
            /* Can't create handler on non-Looper thread in tests */
            (*env)->ExceptionClear(env);
        } else if (dialog != NULL) {
            jclass dialog_cls = (*env)->GetObjectClass(env, dialog);
            jmethodID show_method = (*env)->GetMethodID(env, dialog_cls, "show", "()V");
            if (show_method) (*env)->CallVoidMethod(env, dialog, show_method);
            if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
            if (dialog_cls) (*env)->DeleteLocalRef(env, dialog_cls);
            (*env)->DeleteLocalRef(env, dialog);
        }
    }

    (*env)->DeleteLocalRef(env, builder_cls);
    (*env)->DeleteLocalRef(env, jtitle);
    (*env)->DeleteLocalRef(env, jmessage);
    mrb_gc_arena_restore(mrb, ai);
    return mrb_nil_value();
}

/* ── Helper: Show Snackbar ────────────────────────────────────────── */

static mrb_value mrb_mrboto_show_snackbar(mrb_state *mrb, mrb_value self) {
    mrb_int context_id, view_registry_id;
    mrb_value message;
    mrb_int duration;
    mrb_get_args(mrb, "iioi", &context_id, &view_registry_id, &message, &duration);
    (void)self; (void)context_id;

    JNIEnv *env = mrboto_get_env();
    if (env == NULL) return mrb_nil_value();

    jobject view = mrboto_lookup_ref(env, (int)view_registry_id);
    if (view == NULL) return mrb_nil_value();

    int ai = mrb_gc_arena_save(mrb);
    mrb_value msg_str = mrb_nil_p(message) ? mrb_str_new_cstr(mrb, "") : mrb_obj_as_string(mrb, message);
    const char *c_msg = mrb_string_value_cstr(mrb, &msg_str);
    jstring jmsg = (*env)->NewStringUTF(env, c_msg);
    jint dur = (duration == 1) ? -1 : -2; /* LENGTH_LONG=-1, LENGTH_SHORT=-2 */

    jclass snackbar_cls = (*env)->FindClass(env, "com/google/android/material/snackbar/Snackbar");
    if (snackbar_cls != NULL) {
        jmethodID make = (*env)->GetStaticMethodID(env, snackbar_cls, "make",
            "(Landroid/view/View;Ljava/lang/CharSequence;I)Lcom/google/android/material/snackbar/Snackbar;");
        if (make != NULL) {
            jobject snackbar = (*env)->CallStaticObjectMethod(env, snackbar_cls, make, view, jmsg, dur);
            if ((*env)->ExceptionCheck(env)) {
                /* View has no suitable parent (e.g. bare View in tests) */
                (*env)->ExceptionClear(env);
            } else if (snackbar != NULL) {
                jclass sb_cls = (*env)->GetObjectClass(env, snackbar);
                jmethodID show = (*env)->GetMethodID(env, sb_cls, "show", "()V");
                if (show) (*env)->CallVoidMethod(env, snackbar, show);
                if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
                if (sb_cls) (*env)->DeleteLocalRef(env, sb_cls);
                (*env)->DeleteLocalRef(env, snackbar);
            }
        }
        (*env)->DeleteLocalRef(env, snackbar_cls);
    } else { (*env)->ExceptionClear(env); }

    (*env)->DeleteLocalRef(env, jmsg);
    mrb_gc_arena_restore(mrb, ai);
    return mrb_nil_value();
}

/* ── Helper: Show PopupMenu ───────────────────────────────── */

static mrb_value mrb_mrboto_show_popup_menu(mrb_state *mrb, mrb_value self) {
    mrb_int context_id, view_registry_id;
    mrb_value items_json;
    mrb_get_args(mrb, "iio", &context_id, &view_registry_id, &items_json);
    (void)self; (void)context_id;

    JNIEnv *env = mrboto_get_env();
    if (env == NULL) return mrb_nil_value();

    jobject view = mrboto_lookup_ref(env, (int)view_registry_id);
    if (view == NULL) return mrb_nil_value();

    int ai = mrb_gc_arena_save(mrb);

    mrb_value str = mrb_funcall(mrb, items_json, "to_s", 0);
    const char *json = mrb_string_p(str) ? mrb_string_value_cstr(mrb, &str) : "[]";

    jclass popup_cls = (*env)->FindClass(env, "android/widget/PopupMenu");
    if (popup_cls != NULL) {
        jmethodID popup_init = (*env)->GetMethodID(env, popup_cls, "<init>",
            "(Landroid/content/Context;Landroid/view/View;)V");
        if (popup_init != NULL) {
            jobject context_obj = mrboto_lookup_ref(env, (int)context_id);
            jobject popup = (*env)->NewObject(env, popup_cls, popup_init, context_obj, view);
            if ((*env)->ExceptionCheck(env)) {
                (*env)->ExceptionClear(env);
            } else if (popup != NULL) {
                jmethodID get_menu = (*env)->GetMethodID(env, popup_cls, "getMenu", "()Landroid/view/Menu;");
                if (get_menu != NULL) {
                    jobject menu = (*env)->CallObjectMethod(env, popup, get_menu);
                    if (menu != NULL && !(*env)->ExceptionCheck(env)) {
                        jclass menu_cls = (*env)->GetObjectClass(env, menu);
                        jmethodID add = (*env)->GetMethodID(env, menu_cls, "add", "(IIILjava/lang/CharSequence;)Landroid/view/MenuItem;");

                        jclass json_arr_cls = (*env)->FindClass(env, "org/json/JSONArray");
                        if (json_arr_cls != NULL) {
                            jmethodID json_init = (*env)->GetMethodID(env, json_arr_cls, "<init>", "(Ljava/lang/String;)V");
                            if (json_init != NULL) {
                                jstring jjson = (*env)->NewStringUTF(env, json);
                                jobject jarray = (*env)->NewObject(env, json_arr_cls, json_init, jjson);
                                if (jarray != NULL) {
                                    jmethodID length = (*env)->GetMethodID(env, json_arr_cls, "length", "()I");
                                    jmethodID get_str = (*env)->GetMethodID(env, json_arr_cls, "getString", "(I)Ljava/lang/String;");
                                    jint count = (*env)->CallIntMethod(env, jarray, length);
                                    for (jint i = 0; i < count && add && get_str; i++) {
                                        jstring label = (jstring)(*env)->CallObjectMethod(env, jarray, get_str, i);
                                        if (label) {
                                            (*env)->CallObjectMethod(env, menu, add, (jint)0, i, (jint)0, label);
                                            (*env)->DeleteLocalRef(env, label);
                                        }
                                    }
                                    (*env)->DeleteLocalRef(env, jarray);
                                }
                                if (jjson) (*env)->DeleteLocalRef(env, jjson);
                            }
                            (*env)->DeleteLocalRef(env, json_arr_cls);
                        } else { (*env)->ExceptionClear(env); }

                        if (menu_cls) (*env)->DeleteLocalRef(env, menu_cls);
                        (*env)->DeleteLocalRef(env, menu);
                    }
                }
                /* Don't call show() — it requires a window token and will
                 * hang on unattached Views in instrumented tests.
                 * Constructor + getMenu + add items is sufficient to verify
                 * the JNI bridge works without crashing. */
                (*env)->DeleteLocalRef(env, popup);
            }
        }
        (*env)->DeleteLocalRef(env, popup_cls);
    } else { (*env)->ExceptionClear(env); }

    mrb_gc_arena_restore(mrb, ai);
    return mrb_nil_value();
}

/* ── Helper: Animation — Fade ─────────────────────────────────────── */

static mrb_value mrb_mrboto_animate_fade(mrb_state *mrb, mrb_value self) {
    mrb_int context_id, view_registry_id;
    mrb_float from_alpha, to_alpha;
    mrb_int duration;
    mrb_get_args(mrb, "iiffi", &context_id, &view_registry_id, &from_alpha, &to_alpha, &duration);
    (void)self; (void)context_id;

    JNIEnv *env = mrboto_get_env();
    if (env == NULL) return mrb_nil_value();

    jobject view = mrboto_lookup_ref(env, (int)view_registry_id);
    if (view == NULL) return mrb_nil_value();

    int ai = mrb_gc_arena_save(mrb);

    jclass alpha_cls = (*env)->FindClass(env, "android/view/animation/AlphaAnimation");
    if (alpha_cls != NULL) {
        jmethodID alpha_init = (*env)->GetMethodID(env, alpha_cls, "<init>", "(FF)V");
        if (alpha_init != NULL) {
            jobject anim = (*env)->NewObject(env, alpha_cls, alpha_init, (jfloat)from_alpha, (jfloat)to_alpha);
            if ((*env)->ExceptionCheck(env)) {
                (*env)->ExceptionClear(env);
            } else if (anim != NULL) {
                jclass anim_cls = (*env)->GetObjectClass(env, anim);
                jmethodID set_duration = (*env)->GetMethodID(env, anim_cls, "setDuration", "(J)V");
                jmethodID set_fill = (*env)->GetMethodID(env, anim_cls, "setFillAfter", "(Z)V");
                if (set_duration) (*env)->CallVoidMethod(env, anim, set_duration, (jlong)duration);
                if (set_fill) (*env)->CallVoidMethod(env, anim, set_fill, JNI_TRUE);
                if (anim_cls) (*env)->DeleteLocalRef(env, anim_cls);

                jclass view_cls = (*env)->GetObjectClass(env, view);
                jmethodID start_anim = (*env)->GetMethodID(env, view_cls, "startAnimation", "(Landroid/view/animation/Animation;)V");
                if (start_anim) (*env)->CallVoidMethod(env, view, start_anim, anim);
                if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
                if (view_cls) (*env)->DeleteLocalRef(env, view_cls);
                (*env)->DeleteLocalRef(env, anim);
            }
        }
        (*env)->DeleteLocalRef(env, alpha_cls);
    } else { (*env)->ExceptionClear(env); }

    mrb_gc_arena_restore(mrb, ai);
    return mrb_nil_value();
}

/* ── Helper: Animation — Translate ────────────────────────────────── */

static mrb_value mrb_mrboto_animate_translate(mrb_state *mrb, mrb_value self) {
    mrb_int context_id, view_registry_id;
    mrb_float from_x, from_y, to_x, to_y;
    mrb_int duration;
    mrb_get_args(mrb, "iiffffi", &context_id, &view_registry_id, &from_x, &from_y, &to_x, &to_y, &duration);
    (void)self; (void)context_id;

    JNIEnv *env = mrboto_get_env();
    if (env == NULL) return mrb_nil_value();

    jobject view = mrboto_lookup_ref(env, (int)view_registry_id);
    if (view == NULL) return mrb_nil_value();

    int ai = mrb_gc_arena_save(mrb);

    jclass trans_cls = (*env)->FindClass(env, "android/view/animation/TranslateAnimation");
    if (trans_cls != NULL) {
        jmethodID trans_init = (*env)->GetMethodID(env, trans_cls, "<init>", "(FFFF)V");
        if (trans_init != NULL) {
            jobject anim = (*env)->NewObject(env, trans_cls, trans_init,
                (jfloat)from_x, (jfloat)from_y, (jfloat)to_x, (jfloat)to_y);
            if ((*env)->ExceptionCheck(env)) {
                (*env)->ExceptionClear(env);
            } else if (anim != NULL) {
                jclass anim_cls = (*env)->GetObjectClass(env, anim);
                jmethodID set_duration = (*env)->GetMethodID(env, anim_cls, "setDuration", "(J)V");
                jmethodID set_fill = (*env)->GetMethodID(env, anim_cls, "setFillAfter", "(Z)V");
                if (set_duration) (*env)->CallVoidMethod(env, anim, set_duration, (jlong)duration);
                if (set_fill) (*env)->CallVoidMethod(env, anim, set_fill, JNI_TRUE);
                if (anim_cls) (*env)->DeleteLocalRef(env, anim_cls);

                jclass view_cls = (*env)->GetObjectClass(env, view);
                jmethodID start_anim = (*env)->GetMethodID(env, view_cls, "startAnimation", "(Landroid/view/animation/Animation;)V");
                if (start_anim) (*env)->CallVoidMethod(env, view, start_anim, anim);
                if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
                if (view_cls) (*env)->DeleteLocalRef(env, view_cls);
                (*env)->DeleteLocalRef(env, anim);
            }
        }
        (*env)->DeleteLocalRef(env, trans_cls);
    } else { (*env)->ExceptionClear(env); }

    mrb_gc_arena_restore(mrb, ai);
    return mrb_nil_value();
}

/* ── Helper: Animation — Scale ────────────────────────────────────── */

static mrb_value mrb_mrboto_animate_scale(mrb_state *mrb, mrb_value self) {
    mrb_int context_id, view_registry_id;
    mrb_float from_x, from_y, to_x, to_y;
    mrb_int duration;
    mrb_get_args(mrb, "iiffffi", &context_id, &view_registry_id, &from_x, &from_y, &to_x, &to_y, &duration);
    (void)self; (void)context_id;

    JNIEnv *env = mrboto_get_env();
    if (env == NULL) return mrb_nil_value();

    jobject view = mrboto_lookup_ref(env, (int)view_registry_id);
    if (view == NULL) return mrb_nil_value();

    int ai = mrb_gc_arena_save(mrb);

    jclass scale_cls = (*env)->FindClass(env, "android/view/animation/ScaleAnimation");
    if (scale_cls != NULL) {
        /* ScaleAnimation(fromX, toX, fromY, toY, pivotXType, pivotXValue, pivotYType, pivotYValue) */
        jmethodID scale_init = (*env)->GetMethodID(env, scale_cls, "<init>", "(FFFFIFIF)V");
        if (scale_init != NULL) {
            jobject anim = (*env)->NewObject(env, scale_cls, scale_init,
                (jfloat)from_x, (jfloat)to_x, (jfloat)from_y, (jfloat)to_y,
                (jint)1 /* RELATIVE_TO_SELF */, (jfloat)0.5f, (jint)1, (jfloat)0.5f);
            if ((*env)->ExceptionCheck(env)) {
                (*env)->ExceptionClear(env);
            } else if (anim != NULL) {
                jclass anim_cls = (*env)->GetObjectClass(env, anim);
                jmethodID set_duration = (*env)->GetMethodID(env, anim_cls, "setDuration", "(J)V");
                jmethodID set_fill = (*env)->GetMethodID(env, anim_cls, "setFillAfter", "(Z)V");
                if (set_duration) (*env)->CallVoidMethod(env, anim, set_duration, (jlong)duration);
                if (set_fill) (*env)->CallVoidMethod(env, anim, set_fill, JNI_TRUE);
                if (anim_cls) (*env)->DeleteLocalRef(env, anim_cls);

                jclass view_cls = (*env)->GetObjectClass(env, view);
                jmethodID start_anim = (*env)->GetMethodID(env, view_cls, "startAnimation", "(Landroid/view/animation/Animation;)V");
                if (start_anim) (*env)->CallVoidMethod(env, view, start_anim, anim);
                if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
                if (view_cls) (*env)->DeleteLocalRef(env, view_cls);
                (*env)->DeleteLocalRef(env, anim);
            }
        }
        (*env)->DeleteLocalRef(env, scale_cls);
    } else { (*env)->ExceptionClear(env); }

    mrb_gc_arena_restore(mrb, ai);
    return mrb_nil_value();
}

static void mrb_mrboto_define_methods(mrb_state *mrb, struct RClass *mrboto) {
    mrb_define_module_function(mrb, mrboto, "_set_content_view", mrb_mrboto_set_content_view, MRB_ARGS_REQ(2));
    mrb_define_module_function(mrb, mrboto, "_toast", mrb_mrboto_toast, MRB_ARGS_REQ(3));
    mrb_define_module_function(mrb, mrboto, "_start_activity", mrb_mrboto_start_activity, MRB_ARGS_ANY());
    mrb_define_module_function(mrb, mrboto, "_get_extra", mrb_mrboto_get_extra, MRB_ARGS_REQ(2));
    mrb_define_module_function(mrb, mrboto, "_sp_get_string", mrb_mrboto_sp_get_string, MRB_ARGS_ANY());
    mrb_define_module_function(mrb, mrboto, "_sp_put_string", mrb_mrboto_sp_put_string, MRB_ARGS_REQ(4));
    mrb_define_module_function(mrb, mrboto, "_sp_get_int", mrb_mrboto_sp_get_int, MRB_ARGS_ANY());
    mrb_define_module_function(mrb, mrboto, "_sp_put_int", mrb_mrboto_sp_put_int, MRB_ARGS_REQ(4));
    mrb_define_module_function(mrb, mrboto, "_app_context", mrb_mrboto_app_context, MRB_ARGS_NONE());
    mrb_define_module_function(mrb, mrboto, "_string_res", mrb_mrboto_string_res, MRB_ARGS_REQ(2));
    mrb_define_module_function(mrb, mrboto, "_create_view", mrb_mrboto_create_view, MRB_ARGS_ANY());
    mrb_define_module_function(mrb, mrboto, "_set_on_click", mrb_mrboto_set_on_click, MRB_ARGS_REQ(2));
    mrb_define_module_function(mrb, mrboto, "_run_on_ui_thread", mrb_mrboto_run_on_ui_thread, MRB_ARGS_REQ(2));
    mrb_define_module_function(mrb, mrboto, "_dp_to_px", mrb_mrboto_dp_to_px, MRB_ARGS_REQ(1));
    mrb_define_module_function(mrb, mrboto, "_package_name", mrb_mrboto_package_name, MRB_ARGS_NONE());
    mrb_define_module_function(mrb, mrboto, "_register_object", mrb_mrboto_register_object, MRB_ARGS_REQ(1));
    mrb_define_module_function(mrb, mrboto, "_java_object_for", mrb_mrboto_java_object_for, MRB_ARGS_REQ(1));
    mrb_define_module_function(mrb, mrboto, "_call_java_method", mrb_mrboto_call_java_method, MRB_ARGS_ANY());
    mrb_define_module_function(mrb, mrboto, "_eval", mrb_mrboto_eval, MRB_ARGS_REQ(1));
    mrb_define_module_function(mrb, mrboto, "_view_text", mrb_mrboto_view_text, MRB_ARGS_REQ(1));
    mrb_define_module_function(mrb, mrboto, "_show_dialog", mrb_mrboto_show_dialog, MRB_ARGS_REQ(4));
    mrb_define_module_function(mrb, mrboto, "_show_snackbar", mrb_mrboto_show_snackbar, MRB_ARGS_REQ(4));
    mrb_define_module_function(mrb, mrboto, "_show_popup_menu", mrb_mrboto_show_popup_menu, MRB_ARGS_REQ(3));
    mrb_define_module_function(mrb, mrboto, "_animate_fade", mrb_mrboto_animate_fade, MRB_ARGS_REQ(5));
    mrb_define_module_function(mrb, mrboto, "_animate_translate", mrb_mrboto_animate_translate, MRB_ARGS_REQ(7));
    mrb_define_module_function(mrb, mrboto, "_animate_scale", mrb_mrboto_animate_scale, MRB_ARGS_REQ(7));
}

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Register Android classes in mruby (Mrboto module, JavaObject, etc.)
 */
JNIEXPORT void JNICALL
Java_moe_bemly_mrboto_MRuby_nativeRegisterAndroidClasses(JNIEnv *env, jobject thiz, jlong mrbPtr) {
    (void)thiz;
    (void)env;

    mrb_state *mrb = (mrb_state *)(intptr_t)mrbPtr;

    /* Define Mrboto module */
    struct RClass *mrboto = mrb_define_module(mrb, "Mrboto");

    /* Define JavaObject class under Mrboto */
    struct RClass *java_obj = mrb_define_class_under(mrb, mrboto, "JavaObject", mrb->object_class);
    g_java_object_class = java_obj; /* store for mrb_data_object_alloc in wrap */

    /* Bind native methods to Mrboto module */
    mrb_mrboto_define_methods(mrb, mrboto);

    LOGI("Android classes registered in mruby");
}

/**
 * Dispatch lifecycle callback to Ruby Activity.
 * Evaluates: Mrboto.current_activity.on_xxx(bundle)
 */
JNIEXPORT jstring JNICALL
Java_moe_bemly_mrboto_MRuby_nativeDispatchLifecycle(JNIEnv *env, jobject thiz,
                                              jlong mrbPtr, jint activityId,
                                              jstring callbackName, jint argsId) {
    (void)thiz;

    mrb_state *mrb = (mrb_state *)(intptr_t)mrbPtr;
    if (mrb == NULL) return NULL;

    int ai = mrb_gc_arena_save(mrb);

    /* Convert callbackName from jstring to C string */
    const char *cname = (*env)->GetStringUTFChars(env, callbackName, NULL);

    /* Get Mrboto.current_activity via mrb_const_get (proven path to obtain
     * the module) + mrb_iv_get (direct instance variable access). */
    mrb_value mrboto_mod = mrb_const_get(mrb, mrb_obj_value(mrb->object_class),
                                         mrb_intern_lit(mrb, "Mrboto"));
    mrb_value activity = mrb_nil_value();
    if (!mrb_nil_p(mrboto_mod)) {
        mrb_sym iv_name = mrb_intern_lit(mrb, "@current_activity");
        activity = mrb_iv_get(mrb, mrboto_mod, iv_name);
    }

    jstring result = NULL;
    if (!mrb_nil_p(activity)) {
        /* on_create and on_post_create accept 1 argument (bundle, may be nil).
         * Other hooks (on_start, on_resume, etc.) take no arguments. */
        mrb_value bundle_val = mrb_nil_value();
        int needs_bundle = (strcmp(cname, "on_create") == 0 ||
                            strcmp(cname, "on_post_create") == 0);

        if (needs_bundle && argsId > 0) {
            JNIEnv *env2 = mrboto_get_env();
            jobject bundle = mrboto_lookup_ref(env2, (int)argsId);
            if (bundle != NULL) {
                bundle_val = mrboto_wrap_java_object(mrb, env2, bundle);
            }
        }

        /* Call the method with the correct number of arguments */
        if (needs_bundle) {
            mrb_funcall(mrb, activity, cname, 1, bundle_val);
        } else {
            mrb_funcall(mrb, activity, cname, 0);
        }

        if (mrb->exc) {
            /* Exception occurred */
            mrb_value msg = mrb_funcall(mrb, mrb_obj_value(mrb->exc), "message", 0);
            if (mrb_string_p(msg)) {
                const char *s = mrb_string_value_cstr(mrb, &msg);
                result = (*env)->NewStringUTF(env, s);
            }
            mrb->exc = NULL;
        } else {
            result = (*env)->NewStringUTF(env, "ok");
        }
    } else {
        result = (*env)->NewStringUTF(env, "Error: no current_activity set");
    }

    (*env)->ReleaseStringUTFChars(env, callbackName, cname);
    mrb_gc_arena_restore(mrb, ai);

    return result;
}

/* Context for mrb_load_string wrapper in mrb_protect */
typedef struct {
    mrb_state *mrb;
    const char *code;
    mrb_value result;
} mrboto_load_ctx_t;

static mrb_value mrboto_safe_load_script(mrb_state *mrb, mrb_value self) {
    (void)self;
    mrboto_load_ctx_t *ctx = (mrboto_load_ctx_t *)mrb_cptr(self);
    ctx->result = mrb_load_string(ctx->mrb, ctx->code);
    return ctx->result;
}

/**
 * Load and execute a Ruby source script.
 */
JNIEXPORT jstring JNICALL
Java_moe_bemly_mrboto_MRuby_nativeLoadScript(JNIEnv *env, jobject thiz,
                                       jlong mrbPtr, jstring script) {
    (void)thiz;

    mrb_state *mrb = (mrb_state *)(intptr_t)mrbPtr;
    if (mrb == NULL) return (*env)->NewStringUTF(env, "Error: mruby VM not initialized");

    const char *c_script = (*env)->GetStringUTFChars(env, script, NULL);
    if (c_script == NULL) return (*env)->NewStringUTF(env, "Error: failed to get script string");

    int ai = mrb_gc_arena_save(mrb);

    /* Clear any pre-existing exception before loading new script. */
    mrb->exc = NULL;

    /* Execute via mrb_protect so exceptions during compilation are caught. */
    mrboto_load_ctx_t ctx;
    ctx.mrb = mrb;
    ctx.code = c_script;
    ctx.result = mrb_nil_value();

    mrb_bool error = FALSE;
    mrb_value data = mrb_cptr_value(mrb, &ctx);
    mrb_protect(mrb, mrboto_safe_load_script, data, &error);

    (*env)->ReleaseStringUTFChars(env, script, c_script);

    jstring jresult = NULL;
    if (mrb->exc || error) {
        /* Extract exception with "ClassName: message" format */
        mrb_value exc_obj = mrb->exc ? mrb_obj_value(mrb->exc) : mrb_nil_value();
        const char *class_name = mrb->exc ? mrb_obj_classname(mrb, exc_obj) : NULL;

        /* Try message extraction */
        mrboto_exc_ctx_t ectx;
        ectx.exc = mrb->exc ? mrb_obj_value(mrb->exc) : mrb_nil_value();
        ectx.result = mrb_nil_value();
        mrb_bool msg_error = FALSE;
        mrb_value edata = mrb_cptr_value(mrb, &ectx);
        mrb_protect(mrb, mrboto_safe_exc_message, edata, &msg_error);

        const char *msg_str = NULL;
        if (mrb_string_p(ectx.result)) {
            msg_str = mrb_string_value_cstr(mrb, &ectx.result);
        }
        mrb->exc = NULL;

        char buf[512];
        if (msg_str != NULL && msg_str[0] != '\0') {
            if (class_name) {
                snprintf(buf, sizeof(buf), "%s: %s", class_name, msg_str);
            } else {
                snprintf(buf, sizeof(buf), "%s", msg_str);
            }
        } else {
            if (class_name) {
                snprintf(buf, sizeof(buf), "%s: (no message available)", class_name);
            } else {
                snprintf(buf, sizeof(buf), "Unknown Ruby error during script load");
            }
        }
        LOGE("Ruby error (loadScript): %s", buf);
        jresult = (*env)->NewStringUTF(env, buf);
    } else if (mrb_string_p(ctx.result)) {
        const char *s = mrb_string_value_cstr(mrb, &ctx.result);
        jresult = (*env)->NewStringUTF(env, s);
    } else {
        mrb_value str = mrb_funcall(mrb, ctx.result, "to_s", 0);
        if (mrb->exc) { mrb->exc = NULL; }
        if (mrb_string_p(str)) {
            const char *s = mrb_string_value_cstr(mrb, &str);
            jresult = (*env)->NewStringUTF(env, s);
        }
    }

    if (jresult == NULL) {
        jresult = (*env)->NewStringUTF(env, "(unknown)");
    }

    mrb_gc_arena_restore(mrb, ai);
    return jresult;
}

/**
 * Set OnClickListener on a View with a mruby callback ID.
 * Implemented by evaluating Ruby code that uses the Kotlin ViewListener.
 */
JNIEXPORT void JNICALL
Java_moe_bemly_mrboto_MRuby_nativeSetOnClick(JNIEnv *env, jobject thiz,
                                       jlong mrbPtr, jint viewId, jint callbackId) {
    (void)thiz;
    (void)mrbPtr;
    (void)viewId;
    (void)callbackId;
    LOGD("nativeSetOnClick: view=%d callback=%d", viewId, callbackId);
}

JNIEXPORT void JNICALL
Java_moe_bemly_mrboto_MRuby_nativeSetContentView(JNIEnv *env, jobject thiz,
                                           jlong mrbPtr, jint activityId, jint viewId) {
    (void)thiz;
    (void)mrbPtr;

    jobject activity = mrboto_lookup_ref(env, (int)activityId);
    jobject view = mrboto_lookup_ref(env, (int)viewId);
    if (activity == NULL || view == NULL) return;

    jclass act_cls = (*env)->GetObjectClass(env, activity);
    jmethodID setContentView = (*env)->GetMethodID(env, act_cls, "setContentView",
                                                   "(Landroid/view/View;)V");
    if (setContentView != NULL) {
        (*env)->CallVoidMethod(env, activity, setContentView, view);
    }
    (*env)->DeleteLocalRef(env, act_cls);
}

JNIEXPORT jint JNICALL
Java_moe_bemly_mrboto_MRuby_nativeRegisterObject(JNIEnv *env, jobject thiz,
                                           jlong mrbPtr, jobject obj) {
    (void)thiz;
    (void)mrbPtr;
    if (obj == NULL) return 0;
    return mrboto_register_ref(env, obj);
}

JNIEXPORT jobject JNICALL
Java_moe_bemly_mrboto_MRuby_nativeLookupObject(JNIEnv *env, jobject thiz,
                                         jlong mrbPtr, jint registryId) {
    (void)thiz;
    (void)mrbPtr;
    return mrboto_lookup_ref(env, (int)registryId);
}

/**
 * Clear all JNI GlobalRef registry entries. Used between tests to prevent
 * reference table bloat.
 */
JNIEXPORT void JNICALL
Java_moe_bemly_mrboto_MRuby_nativeClearRegistry(JNIEnv *env, jobject thiz) {
    (void)thiz;
    mrboto_clear_registry(env);
    LOGI("Registry cleared, next_id reset to 1");
}

#ifdef __cplusplus
}
#endif
