#!/usr/bin/env bash
set -euo pipefail

root_dir="$(cd "$(dirname "$0")/.." && pwd)"
output_dir="$root_dir/deliverables/competition-submission"
stage_dir="$root_dir/target/competition-submission-staging"

for command_name in mvn npm rsync rg; do
  command -v "$command_name" >/dev/null 2>&1 || {
    echo "缺少构建命令: $command_name" >&2
    exit 1
  }
done

existing_document_dir="$stage_dir-existing-documents"
rm -rf "$stage_dir" "$existing_document_dir"
mkdir -p "$stage_dir"
mkdir -p "$existing_document_dir"
trap 'rm -rf "$stage_dir" "$existing_document_dir"' EXIT

if [[ -d "$output_dir/docs" ]]; then
  find "$output_dir/docs" -type f \( \
    -iname '*.doc' -o -iname '*.docx' -o -iname '*.ppt' -o -iname '*.pptx' -o -iname '*.pdf' \
    \) -exec cp {} "$existing_document_dir/" \;
fi

echo "[1/5] 构建后端可执行包"
(cd "$root_dir/server" && mvn -DskipTests package)

echo "[2/5] 构建教育运营管理端"
(cd "$root_dir/admin-web" && npm run build)

echo "[3/5] 构建学生/教师端"
(cd "$root_dir/employee-web" && npm run build)

jar_path="$(find "$root_dir/server/target" -maxdepth 1 -type f \
  -name 'workforce-hub-*.jar' ! -name '*.jar.original' | sort | tail -n 1)"
if [[ -z "$jar_path" ]]; then
  echo "未找到后端可执行 JAR" >&2
  exit 1
fi

copy_source_file() {
  local source_path="$1"
  local target_path="$2"
  mkdir -p "$(dirname "$target_path")"
  cp "$source_path" "$target_path"
}

echo "[4/5] 按白名单同步三端、配置样板和正式文档"
mkdir -p "$stage_dir/server/source/src/main"
copy_source_file "$root_dir/server/pom.xml" "$stage_dir/server/source/pom.xml"
rsync -a "$root_dir/server/src/main/" "$stage_dir/server/source/src/main/"
copy_source_file "$jar_path" "$stage_dir/server/learning-agent-hub-server.jar"

admin_source="$stage_dir/admin-web/source/admin-web"
mkdir -p "$admin_source/src" "$admin_source/public" "$stage_dir/admin-web/source/shared-web"
for file_name in package.json package-lock.json index.html tsconfig.json vite.config.ts vite.config.test.ts; do
  copy_source_file "$root_dir/admin-web/$file_name" "$admin_source/$file_name"
done
rsync -a "$root_dir/admin-web/src/" "$admin_source/src/"
rsync -a "$root_dir/admin-web/public/" "$admin_source/public/"
rsync -a "$root_dir/shared-web/" "$stage_dir/admin-web/source/shared-web/"
mkdir -p "$stage_dir/admin-web/dist"
rsync -a "$root_dir/admin-web/dist/" "$stage_dir/admin-web/dist/"

employee_source="$stage_dir/employee-web/source/employee-web"
mkdir -p "$employee_source/src" "$employee_source/public" "$stage_dir/employee-web/source/shared-web"
for file_name in package.json package-lock.json index.html file-preview.html tsconfig.json tsconfig.node.json vite.config.ts vite.config.test.ts vite.file.config.ts; do
  copy_source_file "$root_dir/employee-web/$file_name" "$employee_source/$file_name"
done
rsync -a "$root_dir/employee-web/src/" "$employee_source/src/"
rsync -a "$root_dir/employee-web/public/" "$employee_source/public/"
rsync -a "$root_dir/shared-web/" "$stage_dir/employee-web/source/shared-web/"
mkdir -p "$stage_dir/employee-web/dist"
rsync -a --exclude 'stats.html' "$root_dir/employee-web/dist/" "$stage_dir/employee-web/dist/"

copy_source_file "$root_dir/deploy/standalone.env.example" "$stage_dir/.env.example"
copy_source_file "$root_dir/deploy/competition-submission.README.md" "$stage_dir/README.md"

mkdir -p "$stage_dir/docs"
find "$existing_document_dir" -maxdepth 1 -type f -exec cp {} "$stage_dir/docs/" \;
if [[ -d "$root_dir/docs/提交" ]]; then
  find "$root_dir/docs/提交" -maxdepth 1 -type f \( \
    -iname '*.doc' -o -iname '*.docx' -o -iname '*.ppt' -o -iname '*.pptx' -o -iname '*.pdf' \
    \) -exec cp {} "$stage_dir/docs/" \;
fi

copy_source_file "$root_dir/docs/docs/软件杯全功能系统测试报告-20260719.md" \
  "$stage_dir/docs/技术附件-系统测试报告.md"
copy_source_file "$root_dir/docs/开源软件与许可证清单.md" \
  "$stage_dir/docs/技术附件-开源软件与许可证清单.md"
copy_source_file "$root_dir/docs/部署与交付/AI-Coding工具使用说明.md" \
  "$stage_dir/docs/技术附件-AI-Coding工具使用说明.md"

if ! find "$stage_dir/docs" -maxdepth 1 -type f -print -quit | grep -q .; then
  echo "未找到比赛 Word、PPT 或 PDF 文档" >&2
  exit 1
fi

if find "$stage_dir" -type f \( \
  -name '.env' -o -name '.DS_Store' -o -name '*.log' -o -name '*.pid' \
  -o -name '*.mp4' -o -name '*.zip' -o -name '*.tar' -o -name '*.gz' \
  \) -print -quit | grep -q .; then
  echo "比赛目录出现白名单外文件" >&2
  find "$stage_dir" -type f \( \
    -name '.env' -o -name '.DS_Store' -o -name '*.log' -o -name '*.pid' \
    -o -name '*.mp4' -o -name '*.zip' -o -name '*.tar' -o -name '*.gz' \
    \) -print >&2
  exit 1
fi

if rg -l --hidden --glob '!*.jar' \
  '(-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----|sk-[A-Za-z0-9_-]{20,}|AKIA[0-9A-Z]{16})' \
  "$stage_dir" >/dev/null; then
  echo "比赛目录疑似包含密钥，请检查后再同步" >&2
  exit 1
fi

mkdir -p "$output_dir"
rsync -a --delete "$stage_dir/" "$output_dir/"

top_level="$(find "$output_dir" -mindepth 1 -maxdepth 1 -print | sed "s#^$output_dir/##" | LC_ALL=C sort | tr '\n' ' ')"
if [[ "$top_level" != ".env.example README.md admin-web docs employee-web server " ]]; then
  echo "比赛目录顶层结构不符合白名单: $top_level" >&2
  exit 1
fi

echo "[5/5] 同步完成"
echo "目录: $output_dir"
du -sh "$output_dir"
