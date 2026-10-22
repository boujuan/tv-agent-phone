# kotlinx.serialization, OkHttp, CameraX and Compose ship their own consumer rules. ZXing needs none.
# Keep the names in crash stack traces readable against the mapping file.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
