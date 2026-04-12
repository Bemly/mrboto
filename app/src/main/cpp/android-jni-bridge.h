/*
 * android-jni-bridge.h — JNI bridge between mruby and Android Java APIs
 *
 * This header provides:
 *   - Global JNI reference registry (integer IDs ↔ jobject)
 *   - Type marshaling helpers
 *   - Function declarations for native methods
 */

#ifndef ANDROID_JNI_BRIDGE_H
#define ANDROID_JNI_BRIDGE_H

#include <jni.h>
#include <mruby.h>

/* ── Reference Registry ───────────────────────────────────────────── */

#define MRBOTO_REGISTRY_MAX 4096

typedef struct {
    jobject  refs[MRBOTO_REGISTRY_MAX];
    int      used[MRBOTO_REGISTRY_MAX];
    int      next_id;
} mrboto_ref_registry_t;

/* Get the singleton registry (initialized in JNI_OnLoad) */
mrboto_ref_registry_t *mrboto_registry(void);

/* Register a JNI GlobalRef and return a unique integer ID */
int mrboto_register_ref(JNIEnv *env, jobject obj);

/* Look up a registered object by ID (returns the GlobalRef, no new ref) */
jobject mrboto_lookup_ref(JNIEnv *env, int id);

/* Unregister and DeleteGlobalRef */
void mrboto_unregister_ref(JNIEnv *env, int id);

/* Clear ALL used registry entries (used between tests) */
void mrboto_clear_registry(JNIEnv *env);

/* ── JNI Environment ──────────────────────────────────────────────── */

/* Get cached JavaVM pointer (set in JNI_OnLoad) */
JavaVM *mrboto_get_java_vm(void);

/* Attach to current thread and get JNIEnv */
JNIEnv *mrboto_get_env(void);

/* ── mruby Integration ────────────────────────────────────────────── */

/* Wrap a Java object as an mruby Mrboto::JavaObject (returns mrb_value) */
mrb_value mrboto_wrap_java_object(mrb_state *mrb, JNIEnv *env, jobject obj);

/* Unwrap an Mrboto::JavaObject to get the JNI GlobalRef */
jobject mrboto_unwrap_java_object(mrb_state *mrb, mrb_value val, JNIEnv **out_env);

/* Register a Java object and return its registry ID */
int mrboto_register_java_object(mrb_state *mrb, JNIEnv *env, jobject obj);

/* ── Lifecycle Dispatcher ─────────────────────────────────────────── */

/* Dispatch a lifecycle callback to a Ruby Activity instance */
void mrboto_dispatch_lifecycle(mrb_state *mrb, int activity_id,
                               const char *callback_name, int args_id);

/* ── View Creation ────────────────────────────────────────────────── */

/* Create an Android View via JNI and return its registry ID */
int mrboto_create_view(mrb_state *mrb, int context_id, const char *class_name,
                       mrb_value attrs);

/* Set OnClickListener on a View, storing the callback ID */
void mrboto_set_on_click(int view_id, int callback_id);

/* Set TextWatcher on an EditText, storing the callback ID */
void mrboto_set_text_watcher(int view_id, int callback_id);

/* ── Helper Functions ─────────────────────────────────────────────── */

/* Show a Toast message */
void mrboto_toast(mrb_state *mrb, int context_id, const char *msg, int duration);

/* Start an Activity by class name with optional extras hash */
void mrboto_start_activity(mrb_state *mrb, int context_id, const char *cls_name,
                           mrb_value extras);

/* Get a String extra from the current Activity's Intent */
mrb_value mrboto_get_extra(mrb_state *mrb, int activity_id, const char *key);

/* SharedPreferences helpers */
mrb_value mrboto_sp_get_string(mrb_state *mrb, int context_id,
                               const char *name, const char *key, const char *default_val);
void mrboto_sp_put_string(mrb_state *mrb, int context_id,
                          const char *name, const char *key, const char *value);

/* Get current application context */
jobject mrboto_get_app_context(mrb_state *mrb);

#endif /* ANDROID_JNI_BRIDGE_H */
