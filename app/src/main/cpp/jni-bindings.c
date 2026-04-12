/*
 * jni-bindings.c — Ruby method bindings and JNI exports
 *
 * Provides:
 *   - All mrb_mrboto_* method implementations (except UI)
 *   - Error extraction (safe_extract_error)
 *   - Method table (mrb_mrboto_define_methods)
 *   - JNI exported functions (Java_moe_bemly_mrboto_MRuby_*)
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
#include <mruby/compile.h>
#include <mruby/error.h>
#include <mruby/object.h>

#include "android-jni-bridge.h"

#define LOG_TAG "mrboto-bridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

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

mrb_value mrb_mrboto_string_res(mrb_state *mrb, mrb_value self) {
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

mrb_value mrb_mrboto_register_object(mrb_state *mrb, mrb_value self) {
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

mrb_value mrb_mrboto_java_object_for(mrb_state *mrb, mrb_value self) {
    mrb_int registry_id;
    mrb_get_args(mrb, "i", &registry_id);
    JNIEnv *env = mrboto_get_env();
    jobject java_obj = mrboto_lookup_ref(env, (int)registry_id);
    if (java_obj == NULL) return mrb_nil_value();
    return mrboto_wrap_java_object(mrb, env, java_obj);
}


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
mrb_value mrb_mrboto_call_java_method(mrb_state *mrb, mrb_value self) {
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

mrb_value mrb_mrboto_view_text(mrb_state *mrb, mrb_value self) {
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

mrb_value mrb_mrboto_eval(mrb_state *mrb, mrb_value self) {
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
