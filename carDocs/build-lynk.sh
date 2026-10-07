#!/usr/bin/env bash
#
# 领克 / 吉利 geely_fx11 定制构建（lynk 分支）
# ------------------------------------------------------------------
# 产物：mobile/build/outputs/apk/release/mobile-release.apk
#       （包名 com.shihab.diplay，即车机桌面显示的那个 DiPlay）
#
# 会做三件事：
#   1. 从车机上“已安装的 DiPlay APK”里提取离线 MFi 身份
#      （assets/offline-mfi/identity.pk8 + certificate.p7b），
#      没有它构建出的包无法完成 CarPlay 鉴权。
#   2. 在本地生成一个签名密钥（.private/，已被 .gitignore 忽略）。
#   3. 调用 Gradle 打 release 包。
#
# 用法：
#   ./carDocs/build-lynk.sh
#
# 可用环境变量覆盖：
#   DIPLAY_AUTH_ASSETS_DIR   已备好的 auth 资产目录
#   ANDROID_KEYSTORE_PATH    已有签名密钥路径
#   ANDROID_KEYSTORE_PASSWORD / ANDROID_KEY_ALIAS / ANDROID_KEY_PASSWORD
#
# 注意：新密钥与官方发布包签名不同，安装前需先卸载旧包
#       （adb uninstall com.shihab.diplay），会清掉旧设置。
# ------------------------------------------------------------------
set -euo pipefail
cd "$(dirname "$0")/.."

PRIVATE_DIR="$(pwd)/.private"
ASSETS="${DIPLAY_AUTH_ASSETS_DIR:-$PRIVATE_DIR/auth-assets}"
KEYSTORE="${ANDROID_KEYSTORE_PATH:-$PRIVATE_DIR/lynk-release.jks}"
KEY_ALIAS="${ANDROID_KEY_ALIAS:-lynk}"
STORE_PASS="${ANDROID_KEYSTORE_PASSWORD:-android}"
KEY_PASS="${ANDROID_KEY_PASSWORD:-android}"
PKG="com.shihab.diplay"

# ---------- 1) 离线 MFi 身份 ----------
if [[ ! -f "$ASSETS/offline-mfi/identity.pk8" || ! -f "$ASSETS/offline-mfi/certificate.p7b" ]]; then
    echo ">> 未找到 auth 资产，尝试从车机已安装的 $PKG 中提取 ..."
    command -v adb >/dev/null 2>&1 || { echo "错误：需要 adb" >&2; exit 1; }
    adb wait-for-device
    APK_PATH="$(adb shell "pm path $PKG" | sed -n 's/^package://p' | tr -d '\r' | head -1)"
    [[ -n "$APK_PATH" ]] || { echo "错误：车机上找不到 $PKG（或其已卸载）" >&2; exit 1; }
    TMP="$(mktemp -d)"
    trap 'rm -rf "$TMP"' EXIT
    adb pull "$APK_PATH" "$TMP/base.apk" >/dev/null
    mkdir -p "$ASSETS/offline-mfi"
    unzip -o -j "$TMP/base.apk" 'assets/offline-mfi/*' -d "$ASSETS/offline-mfi" >/dev/null
fi
[[ -s "$ASSETS/offline-mfi/identity.pk8" && -s "$ASSETS/offline-mfi/certificate.p7b" ]] || {
    echo "错误：$ASSETS/offline-mfi 缺少 identity.pk8 / certificate.p7b" >&2; exit 1; }
echo ">> auth 资产：$ASSETS"

# ---------- 2) 签名密钥 ----------
if [[ ! -f "$KEYSTORE" ]]; then
    echo ">> 生成本地签名密钥：$KEYSTORE"
    mkdir -p "$(dirname "$KEYSTORE")"
    keytool -genkeypair -keystore "$KEYSTORE" -alias "$KEY_ALIAS" \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -storepass "$STORE_PASS" -keypass "$KEY_PASS" -dname "CN=DiPlay Lynk"
fi
echo ">> 签名密钥：$KEYSTORE"

# ---------- 3) 构建 ----------
echo ">> 开始构建 release ..."
DIPLAY_AUTH_ASSETS_DIR="$ASSETS" \
ANDROID_KEYSTORE_PATH="$KEYSTORE" \
ANDROID_KEYSTORE_PASSWORD="$STORE_PASS" \
ANDROID_KEY_ALIAS="$KEY_ALIAS" \
ANDROID_KEY_PASSWORD="$KEY_PASS" \
./gradlew :mobile:assembleRelease

APK="mobile/build/outputs/apk/release/mobile-release.apk"
echo
echo ">> 完成：$APK"
echo ">> 安装（需先卸载旧包）："
echo "   adb uninstall $PKG"
echo "   ./carDocs/install-apk.sh $APK"
