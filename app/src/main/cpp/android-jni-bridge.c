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
#include <stdio.h>
#include <mruby.h>
#include <mruby/data.h>
#include <mruby/hash.h>
#include <mruby/array.h>
#include <mruby/string.h>
#include <mruby/variable.h>
#include <mruby/compile.h>

#include "android-jni-bridge.h"
#include <string.h>

#define LOG_TAG "mrboto-bridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

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
                if ((*env)->ExceptionCheck(env)) {
                    (*env)->ExceptionClear(env);
                } else if (jresult != NULL) {
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
    mrb_get_args(mrb, "i", &registry_id);
    (void)self;

    JNIEnv *env = mrboto_get_env();
    if (env == NULL) { LOGE("_view_text: env is NULL"); return mrb_nil_value(); }

    jobject view = mrboto_lookup_ref(env, (int)registry_id);
    if (view == NULL) { LOGE("_view_text: view lookup failed for id=%d", (int)registry_id); return mrb_nil_value(); }

    int ai = mrb_gc_arena_save(mrb);

    jclass view_cls = (*env)->GetObjectClass(env, view);
    if (view_cls == NULL || (*env)->ExceptionCheck(env)) {
        if (view_cls) (*env)->DeleteLocalRef(env, view_cls);
        (*env)->ExceptionClear(env);
        mrb_gc_arena_restore(mrb, ai);
        LOGE("_view_text: GetObjectClass failed");
        return mrb_nil_value();
    }

    /* Get the class name for debugging */
    jmethodID get_name = (*env)->GetMethodID(env, view_cls, "getClass", "()Ljava/lang/Class;");
    if (get_name) {
        jclass cls_of_cls = (*env)->GetObjectClass(env, view_cls);
        if (cls_of_cls) {
            jmethodID get_simple_name = (*env)->GetMethodID(env, cls_of_cls, "getSimpleName", "()Ljava/lang/String;");
            if (get_simple_name) {
                jobject class_obj = (*env)->CallObjectMethod(env, view, get_name);
                if (class_obj) {
                    jclass cobj_cls = (*env)->GetObjectClass(env, class_obj);
                    jmethodID cobj_get_name = (*env)->GetMethodID(env, cobj_cls, "getSimpleName", "()Ljava/lang/String;");
                    if (cobj_get_name) {
                        jstring name = (jstring)(*env)->CallObjectMethod(env, class_obj, cobj_get_name);
                        if (name) {
                            const char *s = (*env)->GetStringUTFChars(env, name, NULL);
                            LOGI("_view_text: view class=%s id=%d", s, (int)registry_id);
                            (*env)->ReleaseStringUTFChars(env, name, s);
                            (*env)->DeleteLocalRef(env, name);
                        }
                    }
                    (*env)->DeleteLocalRef(env, cobj_cls);
                    (*env)->DeleteLocalRef(env, class_obj);
                }
            }
            (*env)->DeleteLocalRef(env, cls_of_cls);
        }
    }

    /* Try getText with Editable return type first, then CharSequence fallback.
     * TextView.getText() returns Editable, but some subclasses declare CharSequence. */
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
    LOGI("_view_text: text_obj=%p", text_obj);
    if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); (*env)->DeleteLocalRef(env, view_cls); mrb_gc_arena_restore(mrb, ai); return mrb_nil_value(); }

    /* Use TextUtils.toString(textObj) — safe for CharSequence/Editable → String,
     * and handles null by returning empty string. */
    jclass tu_cls = (*env)->FindClass(env, "android/text/TextUtils");
    jmethodID tu_to_str = tu_cls ? (*env)->GetStaticMethodID(env, tu_cls, "toString",
        "(Ljava/lang/CharSequence;)Ljava/lang/String;") : NULL;
    if (tu_to_str == NULL) { (*env)->ExceptionClear(env); }

    mrb_value result = mrb_nil_value();
    if (tu_cls && tu_to_str) {
        jstring jstr = (jstring)(*env)->CallStaticObjectMethod(env, tu_cls, tu_to_str, text_obj);
        LOGI("_view_text: TextUtils.toString => jstr=%p", jstr);
        if (jstr != NULL && !(*env)->ExceptionCheck(env)) {
            const char *str = (*env)->GetStringUTFChars(env, jstr, NULL);
            if (str != NULL) {
                result = mrb_str_new_cstr(mrb, str);
                (*env)->ReleaseStringUTFChars(env, jstr, str);
            }
            (*env)->DeleteLocalRef(env, jstr);
        }
        (*env)->DeleteLocalRef(env, tu_cls);
    } else {
        LOGE("_view_text: TextUtils or toString not found");
    }

    (*env)->DeleteLocalRef(env, view_cls);
    if (text_obj) (*env)->DeleteLocalRef(env, text_obj);

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
        mrb_value msg = mrb_funcall(mrb, mrb_obj_value(mrb->exc), "message", 0);
        if (mrb_string_p(msg)) {
            const char *s = mrb_string_value_cstr(mrb, &msg);
            out = mrb_str_new_cstr(mrb, s);
        } else {
            out = mrb_str_new_cstr(mrb, "Error");
        }
        mrb->exc = NULL;
    } else {
        out = result;
    }

    mrb_gc_arena_restore(mrb, ai);
    return out;
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

/**
 * Load and execute a Ruby source script.
 */
JNIEXPORT jstring JNICALL
Java_moe_bemly_mrboto_MRuby_nativeLoadScript(JNIEnv *env, jobject thiz,
                                       jlong mrbPtr, jstring script) {
    (void)thiz;

    mrb_state *mrb = (mrb_state *)(intptr_t)mrbPtr;
    if (mrb == NULL) return NULL;

    const char *c_script = (*env)->GetStringUTFChars(env, script, NULL);
    if (c_script == NULL) return NULL;

    int ai = mrb_gc_arena_save(mrb);
    mrb_value result = mrb_load_string(mrb, c_script);
    (*env)->ReleaseStringUTFChars(env, script, c_script);

    jstring jresult = NULL;
    if (mrb->exc) {
        mrb_value msg = mrb_funcall(mrb, mrb_obj_value(mrb->exc), "message", 0);
        if (mrb_string_p(msg)) {
            const char *s = mrb_string_value_cstr(mrb, &msg);
            char buf[512];
            snprintf(buf, sizeof(buf), "Error: %s", s);
            jresult = (*env)->NewStringUTF(env, buf);
        } else {
            jresult = (*env)->NewStringUTF(env, "Error: unknown error");
        }
        mrb->exc = NULL;
    } else if (mrb_string_p(result)) {
        const char *s = mrb_string_value_cstr(mrb, &result);
        jresult = (*env)->NewStringUTF(env, s);
    } else {
        /* Convert non-string result to string representation */
        mrb_value str = mrb_funcall(mrb, result, "to_s", 0);
        if (mrb_string_p(str)) {
            const char *s = mrb_string_value_cstr(mrb, &str);
            jresult = (*env)->NewStringUTF(env, s);
        }
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

#ifdef __cplusplus
}
#endif
