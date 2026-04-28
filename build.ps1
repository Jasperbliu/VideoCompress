$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
# JAVA_TOOL_OPTIONS 会被 Gradle daemon、Kotlin daemon、Worker JVM 等所有 JVM 进程继承。
# 中文 Windows 默认 file.encoding=MS936(GBK)，会把 UTF-8 中文注释/字符串解析成乱码，
# 进而引发 "Unresolved reference" / "Unclosed comment" / "Missing '}'" 等假错误。
$env:JAVA_TOOL_OPTIONS = '-Dfile.encoding=UTF-8'
Set-Location x:/CloudWinBuddy/VideoCompress
./gradlew.bat :app:assembleRelease --console=plain
