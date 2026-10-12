#!/usr/bin/env bash
# 桌面布局备份 · LSPosed 模块构建脚本（Windows / Git Bash，无需 Android Studio / Gradle）
# 链路: aapt2 compile -> aapt2 link(生成 R.java) -> javac -> d8 -> 组装 APK -> jarsigner(v1)
# 注意 1：不要设置 MSYS_NO_PATHCONV，让 Git Bash 自动把 /c/... 转成 Windows 路径给 java 工具。
# 注意 2：整个脚本不做任何删除动作（每次构建用一个独立输出目录），避免 rm -rf 触发安全策略。

PROJ="C:/Users/su/WorkBuddy/2026-10-12-13-49-48/lsposed-launcher-backup"
LIBS="${LIBS_DIR:-C:/Users/su/WorkBuddy/2026-10-12-13-49-48/_build/libs}"
# 以下三项都可用环境变量覆盖，方便在 CI 上跑（不设则用本机默认路径）
JDK="${JDK_HOME:-/c/Users/su/.gradle/jdks/eclipse_adoptium-21-amd64-windows.2}"
TOOLS="${TOOLS_DIR:-/c/Users/su/Desktop/散热器/伪彩移植/tools}"
# 签名口令同样支持环境变量覆盖（CI 里走 GitHub Secrets，不写死在仓库）
KEYSTORE_PASS="${KEYSTORE_PASS:-llb123456}"
KEYSTORE_ALIAS="${KEYSTORE_ALIAS:-llb}"
KEYSTORE_FILE="${KEYSTORE_FILE:-$PROJ/llb.jks}"

cd "$PROJ"

OUT="$PROJ/_out/build-$(date +%Y%m%d_%H%M%S)"
mkdir -p "$OUT/classes" "$OUT/dex" "$OUT/gen" "$OUT/apk"

# javac / d8 都是 Windows 程序：所有路径参数都用 Windows 形式，';' 才能被正确解析

mkdir -p "$LIBS"

# libxposed api：优先本机已有的 classes.jar，否则从 Maven Central 拉 aar 解出 classes.jar
# （注意：Maven 上 api-102.0.0.jar 是空壳，真正的类在 aar 里）
LIBXPOSED_JAR="${LIBXPOSED_JAR:-$LIBS/libxposed-api-102.jar}"
if [ ! -f "$LIBXPOSED_JAR" ]; then
  if [ -f "$LIBS/aar_x/classes.jar" ]; then
    cp "$LIBS/aar_x/classes.jar" "$LIBXPOSED_JAR"
  else
    echo "  下载 io.github.libxposed:api:102.0.0 ..."
    mkdir -p "$LIBS/_aar"
    curl -sSL --noproxy '*' -o "$LIBS/_aar/api.aar" \
      "https://repo1.maven.org/maven2/io/github/libxposed/api/102.0.0/api-102.0.0.aar"
    ( cd "$LIBS/_aar" && unzip -o -q api.aar classes.jar && cp classes.jar "$LIBXPOSED_JAR" )
  fi
fi

# android.jar
ANDROID_JAR="${ANDROID_JAR:-}"
if [ -z "$ANDROID_JAR" ]; then
  [ -f "$LIBS/android.jar" ] || cp "$TOOLS/android.jar" "$LIBS/android.jar"
  ANDROID_JAR="$LIBS/android.jar"
fi

# r8（内含 D8）
R8_JAR="${R8_JAR:-}"
if [ -z "$R8_JAR" ]; then
  [ -f "$LIBS/r8.jar" ] || cp "$TOOLS/r8-8.3.37.jar" "$LIBS/r8.jar"
  R8_JAR="$LIBS/r8.jar"
fi

# aapt2
AAPT2="${AAPT2_BIN:-}"
if [ -z "$AAPT2" ]; then
  for c in $(find /c/Users/su/.gradle/caches -maxdepth 9 -iname "aapt2.exe" 2>/dev/null); do AAPT2="$c"; break; done
fi

# javac 的 classpath（必须在上面解析完之后赋值）
CP="$ANDROID_JAR;$LIBXPOSED_JAR"

echo "=== 0) 环境 ==="
echo "javac : $("$JDK/bin/javac" -version 2>&1)"
echo "aapt2 : $AAPT2"
echo "a.jar : $ANDROID_JAR"
echo "r8    : $R8_JAR"
echo "out   : $OUT"
[ -n "$AAPT2" ] || { echo "!! 找不到 aapt2"; exit 1; }

echo
echo "=== 1) aapt2 compile 资源 ==="
if ! "$AAPT2" compile --dir res -o "$OUT/res.zip" > "$OUT/aapt2c.log" 2>&1; then
  echo "!! aapt2 compile 失败"; head -30 "$OUT/aapt2c.log"; exit 1
fi
head -10 "$OUT/aapt2c.log" || true

