# Gson populates data models reflectively from PeerTube/search responses and from stored
# preferences. Those classes are annotated with @androidx.annotation.Keep at their declaration
# (kept by the default Android rules), so a new model only needs the annotation.

-keepattributes Signature,*Annotation*
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement

# ML Kit discovers these component registrars by class name and reflection at runtime.
-keep class com.google.mlkit.common.internal.CommonComponentRegistrar { *; }
-keep class com.google.mlkit.nl.translate.NaturalLanguageTranslateRegistrar { *; }
-keep class com.google.mlkit.nl.languageid.internal.LanguageIdRegistrar { *; }
-keep class com.google.mlkit.nl.languageid.bundled.internal.ThickLanguageIdRegistrar { *; }
