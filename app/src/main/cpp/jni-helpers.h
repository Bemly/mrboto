/*
 * jni-helpers.h — Android helper functions API
 */

#ifndef JNI_HELPERS_H
#define JNI_HELPERS_H

#include <mruby.h>

/* ── C helper functions (called from other C modules) ────────────── */

int mrboto_create_view(mrb_state *mrb, int context_id, const char *class_name, mrb_value attrs);
void mrboto_set_on_click(int view_id, int callback_id);
void mrboto_set_text_watcher(int view_id, int callback_id);
void mrboto_dispatch_lifecycle(mrb_state *mrb, int activity_id, const char *callback_name, int args_id);
void mrboto_toast(mrb_state *mrb, int context_id, const char *msg, int duration);
void mrboto_start_activity(mrb_state *mrb, int context_id, const char *cls_name, mrb_value extras);
mrb_value mrboto_get_extra(mrb_state *mrb, int activity_id, const char *key);
mrb_value mrboto_sp_get_string(mrb_state *mrb, int context_id, const char *name, const char *key, const char *default_val);
void mrboto_sp_put_string(mrb_state *mrb, int context_id, const char *name, const char *key, const char *value);
jobject mrboto_get_app_context(mrb_state *mrb);

/* ── Ruby method bindings (called from mrb_mrboto_define_methods) ── */

mrb_value mrb_mrboto_set_content_view(mrb_state *mrb, mrb_value self);
mrb_value mrb_mrboto_toast(mrb_state *mrb, mrb_value self);
mrb_value mrb_mrboto_start_activity(mrb_state *mrb, mrb_value self);
mrb_value mrb_mrboto_get_extra(mrb_state *mrb, mrb_value self);
mrb_value mrb_mrboto_sp_get_string(mrb_state *mrb, mrb_value self);
mrb_value mrb_mrboto_sp_put_string(mrb_state *mrb, mrb_value self);
mrb_value mrb_mrboto_sp_get_int(mrb_state *mrb, mrb_value self);
mrb_value mrb_mrboto_sp_put_int(mrb_state *mrb, mrb_value self);
mrb_value mrb_mrboto_app_context(mrb_state *mrb, mrb_value self);
mrb_value mrb_mrboto_create_view(mrb_state *mrb, mrb_value self);
mrb_value mrb_mrboto_set_on_click(mrb_state *mrb, mrb_value self);
mrb_value mrb_mrboto_run_on_ui_thread(mrb_state *mrb, mrb_value self);
mrb_value mrb_mrboto_dp_to_px(mrb_state *mrb, mrb_value self);
mrb_value mrb_mrboto_package_name(mrb_state *mrb, mrb_value self);
mrb_value mrb_mrboto_get_sys_res_id(mrb_state *mrb, mrb_value self);

#endif /* JNI_HELPERS_H */
