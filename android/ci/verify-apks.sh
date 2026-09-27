#!/usr/bin/env bash
set -euo pipefail

SDK_ROOT="${ANDROID_SDK_ROOT:?ANDROID_SDK_ROOT is required}"
BUILD_TOOLS="${SDK_ROOT}/build-tools/35.0.0"
DEVELOPER_APK="${1:?Developer APK path is required}"
HOST_APK="${2:?Build Host APK path is required}"

for tool in "${BUILD_TOOLS}/aapt" "${BUILD_TOOLS}/apksigner"; do
  test -x "$tool" || { echo "Missing Android tool: $tool" >&2; exit 1; }
done

verify_one() {
  local apk="$1"
  local expected_package="$2"
  local minimum_version="$3"
  local badging
  badging="$("${BUILD_TOOLS}/aapt" dump badging "$apk")"
  grep -q "package: name='${expected_package}'" <<<"$badging"
  local version
  version="$(sed -n "s/.*versionCode='\([0-9]*\)'.*/\1/p" <<<"$badging" | head -1)"
  test -n "$version"
  test "$version" -ge "$minimum_version"
  "${BUILD_TOOLS}/apksigner" verify --verbose "$apk" >/dev/null
  echo "verified package=${expected_package} version=${version} file=${apk}"
}

verify_one "$DEVELOPER_APK" "com.example.aideveloper" 3
verify_one "$HOST_APK" "com.example.aideveloper.host" 6

developer_cert="$("${BUILD_TOOLS}/apksigner" verify --print-certs "$DEVELOPER_APK" | sed -n 's/.*SHA-256 digest: //p' | head -1)"
host_cert="$("${BUILD_TOOLS}/apksigner" verify --print-certs "$HOST_APK" | sed -n 's/.*SHA-256 digest: //p' | head -1)"
test -n "$developer_cert"
test "$developer_cert" = "$host_cert"
echo "verified shared signer=${developer_cert}"