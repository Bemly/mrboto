/*
 * jni-ui.h — UI helper Ruby method bindings
 */

#ifndef JNI_UI_H
#define JNI_UI_H

#include <mruby.h>

/* ── Ruby method bindings (called from mrb_mrboto_define_methods) ── */

mrb_value mrb_mrboto_show_dialog(mrb_state *mrb, mrb_value self);
mrb_value mrb_mrboto_show_snackbar(mrb_state *mrb, mrb_value self);
mrb_value mrb_mrboto_show_popup_menu(mrb_state *mrb, mrb_value self);
mrb_value mrb_mrboto_animate_fade(mrb_state *mrb, mrb_value self);
mrb_value mrb_mrboto_animate_translate(mrb_state *mrb, mrb_value self);
mrb_value mrb_mrboto_animate_scale(mrb_state *mrb, mrb_value self);

#endif /* JNI_UI_H */
