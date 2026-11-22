#!/bin/sh
set -eu

out=/tmp/app-links
mkdir -p "$out"
rm -f "$out/apple-app-site-association" "$out/assetlinks.json"

ios_ids="${APP_LINKS_IOS_APP_IDS:-}"
android_package="${APP_LINKS_ANDROID_PACKAGE:-}"
android_fingerprints="${APP_LINKS_ANDROID_SHA256_FINGERPRINTS:-}"

fail() {
  echo "40-app-links: $1" >&2
  exit 1
}

json_list() {
  printf '%s' "$1" | tr ',' '\n' | sed 's/^[[:space:]]*//;s/[[:space:]]*$//' | sed '/^$/d' \
    | awk 'BEGIN { first = 1 } { printf "%s\"%s\"", (first ? "" : ","), $0; first = 0 }'
}

if [ -n "$ios_ids" ]; then
  for id in $(printf '%s' "$ios_ids" | tr ',' ' '); do
    printf '%s' "$id" | grep -Eq '^[A-Z0-9]{10}\.[A-Za-z0-9][A-Za-z0-9.-]*$' \
      || fail "APP_LINKS_IOS_APP_IDS entry \"$id\" must look like TEAMID.com.example.app"
  done
  printf '{"applinks":{"details":[{"appIDs":[%s],"components":[{"/":"/verify-email"},{"/":"/reset-password"}]}]}}\n' \
    "$(json_list "$ios_ids")" > "$out/apple-app-site-association"
  echo "40-app-links: serving apple-app-site-association"
fi

if [ -n "$android_package" ] || [ -n "$android_fingerprints" ]; then
  [ -n "$android_package" ] && [ -n "$android_fingerprints" ] \
    || fail "set both APP_LINKS_ANDROID_PACKAGE and APP_LINKS_ANDROID_SHA256_FINGERPRINTS"
  printf '%s' "$android_package" | grep -Eq '^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$' \
    || fail "APP_LINKS_ANDROID_PACKAGE \"$android_package\" is not a valid package name"
  for fingerprint in $(printf '%s' "$android_fingerprints" | tr ',' ' '); do
    printf '%s' "$fingerprint" | grep -Eq '^([0-9A-F]{2}:){31}[0-9A-F]{2}$' \
      || fail "APP_LINKS_ANDROID_SHA256_FINGERPRINTS entry \"$fingerprint\" must be an upper-case SHA-256 fingerprint"
  done
  printf '[{"relation":["delegate_permission/common.handle_all_urls"],"target":{"namespace":"android_app","package_name":"%s","sha256_cert_fingerprints":[%s]}}]\n' \
    "$android_package" "$(json_list "$android_fingerprints")" > "$out/assetlinks.json"
  echo "40-app-links: serving assetlinks.json"
fi
