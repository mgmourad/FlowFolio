# Project-specific R8 rules. Libraries ship their own consumer rules.

# ML Kit (translate and language-id) reaches into its own options/model classes by reflection; R8's
# optimization renames those fields and leaves nulls behind (a NullPointerException in the translate
# flow). Keep its classes whole so translation works in the minified release build (owner, 2026-11-07).
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# sherpa-onnx (Narrator Mode's Kokoro TTS) binds its Kotlin API to native code by class and method
# name (JNI); R8's renaming would break that binding, so keep its classes whole in the minified build.
-keep class com.k2fsa.sherpa.onnx.** { *; }
-dontwarn com.k2fsa.sherpa.onnx.**
