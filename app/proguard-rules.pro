# TV
-keep class com.fongmi.android.tv.bean.** { *; }

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * { @com.google.gson.annotations.SerializedName <fields>; }
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

# TikXml
-keep class com.tickaroo.tikxml.** { *; }
-keepattributes *Annotation*
-keepclassmembers class * {
    @com.tickaroo.tikxml.annotation.* <fields>;
}


# 🚀 核心：強行保留 Room 自動生成的資料表實作類別及其建構子，防止反射閃退
-keep class * extends androidx.room.RoomDatabase {
    <init>();
}

# 📥 全套 Room 混淆保護規則
-keep class * extends androidx.room.RoomDatabase
-keep class * implements androidx.room.RoomOpenHelper
-dontwarn androidx.room.**

# 保護您的自訂 Entity 類別不被混淆 (包含您剛才修改的 Config, History, Keep 等)
-keep class com.fongmi.android.tv.bean.** { *; }
-keep class com.fongmi.android.tv.db.** { *; }

# OkHttp
-dontwarn okhttp3.**
-keep class okio.** { *; }
-keep class okhttp3.** { *; }

# CatVod
-keep class com.github.catvod.Proxy { *; }
-keep class com.github.catvod.crawler.** { *; }
-keep class com.github.catvod.spider.** { *; }
-keep class * extends com.github.catvod.crawler.Spider

# jUPnP
-dontwarn javax.**
-dontwarn sun.net.**
-dontwarn java.awt.**
-dontwarn com.sun.net.**
-dontwarn org.ietf.jgss.**
-keep class org.jupnp.** { *; }
-keep class javax.xml.** { *; }

# Cronet
-keep class org.chromium.net.** { *; }
-keep class com.google.net.cronet.** { *; }

# EXO
-dontwarn org.kxml2.io.**
-dontwarn org.xmlpull.v1.**
-dontwarn android.content.res.**
-dontwarn org.slf4j.impl.StaticLoggerBinder
-keep class org.xmlpull.** { *; }
-keepclassmembers class org.xmlpull.** { *; }

# IJK


# Jianpian
-keep class com.p2p.** { *; }

# Nano
-keep class fi.iki.elonen.** { *; }

# QuickJS
-keep class com.whl.quickjs.** { *; }
-keep class com.fongmi.quickjs.** { *; }

# Chaquopy
-keep class com.fongmi.chaquo.** { *; }
-keepclassmembers class com.fongmi.chaquo.Loader {
    public com.github.catvod.crawler.Spider spider(java.lang.String);
}
-keep class com.chaquo.python.** { *; }

# Sardine
-keep class com.thegrizzlylabs.sardineandroid.** { *; }

# Smbj
-keep class com.hierynomus.** { *; }
-keep class net.engio.mbassy.** { *; }

# TVBus
-keep class com.tvbus.engine.** { *; }

# XunLei
-keep class com.xunlei.downloadlib.** { *; }

# ZLive
-keep class com.sun.jna.** { *; }
-keep class com.east.android.zlive.** { *; }

# Zxing
-keep class com.google.zxing.** { *; }
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# x5


-dontwarn org.javamoney.moneta.Money
-dontwarn org.joda.time.DateTime
-dontwarn org.joda.time.DateTimeZone
-dontwarn org.joda.time.Duration
-dontwarn org.joda.time.Instant
-dontwarn org.joda.time.LocalDate
-dontwarn org.joda.time.LocalDateTime
-dontwarn org.joda.time.LocalTime
-dontwarn org.joda.time.Period
-dontwarn org.joda.time.ReadablePartial
-dontwarn org.joda.time.format.DateTimeFormat
-dontwarn org.joda.time.format.DateTimeFormatter
-dontwarn org.osgi.framework.Bundle
-dontwarn org.osgi.framework.BundleContext
-dontwarn org.osgi.framework.BundleReference
-dontwarn org.osgi.framework.FrameworkUtil
-dontwarn org.osgi.framework.SynchronousBundleListener
-dontwarn springfox.documentation.spring.web.json.Json
-dontwarn org.glassfish.jersey.internal.spi.AutoDiscoverable

# Jetty
-dontwarn org.eclipse.jetty.client.api.ContentProvider$Typed
-dontwarn org.eclipse.jetty.client.api.ContentProvider
-dontwarn org.eclipse.jetty.client.api.ContentResponse
-dontwarn org.eclipse.jetty.client.api.Request
-dontwarn org.eclipse.jetty.client.api.Response
-dontwarn org.eclipse.jetty.client.util.BytesContentProvider
-dontwarn org.eclipse.jetty.client.util.StringContentProvider
-dontwarn org.eclipse.jetty.http.HttpField
-dontwarn org.eclipse.jetty.http.HttpHeader
-dontwarn org.eclipse.jetty.http.HttpVersion
-dontwarn org.eclipse.jetty.server.ServerConnector

# OSGi
-dontwarn org.osgi.service.component.ComponentContext
-dontwarn org.osgi.service.component.annotations.Activate
-dontwarn org.osgi.service.component.annotations.ConfigurationPolicy
-dontwarn org.osgi.service.component.annotations.Deactivate
-dontwarn org.osgi.service.component.annotations.Modified
-dontwarn org.osgi.service.component.annotations.Reference
-dontwarn org.osgi.service.http.HttpContext
-dontwarn org.osgi.service.http.HttpService
-dontwarn org.osgi.service.http.NamespaceException
-dontwarn org.osgi.service.metatype.annotations.AttributeDefinition
-dontwarn org.osgi.service.metatype.annotations.ObjectClassDefinition

# Please add these rules to your existing keep rules in order to suppress warnings.
# This is generated automatically by the Android Gradle plugin.
-dontwarn org.eclipse.jetty.client.HttpClient
-dontwarn org.eclipse.jetty.http.HttpFields
-dontwarn org.eclipse.jetty.server.Connector
-dontwarn org.eclipse.jetty.server.Handler
-dontwarn org.eclipse.jetty.server.Request
-dontwarn org.eclipse.jetty.server.Server
-dontwarn org.eclipse.jetty.servlet.ServletContextHandler
-dontwarn org.eclipse.jetty.servlet.ServletHolder
-dontwarn org.eclipse.jetty.util.thread.QueuedThreadPool

