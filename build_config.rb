# mrboto - mruby 4.0.0 build configuration for Android
#
# This file configures mruby cross-compilation for Android NDK.
#
# Usage:
#   export ANDROID_NDK_HOME=/path/to/ndk
#   rake
#
# Then copy the built libraries:
#   mkdir -p app/src/main/cpp/mruby/lib/arm64-v8a
#   cp build/android-arm64-v8a/lib/libmruby.a app/src/main/cpp/mruby/lib/arm64-v8a/
#   mkdir -p app/src/main/cpp/mruby/lib/x86_64
#   cp build/android-x86_64/lib/libmruby.a app/src/main/cpp/mruby/lib/x86_64/

# ── Host build (for mrbc tool to precompile .rb -> .mrb) ──
MRuby::Build.new do |conf|
  toolchain :gcc
  conf.gembox 'default'
end

# ── Android arm64-v8a cross-build ──
MRuby::CrossBuild.new('android-arm64-v8a') do |conf|
  toolchain :android, :arch => 'arm64-v8a', :platform => 'android-33'

  # Standard library + math (no CLI tools for smaller binary)
  conf.gembox 'stdlib'
  conf.gembox 'stdlib-ext'
  conf.gembox 'math'
  conf.gembox 'metaprog'

  # stdlib-io requires MRB_NO_STDIO to be unset (default), which is fine for
  # mruby's internal string I/O (StringIO-like operations). It does NOT require
  # actual file descriptors on Android.
  conf.gembox 'stdlib-io'

  # Extra gems
  conf.gem :core => "mruby-encoding"
  conf.gem :core => "mruby-binding"
  conf.gem :core => "mruby-catch"
  conf.gem :core => "mruby-enum-chain"
  conf.gem :core => "mruby-strftime"
end

# ── Android x86_64 cross-build ──
MRuby::CrossBuild.new('android-x86_64') do |conf|
  toolchain :android, :arch => 'x86_64', :platform => 'android-33'

  conf.gembox 'stdlib'
  conf.gembox 'stdlib-ext'
  conf.gembox 'math'
  conf.gembox 'metaprog'
  conf.gembox 'stdlib-io'

  # Extra gems
  conf.gem :core => "mruby-encoding"
  conf.gem :core => "mruby-binding"
  conf.gem :core => "mruby-catch"
  conf.gem :core => "mruby-enum-chain"
  conf.gem :core => "mruby-strftime"
end
