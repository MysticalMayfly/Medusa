# Keep the names of Kotlin's multifile facade parts (e.g. kotlin.collections.CollectionsKt__IterablesKt,
# kotlin.text.StringsKt__StringsKt). With packageScope optimisation, R8 otherwise moves one part of a facade chain out
# of its package while its subclass stays inside, and the release build crashes with
# "IllegalAccessError: Class kotlin.collections.* extended by class * is inaccessible" (2026-11-26).
-keepnames class kotlin.**Kt__*
