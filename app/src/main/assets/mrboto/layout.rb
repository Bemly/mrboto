# mrboto/layout.rb — Layout constants and DP conversion

module Mrboto
  module LayoutParams
    MATCH_PARENT = -1
    FILL_PARENT  = -1
    WRAP_CONTENT = -2
  end

  module Gravity
    CENTER         = 17
    CENTER_VERTICAL = 16
    CENTER_HORIZONTAL = 1
    TOP            = 48
    BOTTOM         = 80
    LEFT           = 3
    RIGHT          = 5
  end

  module Orientation
    VERTICAL   = 1
    HORIZONTAL = 0
  end

  # ── Top-level constants ──────────────────────────────────────────
  MATCH_PARENT = Mrboto::LayoutParams::MATCH_PARENT
  FILL_PARENT  = Mrboto::LayoutParams::FILL_PARENT
  WRAP_CONTENT = Mrboto::LayoutParams::WRAP_CONTENT
end

# ── DP to PX ────────────────────────────────────────────────────────
def dp(value)
  ctx = Mrboto._app_context
  return (value * 1.5 + 0.5).to_i unless ctx
  # Use real display metrics: density = getResources().getDisplayMetrics().density
  display_metrics = ctx.call_java_method("getResources").call_java_method("getDisplayMetrics")
  density = display_metrics.call_java_method("getDensity") rescue 1.5
  (value * density + 0.5).to_i
end
