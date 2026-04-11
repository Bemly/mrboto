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

#include "android-jni-bridge.h"
#include <string.h>

#define LOG_TAG "mrboto-bridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

/* ── Global State ─────────────────────────────────────────────────── */

static JavaVM *g_java_vm = NULL;
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
    struct RData *rdata = mrb_data_object_alloc(mrb, NULL, data, &mrboto_java_object_type);
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

/* ── Helper: Call Java Method via Reflection ──────────────────────── */

static mrb_value mrboto_call_java_method(mrb_state *mrb, int obj_id,
                                          const char *method_name, int argc,
                                          mrb_value *argv, const char *ret_type) {
    JNIEnv *env = mrboto_get_env();
    jobject obj = mrboto_lookup_ref(env, obj_id);
    if (obj == NULL) return mrb_nil_value();

    jclass cls = (*env)->GetObjectClass(env, obj);
    jmethodID mid = (*env)->GetMethodID(env, cls, method_name, "()V"); /* stub */

    /* Build JNI method signature from return type and argument types */
    char sig[256] = "(";
    int sig_len = 1;
    for (int i = 0; i < argc; i++) {
        if (mrb_integer_p(argv[i])) {
            sig[sig_len++] = 'I';
        } else if (mrb_string_p(argv[i])) {
            sig[sig_len++] = 'L'; sig[sig_len++] = 'j'; sig[sig_len++] = 'a';
            sig[sig_len++] = 'v'; sig[sig_len++] = 'a'; sig[sig_len++] = '/';
            sig[sig_len++] = 'l'; sig[sig_len++] = 'a'; sig[sig_len++] = 'n';
            sig[sig_len++] = 'g'; sig[sig_len++] = '/'; sig[sig_len++] = 'S';
            sig[sig_len++] = 't'; sig[sig_len++] = 'r'; sig[sig_len++] = 'i';
            sig[sig_len++] = 'n'; sig[sig_len++] = 'g'; sig[sig_len++] = ';';
        } else if (mrb_true_p(argv[i]) || mrb_false_p(argv[i])) {
            sig[sig_len++] = 'Z';
        } else {
            sig[sig_len++] = 'L'; sig[sig_len++] = 'j'; sig[sig_len++] = 'a';
            sig[sig_len++] = 'v'; sig[sig_len++] = 'a'; sig[sig_len++] = '/';
            sig[sig_len++] = 'l'; sig[sig_len++] = 'a'; sig[sig_len++] = 'n';
            sig[sig_len++] = 'g'; sig[sig_len++] = '/'; sig[sig_len++] = 'O';
            sig[sig_len++] = 'b'; sig[sig_len++] = 'j'; sig[sig_len++] = 'e';
            sig[sig_len++] = 'c'; sig[sig_len++] = 't'; sig[sig_len++] = ';';
        }
    }
    sig[sig_len++] = ')';
    if (ret_type) {
        strcat(sig + sig_len, ret_type);
    } else {
        sig[sig_len++] = 'V';
        sig[sig_len] = '\0';
    }

    mid = (*env)->GetMethodID(env, cls, method_name, sig);
    if (mid == NULL) {
        LOGE("Method not found: %s signature: %s", method_name, sig);
        (*env)->DeleteLocalRef(env, cls);
        return mrb_nil_value();
    }

    /* Build jvalue array for arguments */
    jvalue *jargs = (jvalue *)calloc(argc > 0 ? argc : 1, sizeof(jvalue));
    for (int i = 0; i < argc; i++) {
        if (mrb_integer_p(argv[i])) {
            jargs[i].i = (jint)mrb_integer(argv[i]);
        } else if (mrb_string_p(argv[i])) {
            const char *s = mrb_string_value_cstr(mrb, &argv[i]);
            jargs[i].l = (*env)->NewStringUTF(env, s);
        } else if (mrb_true_p(argv[i])) {
            jargs[i].z = JNI_TRUE;
        } else if (mrb_false_p(argv[i])) {
            jargs[i].z = JNI_FALSE;
        }
    }

    mrb_value result = mrb_nil_value();

    if (ret_type == NULL || ret_type[0] == 'V') {
        (*env)->CallVoidMethodA(env, obj, mid, jargs);
    } else if (ret_type[0] == 'I') {
        jint r = (*env)->CallIntMethodA(env, obj, mid, jargs);
        result = mrb_fixnum_value((mrb_int)r);
    } else if (ret_type[0] == 'Z') {
        jboolean r = (*env)->CallBooleanMethodA(env, obj, mid, jargs);
        result = r ? mrb_true_value() : mrb_false_value();
    } else if (ret_type[0] == 'L') {
        jobject r = (*env)->CallObjectMethodA(env, obj, mid, jargs);
        if (r != NULL) {
            result = mrboto_wrap_java_object(mrb, env, r);
            /* Release local ref for string results */
            if (ret_type[0] == 'L' && strstr(ret_type, "String")) {
                (*env)->DeleteLocalRef(env, r);
            }
        }
    } else if (ret_type[0] == 'F') {
        jfloat r = (*env)->CallFloatMethodA(env, obj, mid, jargs);
        result = mrb_float_value(mrb, (mrb_float)r);
    }

    /* Clean up local refs for string arguments */
    for (int i = 0; i < argc; i++) {
        if (mrb_string_p(argv[i])) {
            if (jargs[i].l != NULL) {
                (*env)->DeleteLocalRef(env, jargs[i].l);
            }
        }
    }
    free(jargs);
    (*env)->DeleteLocalRef(env, cls);

    return result;
}

