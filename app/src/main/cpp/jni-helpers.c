/*
 * jni-helpers.c — Android helper functions
 *
 * Provides:
 *   - View creation and event listeners
 *   - Lifecycle dispatch
 *   - Toast, start_activity, get_extra
 *   - SharedPreferences
 *   - App context, dp_to_px, package_name
 */

#include <jni.h>
#include <android/log.h>
#include <stdlib.h>
#include <string.h>
#include <mruby.h>
#include <mruby/data.h>
#include <mruby/hash.h>
#include <mruby/array.h>
#include <mruby/string.h>
#include <mruby/variable.h>

#include "android-jni-bridge.h"

#define LOG_TAG "mrboto-helpers"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

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
mrb_value mrb_mrboto_set_content_view(mrb_state *mrb, mrb_value self) {
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

mrb_value mrb_mrboto_toast(mrb_state *mrb, mrb_value self) {
    mrb_int context_id, duration;
    const char *msg;
    mrb_get_args(mrb, "izi", &context_id, &msg, &duration);
    mrboto_toast(mrb, (int)context_id, msg, (int)duration);
    return mrb_nil_value();
}

mrb_value mrb_mrboto_start_activity(mrb_state *mrb, mrb_value self) {
    mrb_int context_id;
    const char *cls_name;
    mrb_get_args(mrb, "iz", &context_id, &cls_name);
    mrboto_start_activity(mrb, (int)context_id, cls_name, mrb_nil_value());
    return mrb_nil_value();
}

mrb_value mrb_mrboto_get_extra(mrb_state *mrb, mrb_value self) {
    mrb_int activity_id;
    const char *key;
    mrb_get_args(mrb, "iz", &activity_id, &key);
    return mrboto_get_extra(mrb, (int)activity_id, key);
}

mrb_value mrb_mrboto_sp_get_string(mrb_state *mrb, mrb_value self) {
    mrb_int context_id;
    const char *name, *key, *default_val = NULL;
    mrb_get_args(mrb, "izzz", &context_id, &name, &key, &default_val);
    return mrboto_sp_get_string(mrb, (int)context_id, name, key, default_val);
}

mrb_value mrb_mrboto_sp_put_string(mrb_state *mrb, mrb_value self) {
    mrb_int context_id;
    const char *name, *key, *value;
    mrb_get_args(mrb, "izzz", &context_id, &name, &key, &value);
    mrboto_sp_put_string(mrb, (int)context_id, name, key, value);
    return mrb_nil_value();
}

mrb_value mrb_mrboto_sp_get_int(mrb_state *mrb, mrb_value self) {
    mrb_int context_id;
    const char *name, *key;
    mrb_int default_val = 0;
    mrb_get_args(mrb, "izz|i", &context_id, &name, &key, &default_val);
    (void)default_val;
    return mrb_nil_value();
}

mrb_value mrb_mrboto_sp_put_int(mrb_state *mrb, mrb_value self) {
    mrb_int context_id, value;
    const char *name, *key;
    mrb_get_args(mrb, "izzi", &context_id, &name, &key, &value);
    (void)value; /* stub — not yet implemented */
    return mrb_nil_value();
}

mrb_value mrb_mrboto_app_context(mrb_state *mrb, mrb_value self) {
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

mrb_value mrb_mrboto_create_view(mrb_state *mrb, mrb_value self) {
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

mrb_value mrb_mrboto_set_on_click(mrb_state *mrb, mrb_value self) {
    mrb_int view_id, callback_id;
    mrb_get_args(mrb, "ii", &view_id, &callback_id);
    mrboto_set_on_click((int)view_id, (int)callback_id);
    return mrb_nil_value();
}

mrb_value mrb_mrboto_run_on_ui_thread(mrb_state *mrb, mrb_value self) {
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
/* ── Helper: DP to PX conversion ──────────────────────────────────── */

mrb_value mrb_mrboto_dp_to_px(mrb_state *mrb, mrb_value self) {
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

mrb_value mrb_mrboto_package_name(mrb_state *mrb, mrb_value self) {
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

/* ── Helper: Get Android System Resource ID ───────────────────────── */

mrb_value mrb_mrboto_get_sys_res_id(mrb_state *mrb, mrb_value self) {
    mrb_int ctx_id;
    const char *name;
    const char *type;
    mrb_get_args(mrb, "izz", &ctx_id, &name, &type);

    JNIEnv *env = mrboto_get_env();
    if (env == NULL) return mrb_fixnum_value(0);

    jobject ctx = mrboto_lookup_ref(env, (int)ctx_id);
    if (ctx == NULL) return mrb_fixnum_value(0);

    /* Get Resources object */
    jclass ctx_cls = (*env)->GetObjectClass(env, ctx);
    jmethodID get_res = (*env)->GetMethodID(env, ctx_cls, "getResources",
                                            "()Landroid/content/res/Resources;");
    if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); (*env)->DeleteLocalRef(env, ctx_cls); return mrb_fixnum_value(0); }
    jobject res = (*env)->CallObjectMethod(env, ctx, get_res);
    if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); (*env)->DeleteLocalRef(env, ctx_cls); return mrb_fixnum_value(0); }
    (*env)->DeleteLocalRef(env, ctx_cls);

    /* Get Resources.getIdentifier(String name, String defType, String defPackage) */
    jclass res_cls = (*env)->GetObjectClass(env, res);
    jmethodID get_id = (*env)->GetMethodID(env, res_cls, "getIdentifier",
                                           "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)I");
    if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); (*env)->DeleteLocalRef(env, res_cls); (*env)->DeleteLocalRef(env, res); return mrb_fixnum_value(0); }

    jstring jname = (*env)->NewStringUTF(env, name);
    jstring jtype = (*env)->NewStringUTF(env, type);
    jstring jpkg = (*env)->NewStringUTF(env, "android");
    jint id = (*env)->CallIntMethod(env, res, get_id, jname, jtype, jpkg);
    if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); }

    (*env)->DeleteLocalRef(env, jname);
    (*env)->DeleteLocalRef(env, jtype);
    (*env)->DeleteLocalRef(env, jpkg);
    (*env)->DeleteLocalRef(env, res_cls);
    (*env)->DeleteLocalRef(env, res);

    return mrb_fixnum_value((mrb_int)id);
}

/* ── Java Object Registry Methods ─────────────────────────────────── */

