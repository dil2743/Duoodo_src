# BouncyCastle: only the lightweight API is used directly; keep it intact (reflection in provider lookups).
-keep class org.bouncycastle.crypto.** { *; }
-keep class org.bouncycastle.math.** { *; }
-keep class org.bouncycastle.asn1.sec.** { *; }
-keep class org.bouncycastle.asn1.x9.** { *; }
-dontwarn org.bouncycastle.**

# OkHttp optional platform integrations.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