/* ── View Creation ────────────────────────────────────────────────── */

int mrboto_create_view(mrb_state *mrb, int context_id, const char *class_name,
                       mrb_value attrs) {
    JNIEnv *env = mrboto_get_env();
    jobject context = mrboto_lookup_ref(env, context_id);
    if (context == NULL) return 0;

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
        return 0;
    }

    /* Get constructor: (Context) */
    jmethodID cid = (*env)->GetMethodID(env, cls, "<init>",
                                        "(Landroid/content/Context;)V");
    if (cid == NULL) {
        LOGE("Constructor not found for %s", jni_class);
        (*env)->DeleteLocalRef(env, cls);
        return 0;
    }

    jobject view = (*env)->NewObject(env, cls, cid, context);
    int view_id = 0;
    if (view != NULL) {
        view_id = mrboto_register_ref(env, view);
        (*env)->DeleteLocalRef(env, view);
    }

    /* Apply attributes from hash if provided */
    if (view_id > 0 && mrb_hash_p(attrs)) {
        /* We'll apply attributes via separate JNI calls after wrapping */
        /* For now, just register the view */
    }

    (*env)->DeleteLocalRef(env, cls);
    return view_id;
}

/* ── Event Listeners (via eval back into mruby) ───────────────────── */

void mrboto_set_on_click(int view_id, int callback_id) {
    JNIEnv *env = mrboto_get_env();
    jobject view = mrboto_lookup_ref(env, view_id);
    if (view == NULL || env == NULL) return;

    /*
     * Set the callback ID as a tag on the View.
     * The actual OnClickListener is set by the Kotlin side via ViewListeners.
     * We store the callback ID for reference.
     */
    jclass view_cls = (*env)->GetObjectClass(env, view);
    jmethodID setTag = (*env)->GetMethodID(env, view_cls, "setTag", "(Ljava/lang/Object;)V");
    if (setTag != NULL) {
        jclass integer_cls = (*env)->FindClass(env, "java/lang/Integer");
        jmethodID int_init = (*env)->GetMethodID(env, integer_cls, "<init>", "(I)V");
        jobject int_obj = (*env)->NewObject(env, integer_cls, int_init, (jint)callback_id);
        (*env)->CallVoidMethod(env, view, setTag, int_obj);
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
    if (toast_cls == NULL) return;

    jmethodID make_text = (*env)->GetStaticMethodID(env, toast_cls, "makeText",
        "(Landroid/content/Context;Ljava/lang/CharSequence;I)Landroid/widget/Toast;");
    if (make_text == NULL) {
        (*env)->DeleteLocalRef(env, toast_cls);
        return;
    }

    jstring jmsg = (*env)->NewStringUTF(env, msg);
    jobject toast = (*env)->CallStaticObjectMethod(env, toast_cls, make_text,
                                                   context, jmsg, (jint)duration);

    if (toast != NULL) {
        jmethodID show = (*env)->GetMethodID(env, toast_cls, "show", "()V");
        if (show) (*env)->CallVoidMethod(env, toast, show);
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
    jmethodID get_pkg = (*env)->GetMethodID(env, context_cls, "getPackageName", "()Ljava/lang/String;");
    jstring pkg = (jstring)(*env)->CallObjectMethod(env, context, get_pkg);

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
    (*env)->CallObjectMethod(env, intent, set_comp, cn);

    /* Start activity */
    jmethodID start = (*env)->GetMethodID(env, context_cls, "startActivity",
                                          "(Landroid/content/Intent;)V");
    (*env)->CallVoidMethod(env, context, start, intent);

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
    jobject intent = (*env)->CallObjectMethod(env, activity, get_intent);
    if (intent == NULL) return mrb_nil_value();

    jclass intent_cls = (*env)->GetObjectClass(env, intent);
    jmethodID get_extras = (*env)->GetMethodID(env, intent_cls, "getExtras",
                                               "()Landroid/os/Bundle;");
    jobject bundle = (*env)->CallObjectMethod(env, intent, get_extras);

    mrb_value result = mrb_nil_value();
    if (bundle != NULL) {
        jclass bundle_cls = (*env)->GetObjectClass(env, bundle);
        jmethodID get_str = (*env)->GetMethodID(env, bundle_cls, "getString",
                                                "(Ljava/lang/String;)Ljava/lang/String;");
        jstring jkey = (*env)->NewStringUTF(env, key);
        jstring jval = (jstring)(*env)->CallObjectMethod(env, bundle, get_str, jkey);

        if (jval != NULL) {
            const char *s = (*env)->GetStringUTFChars(env, jval, NULL);
            result = mrb_str_new_cstr(mrb, s);
            (*env)->ReleaseStringUTFChars(env, jval, s);
            (*env)->DeleteLocalRef(env, jval);
        }
        (*env)->DeleteLocalRef(env, jkey);
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
    jmethodID get_sp = (*env)->GetMethodID(env, ctx_cls, "getSharedPreferences",
                                           "(Ljava/lang/String;I)Landroid/content/SharedPreferences;");
    jstring jname = (*env)->NewStringUTF(env, name);
    jobject sp = (*env)->CallObjectMethod(env, context, get_sp, jname, (jint)0 /* MODE_PRIVATE */);
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
        jstring jkey = (*env)->NewStringUTF(env, key);
        jstring jdef = default_val ? (*env)->NewStringUTF(env, default_val) : NULL;
        jstring jval = (jstring)(*env)->CallObjectMethod(env, sp, get, jkey, jdef);

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
        jobject editor = (*env)->CallObjectMethod(env, sp, edit);

        jclass ed_cls = (*env)->GetObjectClass(env, editor);
        jmethodID put = (*env)->GetMethodID(env, ed_cls, "putString",
                                            "(Ljava/lang/String;Ljava/lang/String;)Landroid/content/SharedPreferences$Editor;");
        jstring jkey = (*env)->NewStringUTF(env, key);
        jstring jval = (*env)->NewStringUTF(env, value);
        (*env)->CallObjectMethod(env, editor, put, jkey, jval);

        jmethodID apply = (*env)->GetMethodID(env, ed_cls, "apply", "()V");
        (*env)->CallVoidMethod(env, editor, apply);

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

    /* Get current ActivityThread's Application */
    jclass at_cls = (*env)->FindClass(env, "android/app/ActivityThread");
    if (at_cls == NULL) return NULL;

    jmethodID current = (*env)->GetStaticMethodID(env, at_cls, "currentActivityThread",
                                                  "()Landroid/app/ActivityThread;");
    jobject at = (*env)->CallStaticObjectMethod(env, at_cls, current);

    jmethodID get_app = (*env)->GetMethodID(env, at_cls, "getApplication",
                                            "()Landroid/app/Application;");
    jobject app = (*env)->CallObjectMethod(env, at, get_app);

    (*env)->DeleteLocalRef(env, at);
    (*env)->DeleteLocalRef(env, at_cls);

    return app;
}

/* ── Native Methods (called from Kotlin MRuby.kt) ─────────────────── */

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Register Android classes in mruby (Mrboto module, JavaObject, etc.)
 */
JNIEXPORT void JNICALL
Java_com_mrboto_MRuby_nativeRegisterAndroidClasses(JNIEnv *env, jobject thiz, jlong mrbPtr) {
    (void)thiz;
    (void)env;

    mrb_state *mrb = (mrb_state *)(intptr_t)mrbPtr;

    /* Define Mrboto module */
    struct RClass *mrboto = mrb_define_module(mrb, "Mrboto");

    /* Define JavaObject class under Mrboto */
    struct RClass *java_obj = mrb_define_class_under(mrb, mrboto, "JavaObject", mrb->object_class);
    (void)java_obj; /* methods defined on Ruby side via core.rb */

    LOGI("Android classes registered in mruby");
}

/**
 * Dispatch lifecycle callback to Ruby Activity.
 * Evaluates: Mrboto.current_activity.on_xxx(bundle)
 */
JNIEXPORT jstring JNICALL
Java_com_mrboto_MRuby_nativeDispatchLifecycle(JNIEnv *env, jobject thiz,
                                              jlong mrbPtr, jint activityId,
                                              jstring callbackName, jint argsId) {
    (void)thiz;

    mrb_state *mrb = (mrb_state *)(intptr_t)mrbPtr;
    if (mrb == NULL) return NULL;

    int ai = mrb_gc_arena_save(mrb);

    /* Convert callbackName from jstring to C string */
    const char *cname = (*env)->GetStringUTFChars(env, callbackName, NULL);

    /* Get Mrboto.current_activity */
    mrb_value mrboto_mod = mrb_const_get(mrb, mrb_obj_value(mrb->object_class),
                                         mrb_intern_lit(mrb, "Mrboto"));
    mrb_value activity = mrb_const_get(mrb, mrboto_mod, mrb_intern_lit(mrb, "current_activity"));

    jstring result = NULL;
    if (!mrb_nil_p(activity)) {
        /* Get the bundle argument if provided */
        mrb_value bundle_val = mrb_nil_value();
        if (argsId > 0) {
            JNIEnv *env2 = mrboto_get_env();
            jobject bundle = mrboto_lookup_ref(env2, (int)argsId);
            if (bundle != NULL) {
                bundle_val = mrboto_wrap_java_object(mrb, env2, bundle);
            }
        }

        /* Call the method on the activity: activity.on_xxx(bundle) */
        mrb_funcall(mrb, activity, cname, 1, bundle_val);

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
Java_com_mrboto_MRuby_nativeLoadScript(JNIEnv *env, jobject thiz,
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
            jresult = (*env)->NewStringUTF(env, s);
        }
        mrb->exc = NULL;
    } else {
        jresult = (*env)->NewStringUTF(env, "ok");
    }

    mrb_gc_arena_restore(mrb, ai);
    return jresult;
}

/**
 * Set OnClickListener on a View with a mruby callback ID.
 * Implemented by evaluating Ruby code that uses the Kotlin ViewListener.
 */
JNIEXPORT void JNICALL
Java_com_mrboto_MRuby_nativeSetOnClick(JNIEnv *env, jobject thiz,
                                       jlong mrbPtr, jint viewId, jint callbackId) {
    (void)thiz;
    mrb_state *mrb = (mrb_state *)(intptr_t)mrbPtr;
    (void)mrb;

    JNIEnv *env2 = mrboto_get_env();
    jobject view = mrboto_lookup_ref(env2, (int)viewId);
    if (view == NULL) return;

    /*
     * Set the callback ID as a tag on the View.
     * The Kotlin MrbotoClickListener (set globally) will read this tag.
     * We use the View's setTag(Object) method.
     */
    jmethodID setTag = (*env2)->GetMethodID(env2, (*env2)->GetObjectClass(env2, view),
                                            "setTag", "(Ljava/lang/Object;)V");
    /* Create an Integer object for the callback ID */
    jclass integer_cls = (*env2)->FindClass(env2, "java/lang/Integer");
    jmethodID int_init = (*env2)->GetMethodID(env2, integer_cls, "<init>", "(I)V");
    jobject int_obj = (*env2)->NewObject(env2, integer_cls, int_init, (jint)callbackId);
    (*env2)->CallVoidMethod(env2, view, setTag, int_obj);

    /*
     * Now set the actual click listener. We need a Java OnClickListener.
     * This is done by having the Kotlin MrbotoApplication register a global
     * OnClickListener factory. For now, we eval back into mruby to set it up.
     */
    /* The actual listener setup is handled by Kotlin side via ViewListeners */
    LOGD("nativeSetOnClick: view=%d callback=%d", viewId, callbackId);

    (*env2)->DeleteLocalRef(env2, int_obj);
    (*env2)->DeleteLocalRef(env2, integer_cls);
}

/**
 * Look up a Java object by its registry ID.
 * Returns the Java object or null.
 */
JNIEXPORT jobject JNICALL
Java_com_mrboto_MRuby_nativeLookupObject(JNIEnv *env, jobject thiz,
                                         jlong mrbPtr, jint registryId) {
    (void)thiz;
    (void)mrbPtr;
    return mrboto_lookup_ref(env, (int)registryId);
}

#ifdef __cplusplus
}
#endif
