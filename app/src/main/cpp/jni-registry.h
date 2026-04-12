/*
 * jni-registry.h — GlobalRef registry and JavaObject wrapping API
 */

#ifndef JNI_REGISTRY_H
#define JNI_REGISTRY_H

#include <jni.h>
#include <mruby.h>

/* ── Registry Structure ───────────────────────────────────────────── */

#define MRBOTO_REGISTRY_MAX 4096

typedef struct {
    jobject  refs[MRBOTO_REGISTRY_MAX];
    int      used[MRBOTO_REGISTRY_MAX];
    int      next_id;
} mrboto_ref_registry_t;

/* Get the singleton registry */
mrboto_ref_registry_t *mrboto_registry(void);

/* Get cached JavaVM / JNIEnv */
JavaVM *mrboto_get_java_vm(void);
JNIEnv *mrboto_get_env(void);

/* Register a JNI GlobalRef and return a unique integer ID */
int mrboto_register_ref(JNIEnv *env, jobject obj);

/* Look up a registered object by ID (returns the GlobalRef, no new ref) */
jobject mrboto_lookup_ref(JNIEnv *env, int id);

/* Unregister and DeleteGlobalRef */
void mrboto_unregister_ref(JNIEnv *env, int id);

/* Clear ALL used registry entries (used between tests) */
void mrboto_clear_registry(JNIEnv *env);

/* Wrap a Java object as an mruby Mrboto::JavaObject (returns mrb_value) */
mrb_value mrboto_wrap_java_object(mrb_state *mrb, JNIEnv *env, jobject obj);

/* Unwrap an Mrboto::JavaObject to get the JNI GlobalRef */
jobject mrboto_unwrap_java_object(mrb_state *mrb, mrb_value val, JNIEnv **out_env);

/* Register a Java object and return its registry ID */
int mrboto_register_java_object(mrb_state *mrb, JNIEnv *env, jobject obj);

/* The mruby class for JavaObject — needed for mrb_data_object_alloc */
extern struct RClass *g_java_object_class;

#endif /* JNI_REGISTRY_H */
