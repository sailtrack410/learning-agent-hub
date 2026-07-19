#!/usr/bin/env bash
set -euo pipefail

root_dir="$(cd "$(dirname "$0")/.." && pwd)"
screenshots="$root_dir/deliverables/screenshots"
work_dir="$root_dir/target/demo-video"
output_dir="$root_dir/deliverables/video"
narration="$root_dir/docs/申报材料/软件杯教育版演示旁白.txt"
output="$output_dir/Learning-Agent-Hub-软件杯教育版演示.mp4"

mkdir -p "$work_dir" "$output_dir"
rm -rf "$work_dir/recordings" "$work_dir/normalized" "$work_dir/recordings.txt" "$output"

"$root_dir/scripts/run-browser-acceptance.sh" >/dev/null

playwright_root="${PLAYWRIGHT_NODE_MODULES:-}"
if [[ -z "$playwright_root" ]]; then
  playwright_root="$(find "$HOME/.npm/_npx" -maxdepth 3 -type d -path '*/node_modules/playwright' 2>/dev/null | head -n 1 | sed 's#/playwright$##')"
fi
browser_executable="${PLAYWRIGHT_BROWSER_EXECUTABLE:-}"
if [[ -z "$browser_executable" ]]; then
  browser_executable="$(find "$HOME/Library/Caches/ms-playwright" -type f -path '*/Google Chrome for Testing.app/Contents/MacOS/Google Chrome for Testing' 2>/dev/null | sort -r | head -n 1)"
fi
PLAYWRIGHT_NODE_MODULES="$playwright_root" PLAYWRIGHT_BROWSER_EXECUTABLE="$browser_executable" \
  node "$root_dir/scripts/record-demo.mjs"

say -v Tingting -r 205 -f "$narration" -o "$work_dir/narration.aiff"
audio_duration="$(ffprobe -v error -show_entries format=duration -of default=noprint_wrappers=1:nokey=1 "$work_dir/narration.aiff")"

mkdir -p "$work_dir/normalized"
concat_file="$work_dir/recordings.txt"
: > "$concat_file"
for role in student teacher admin; do
  source="$work_dir/recordings/$role.webm"
  normalized="$work_dir/normalized/$role.mp4"
  [[ -f "$source" ]] || { echo "缺少操作录屏: $source" >&2; exit 1; }
  ffmpeg -loglevel error -y -i "$source" -vf "fps=30,scale=1440:900,format=yuv420p" \
    -an -c:v libx264 -preset medium -crf 20 "$normalized"
  printf "file '%s'\n" "$normalized" >> "$concat_file"
done

video_duration="$(for role in student teacher admin; do ffprobe -v error -show_entries format=duration -of csv=p=0 "$work_dir/normalized/$role.mp4"; done | awk '{sum += $1} END {printf "%.3f", sum}')"
speed_ratio="$(awk -v video="$video_duration" -v audio="$audio_duration" 'BEGIN { printf "%.8f", video / audio }')"
ffmpeg -loglevel error -y -f concat -safe 0 -i "$concat_file" -vf "setpts=PTS/${speed_ratio},fps=30" \
  -an -c:v libx264 -preset medium -crf 20 "$work_dir/video.mp4"

ffmpeg -loglevel error -y -i "$work_dir/video.mp4" -i "$work_dir/narration.aiff" \
  -c:v copy -c:a aac -b:a 160k -shortest -movflags +faststart "$output"

duration="$(ffprobe -v error -show_entries format=duration -of default=noprint_wrappers=1:nokey=1 "$output")"
has_video="$(ffprobe -v error -select_streams v:0 -show_entries stream=codec_name -of csv=p=0 "$output")"
has_audio="$(ffprobe -v error -select_streams a:0 -show_entries stream=codec_name -of csv=p=0 "$output")"
if awk -v duration="$duration" 'BEGIN { exit !(duration > 60 && duration < 420) }'; then :; else
  echo "演示视频时长不在 1 到 7 分钟范围内: $duration" >&2
  exit 1
fi
[[ "$has_video" == "h264" ]] || { echo "视频编码不是 H.264: $has_video" >&2; exit 1; }
[[ "$has_audio" == "aac" ]] || { echo "音频编码不是 AAC: $has_audio" >&2; exit 1; }

printf '演示视频生成成功\n路径: %s\n时长: %.1f 秒\n视频: %s\n音频: %s\n' "$output" "$duration" "$has_video" "$has_audio"
