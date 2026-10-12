#!/usr/bin/env bash
# 桌面布局备份 · LSPosed 模块构建脚本（Windows / Git Bash，无需 Android Studio / Gradle）
# 链路: aapt2 compile -> aapt2 link(生成 R.java) -> javac -> d8 -> 组装 APK -> jarsigner(v1)
# 注意 1：不要设置 MSYS_NO_PATHCONV，让 Git Bash 自动把 /c/... 转成 Windows 路径给 java 工具。
# 注意 2：整个脚本不做任何删除动作（每次构建用一个独立输出目录），避免 rm -rf 触发安全策略。

PROJ="C:/Users/su/WorkBuddy/2026-10-12-13-49-48/lsposed-launcher-backup"
LIBS="C:/Users/su/WorkBuddy/2026-10-12-13-49-48/_build/libs"
JDK="/c/Users/su/.gradle/jdks/eclipse_adoptium-21-amd64-windows.2"
TOOLS="/c/Users/su/Desktop/散热器/伪彩移植/tools"

cd "$PROJ"

OUT="$PROJ/_out/build-$(date +%Y%m%d_%H%M%S)"
mkdir -p "$OUT/classes" "$OUT/dex" "$OUT/gen" "$OUT/apk"

# javac / d8 都是 Windows 程序：所有路径参数都用 Windows 形式，';' 才能被正确解析
CP="$LIBS/android.jar;$LIBS/libxposed-api-102.jar"

mkdir -p "$LIBS"
[ -f "$LIBS/android.jar" ] || cp "$TOOLS/android.jar" "$LIBS/android.jar"
[ -f "$LIBS/r8.jar" ] || cp "$TOOLS/r8-8.3.37.jar" "$LIBS/r8.jar"
[ -f "$LIBS/libxposed-api-102.jar" ] || cp "$LIBS/aar_x/classes.jar" "$LIBS/libxposed-api-102.jar"

AAPT2=""
for c in $(find /c/Users/su/.gradle/caches -maxdepth 9 -iname "aapt2.exe" 2>/dev/null); do AAPT2="$c"; break; done

echo "=== 0) 环境 ==="
echo "javac : $("$JDK/bin/javac" -version 2>&1)"
echo "aapt2 : $AAPT2"
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
      -I "$LIBS/android.jar" \
      --manifest "$PROJ/AndroidManifest.xml" \
      "$OUT/res.zip" \
      --java "$OUT/gen" \
      --min-sdk-version 26 --target-sdk-version 29 \
      --version-code 17 --version-name 2.8 \
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
if ! "$JDK/bin/java" -cp "$LIBS/r8.jar" com.android.tools.r8.D8 \
      --min-api 26 --lib "$LIBS/android.jar" --output "$OUT/dex" \
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
KS="$PROJ/llb.jks"
if [ ! -f "$KS" ]; then
  "$JDK/bin/keytool" -genkeypair -keystore "$KS" -storetype PKCS12 \
    -alias llb -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass llb123456 -keypass llb123456 \
    -dname "CN=LauncherLayoutBackup, OU=su, O=su, L=Taiyuan, ST=Shanxi, C=CN" > "$OUT/keytool.log" 2>&1 \
    || { echo "!! keytool 失败"; head -20 "$OUT/keytool.log"; exit 1; }
fi
"$JDK/bin/jarsigner" -keystore "$KS" -storepass llb123456 -keypass llb123456 \
  -sigalg SHA256withRSA -digestalg SHA-256 \
  -signedjar "$OUT/LauncherLayoutBackup-2.8.apk" "$OUT/apk/unsigned.apk" llb > "$OUT/sign.log" 2>&1 \
  || { echo "!! jarsigner 失败"; head -20 "$OUT/sign.log"; exit 1; }

echo
echo "=== 7) 产物 ==="
ls -la "$OUT/LauncherLayoutBackup-2.8.apk"
"C:/Users/su/.workbuddy/binaries/python/envs/default/Scripts/python.exe" -c "
import zipfile,hashlib
p=r'$OUT/LauncherLayoutBackup-2.8.apk'
z=zipfile.ZipFile(p)
print('entries:', len(z.namelist()))
print('dex:', [n for n in z.namelist() if n.endswith('.dex')])
print('xposed:', [n for n in z.namelist() if 'xposed' in n])
print('md5:', hashlib.md5(open(p,'rb').read()).hexdigest())
"
echo "APK=$OUT/LauncherLayoutBackup-2.8.apk"
echo "DONE"
