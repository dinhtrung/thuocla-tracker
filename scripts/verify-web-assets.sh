#!/usr/bin/env bash
#
# verify-web-assets.sh — guard against the APK's copy of the web app drifting from the
# source of truth at the repo root.
#
# The web build lives at the repo root (index.html, app.js, ...). The APK ships a
# byte-identical copy in android/app/src/main/assets/web/. Nobody may hand-edit the copy:
# edit at the root, then run this script with --copy to re-sync. Default mode only checks
# and fails on any missing or differing pair.
#
# Usage, from the repository root:
#   scripts/verify-web-assets.sh          # verify all 6 pairs; exit 1 on drift
#   scripts/verify-web-assets.sh --copy   # copy root -> assets/web/, then verify
#
# See openspec/changes/android-webview-shell/ (design D2, spec "Asset của app là bản sao
# của bản web").

set -euo pipefail

# Paths are relative to the repository root so this works on a CI runner with no ~ layout.
# The script resolves the root from its own location, so it also works when invoked as
# `bash scripts/verify-web-assets.sh` from any working directory.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
ASSETS_DIR="${REPO_ROOT}/android/app/src/main/assets/web"

# The six files that make up the web bundle. Order is the order used in output.
WEB_FILES=(
  "index.html"
  "app.js"
  "styles.css"
  "manifest.json"
  "icon-192.png"
  "icon-512.png"
)

MODE="verify"
case "${1:-}" in
  "")        MODE="verify" ;;
  --copy)    MODE="copy" ;;
  -h|--help)
    sed -n '2,20p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
    exit 0
    ;;
  *)
    echo "Lỗi: tham số không hợp lệ: $1" >&2
    echo "Dùng: scripts/verify-web-assets.sh [--copy]" >&2
    exit 2
    ;;
esac

if [ "${MODE}" = "copy" ]; then
  mkdir -p "${ASSETS_DIR}"
  echo "== Copy 6 file web từ repo root sang assets/web/ =="
  for f in "${WEB_FILES[@]}"; do
    if [ ! -f "${REPO_ROOT}/${f}" ]; then
      echo "Lỗi: thiếu file nguồn ở repo root: ${f}" >&2
      exit 1
    fi
    cp "${REPO_ROOT}/${f}" "${ASSETS_DIR}/${f}"
    echo "  copied ${f}"
  done
  echo
fi

echo "== Đối chiếu sha256: repo root  <->  android/app/src/main/assets/web/ =="

fail=0
missing=0
for f in "${WEB_FILES[@]}"; do
  src="${REPO_ROOT}/${f}"
  dst="${ASSETS_DIR}/${f}"

  if [ ! -f "${src}" ]; then
    echo "  MISSING ở repo root: ${f}" >&2
    missing=1; continue
  fi
  if [ ! -f "${dst}" ]; then
    echo "  MISSING trong assets/web: ${f}" >&2
    missing=1; continue
  fi

  src_hash="$(sha256sum "${src}" | awk '{print $1}')"
  dst_hash="$(sha256sum "${dst}" | awk '{print $1}')"

  if [ "${src_hash}" = "${dst_hash}" ]; then
    printf '  OK    %-16s %s\n' "${f}" "${src_hash}"
  else
    printf '  DRIFT %-16s root=%s assets=%s\n' "${f}" "${src_hash}" "${dst_hash}"
    fail=1
  fi
done

echo
if [ "${missing}" -ne 0 ]; then
  echo "FAIL: có file thiếu — không thể xác nhận bản sao." >&2
  exit 1
fi
if [ "${fail}" -ne 0 ]; then
  echo "FAIL: asset trong APK lệch bản web ở repo root." >&2
  echo "      Sửa ở repo root rồi chạy: scripts/verify-web-assets.sh --copy" >&2
  exit 1
fi

echo "PASS: 6/6 file khớp byte-identical giữa repo root và assets/web/."