echo
echo "=== 2) aapt2 link（同时生成 R.java） ==="
if ! "$AAPT2" link \
      -o "$OUT/apk/base.apk" \
      -I "$ANDROID_JAR" \
      --manifest "$PROJ/AndroidManifest.xml" \
      "$OUT/res.zip" \
      --java "$OUT/gen" \
      --min-sdk-version 26 --target-sdk-version 29 \
      --version-code 21 --version-name 1.0 \
      > "$OUT/aapt2l.log" 2>&1; then
  echo "!! aapt2 link 失败"; head -40 "$OUT/aapt2l.log"; exit 1
fi
head -20 "$OUT/aapt2l.log" || true
find "$OUT/gen" -name '*.java' | sed 's#^#   #'

echo
echo "=== 3) javac ==="
find src -name '*.java' > "$OUT/sources.txt"
find "$OUT/gen" -name '*.java' >> "$OUT/sources.txt"
if ! "$JDK/bin/javac" -J-Duser.language=en -J-Duser.country=US \
      --release 11 -encoding UTF-8 -nowarn \
      -cp "$CP" -d "$OUT/classes" @"$OUT/sources.txt" > "$OUT/javac.log" 2>&1; then
  echo "!! javac 失败"; head -60 "$OUT/javac.log"; exit 1
fi
echo "OK, class 文件数: $(find "$OUT/classes" -name '*.class' | wc -l)"

echo
echo "=== 4) d8 -> classes.dex ==="
find "$OUT/classes" -name '*.class' > "$OUT/classes.list"
if ! "$JDK/bin/java" -cp "$R8_JAR" com.android.tools.r8.D8 \
      --min-api 26 --lib "$ANDROID_JAR" --output "$OUT/dex" \
      @"$OUT/classes.list" > "$OUT/d8.log" 2>&1; then
  echo "!! d8 失败"; head -40 "$OUT/d8.log"; exit 1
fi
ls -la "$OUT/dex"

echo
echo "=== 5) 组装 APK ==="
"C:/Users/su/.workbuddy/binaries/python/envs/default/Scripts/python.exe" - "$OUT" "$PROJ" <<'PY'
import sys, os, zipfile
out, proj = sys.argv[1], sys.argv[2]
src = os.path.join(out, "apk", "base.apk")
dst = os.path.join(out, "apk", "unsigned.apk")
with zipfile.ZipFile(src, "r") as zin, zipfile.ZipFile(dst, "w", zipfile.ZIP_DEFLATED) as zout:
    for item in zin.infolist():
        zout.writestr(item, zin.read(item.filename))
    zout.write(os.path.join(out, "dex", "classes.dex"), "classes.dex")
    base = os.path.join(proj, "resources", "META-INF", "xposed")
    for name in sorted(os.listdir(base)):
        zout.write(os.path.join(base, name), "META-INF/xposed/" + name)
print("packed ->", dst, os.path.getsize(dst), "bytes")
with zipfile.ZipFile(dst) as z:
    for n in z.namelist():
        print("   ", n)
PY

echo
echo "=== 6) 签名 ==="
# keystore 必须放在 _out 之外，否则签名每次都变，只能卸载重装（会清掉 LSPosed 启用状态）
KS="$KEYSTORE_FILE"
if [ ! -f "$KS" ]; then
  "$JDK/bin/keytool" -genkeypair -keystore "$KS" -storetype PKCS12 \
    -alias "$KEYSTORE_ALIAS" -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass "$KEYSTORE_PASS" -keypass "$KEYSTORE_PASS" \
    -dname "CN=LauncherLayoutBackup, OU=su, O=su, L=Taiyuan, ST=Shanxi, C=CN" > "$OUT/keytool.log" 2>&1 \
    || { echo "!! keytool 失败"; head -20 "$OUT/keytool.log"; exit 1; }
fi
"$JDK/bin/jarsigner" -keystore "$KS" -storepass "$KEYSTORE_PASS" -keypass "$KEYSTORE_PASS" \
  -sigalg SHA256withRSA -digestalg SHA-256 \
  -signedjar "$OUT/LauncherLayoutBackup-1.0.apk" "$OUT/apk/unsigned.apk" "$KEYSTORE_ALIAS" > "$OUT/sign.log" 2>&1 \
  || { echo "!! jarsigner 失败"; head -20 "$OUT/sign.log"; exit 1; }

echo
echo "=== 7) 产物 ==="
ls -la "$OUT/LauncherLayoutBackup-1.0.apk"
"C:/Users/su/.workbuddy/binaries/python/envs/default/Scripts/python.exe" -c "
import zipfile,hashlib
p=r'$OUT/LauncherLayoutBackup-1.0.apk'
z=zipfile.ZipFile(p)
print('entries:', len(z.namelist()))
print('dex:', [n for n in z.namelist() if n.endswith('.dex')])
print('xposed:', [n for n in z.namelist() if 'xposed' in n])
print('md5:', hashlib.md5(open(p,'rb').read()).hexdigest())
"
echo "APK=$OUT/LauncherLayoutBackup-1.0.apk"
echo "DONE"
