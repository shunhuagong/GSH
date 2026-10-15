# GSH 混淆规则
# Room / Compose / Coroutines 自带的 consumer rules 已随依赖自动应用

# 保留注解与元数据，避免运行时反射问题
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# Kotlin 元数据（Room KSP 依赖注解处理产物，保留以防裁剪）
-keep class kotlinx.metadata.** { *; }

# 崩溃日志更易读：保留行号与源文件名（个人应用体积影响可忽略）
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
