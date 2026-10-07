#!/usr/bin/env bash
#
# 车机 APK 安装脚本
# ------------------------------------------------------------------
# 适用：领克 / 吉利 Flyme Auto 车机（adbd 会过滤命令中的 "install" 关键字）
# 原理：adb push 推送 APK 到 /data/local/tmp，再用变量拆分 "install"
#       关键字调用设备的 pm 命令，绕过 adbd 的关键字过滤。
#
# 用法：
#   ./install-apk.sh <apk文件> [选项...]
#
# 选项：
#   -r   覆盖安装（默认开启）
#   -t   允许安装 test 包（默认开启）
#   -g   安装后授予全部运行时权限
#   -d   允许降级安装
#   -h   显示帮助
#
# 示例：
#   ./install-apk.sh DiPlay-0.2.13.apk
#   ./install-apk.sh app.apk -g
#   ./install-apk.sh app.apk -r -t -d
# ------------------------------------------------------------------
set -euo pipefail

# ---------- 参数解析 ----------
APK=""
PM_FLAGS=()

usage() {
    sed -n '2,23p' "$0" | sed 's/^# \{0,1\}//'
    exit "${1:-0}"
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        -h|--help) usage 0 ;;
        -r) PM_FLAGS+=("-r") ;;
        -t) PM_FLAGS+=("-t") ;;
        -g) PM_FLAGS+=("-g") ;;
        -d) PM_FLAGS+=("-d") ;;
        -*) echo "未知选项: $1" >&2; usage 1 ;;
        *)
            if [[ -z "$APK" ]]; then
                APK="$1"
            else
                echo "只能指定一个 APK 文件" >&2; usage 1
            fi
            ;;
    esac
    shift
done

if [[ -z "$APK" ]]; then
    echo "错误：请指定 APK 文件" >&2
    usage 1
fi

if [[ ! -f "$APK" ]]; then
    echo "错误：文件不存在: $APK" >&2
    exit 1
fi

# 默认带上 -r -t
if [[ ${#PM_FLAGS[@]} -eq 0 ]]; then
    PM_FLAGS=("-r" "-t")
fi

NAME="$(basename "$APK")"
DST="/data/local/tmp/$NAME"

# ---------- 环境检查 ----------
if ! command -v adb >/dev/null 2>&1; then
    echo "错误：未找到 adb，请先安装 android-tools" >&2
    exit 1
fi

echo ">> 等待设备连接..."
adb wait-for-device

echo ">> 设备："
adb devices -l | grep -v '^List of devices' | grep . || true

# ---------- 推送 ----------
echo ">> 推送 $NAME 到 $DST ..."
adb push "$APK" "$DST"

# ---------- 安装（关键字拆分绕过过滤） ----------
echo ">> 安装中（拆分 install 关键字绕过 adbd 过滤）..."
# 注意：命令原文中不能出现 "install" 字样
adb shell "a=in; b=stall; pm \${a}\${b} ${PM_FLAGS[*]} $DST"

# ---------- 清理 ----------
echo ">> 清理设备临时文件..."
adb shell "rm -f $DST" || true

# ---------- 结果 ----------
PKG="$(aapt dump badging "$APK" 2>/dev/null | sed -n "s/^package: name='\([^']*\)'.*/\1/p" | head -1 || true)"
if [[ -n "$PKG" ]]; then
    echo ">> 校验安装结果："
    adb shell "pm list packages | grep '$PKG'" || \
        echo "   （未在包列表中找到 $PKG，请手动确认）"
fi

echo ">> 完成 ✅"
