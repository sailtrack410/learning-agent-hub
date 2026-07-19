#!/usr/bin/env bash
set -euo pipefail

root_dir="$(cd "$(dirname "$0")/.." && pwd)"
playwright_root="${PLAYWRIGHT_NODE_MODULES:-}"

if [[ -z "$playwright_root" ]]; then
  playwright_root="$(find "$HOME/.npm/_npx" -maxdepth 3 -type d -path '*/node_modules/playwright' 2>/dev/null | head -n 1 | sed 's#/playwright$##')"
fi
if [[ -z "$playwright_root" || ! -d "$playwright_root/playwright" ]]; then
  echo "未找到 Playwright。请设置 PLAYWRIGHT_NODE_MODULES 指向包含 playwright 的 node_modules。" >&2
  exit 1
fi

browser_executable="${PLAYWRIGHT_BROWSER_EXECUTABLE:-}"
if [[ -z "$browser_executable" ]]; then
  browser_executable="$(find "$HOME/Library/Caches/ms-playwright" -type f -path '*/Google Chrome for Testing.app/Contents/MacOS/Google Chrome for Testing' 2>/dev/null | sort -r | head -n 1)"
fi
if [[ -z "$browser_executable" || ! -x "$browser_executable" ]]; then
  echo "未找到 Chrome for Testing。请设置 PLAYWRIGHT_BROWSER_EXECUTABLE。" >&2
  exit 1
fi

cd "$root_dir"
PLAYWRIGHT_NODE_MODULES="$playwright_root" PLAYWRIGHT_BROWSER_EXECUTABLE="$browser_executable" \
  node scripts/browser-acceptance.mjs
