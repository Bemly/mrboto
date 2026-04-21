/*
 * jni-features.h — C JNI implementations for new API modules
 *
 * Clipboard, Intent Extras, File Ops, Permission, Notification, SQLite, Network
 */

#ifndef JNI_FEATURES_H
#define JNI_FEATURES_H

#include <mruby.h>

/* ── Clipboard ──────────────────────────────────────────────────── */
mrb_value mrb_mrboto_clipboard_copy(mrb_state *mrb, mrb_value self);
mrb_value mrb_mrboto_clipboard_paste(mrb_state *mrb, mrb_value self);
mrb_value mrb_mrboto_clipboard_has_text(mrb_state *mrb, mrb_value self);

/* ── Intent Extras ──────────────────────────────────────────────── */
mrb_value mrb_mrboto_get_extra_int(mrb_state *mrb, mrb_value self);
mrb_value mrb_mrboto_get_extra_bool(mrb_state *mrb, mrb_value self);
mrb_value mrb_mrboto_get_extra_float(mrb_state *mrb, mrb_value self);
mrb_value mrb_mrboto_get_all_extras(mrb_state *mrb, mrb_value self);

/* ── File Operations ────────────────────────────────────────────── */
mrb_value mrb_mrboto_file_write(mrb_state *mrb, mrb_value self);
mrb_value mrb_mrboto_file_read(mrb_state *mrb, mrb_value self);
mrb_value mrb_mrboto_file_exists(mrb_state *mrb, mrb_value self);
mrb_value mrb_mrboto_file_delete(mrb_state *mrb, mrb_value self);
mrb_value mrb_mrboto_file_list(mrb_state *mrb, mrb_value self);
mrb_value mrb_mrboto_file_size(mrb_state *mrb, mrb_value self);
mrb_value mrb_mrboto_external_file_write(mrb_state *mrb, mrb_value self);
mrb_value mrb_mrboto_external_file_read(mrb_state *mrb, mrb_value self);
mrb_value mrb_mrboto_cache_write(mrb_state *mrb, mrb_value self);
mrb_value mrb_mrboto_cache_read(mrb_state *mrb, mrb_value self);

/* ── Permission ─────────────────────────────────────────────────── */
mrb_value mrb_mrboto_check_permission(mrb_state *mrb, mrb_value self);

/* ── Notification ───────────────────────────────────────────────── */
mrb_value mrb_mrboto_notify_show(mrb_state *mrb, mrb_value self);
mrb_value mrb_mrboto_notify_cancel(mrb_state *mrb, mrb_value self);
mrb_value mrb_mrboto_notify_big(mrb_state *mrb, mrb_value self);
mrb_value mrb_mrboto_notify_progress(mrb_state *mrb, mrb_value self);

/* ── SQLite ─────────────────────────────────────────────────────── */
mrb_value mrb_mrboto_sqlite_open(mrb_state *mrb, mrb_value self);
mrb_value mrb_mrboto_sqlite_execute(mrb_state *mrb, mrb_value self);
mrb_value mrb_mrboto_sqlite_insert(mrb_state *mrb, mrb_value self);
mrb_value mrb_mrboto_sqlite_query(mrb_state *mrb, mrb_value self);
mrb_value mrb_mrboto_sqlite_update(mrb_state *mrb, mrb_value self);
mrb_value mrb_mrboto_sqlite_delete(mrb_state *mrb, mrb_value self);
mrb_value mrb_mrboto_sqlite_close(mrb_state *mrb, mrb_value self);

/* ── Network ────────────────────────────────────────────────────── */
mrb_value mrb_mrboto_http_get(mrb_state *mrb, mrb_value self);
mrb_value mrb_mrboto_http_post(mrb_state *mrb, mrb_value self);
mrb_value mrb_mrboto_http_download(mrb_state *mrb, mrb_value self);

#endif /* JNI_FEATURES_H */
