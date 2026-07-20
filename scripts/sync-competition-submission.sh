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

rm -rf "$stage_dir"
mkdir -p "$stage_dir"
trap 'rm -rf "$stage_dir"' EXIT

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

copy_document() {
  local source_relative="$1"
  local target_relative="$2"
  copy_source_file "$root_dir/docs/$source_relative" "$stage_dir/docs/$target_relative"
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

copy_document "docs/软件杯教育版提交文档清单.md" "00-软件杯教育版提交文档清单.md"
copy_document "产品与业务/软件杯教育版需求规格说明书.md" "01-软件杯教育版需求规格说明书.md"
copy_document "架构与设计/软件杯教育版系统设计说明书.md" "02-软件杯教育版系统设计说明书.md"
copy_document "docs/软件杯教育版数据库设计说明书.md" "03-软件杯教育版数据库设计说明书.md"
copy_document "docs/软件杯教育版权限与安全设计说明书.md" "04-软件杯教育版权限与安全设计说明书.md"
copy_document "API与测试/软件杯教育版测试说明书.md" "05-软件杯教育版测试说明书.md"
copy_document "docs/软件杯全功能系统测试报告-20260719.md" "06-软件杯教育版系统测试报告.md"
copy_document "部署与交付/部署与初始化.md" "07-部署与初始化.md"
copy_document "docs/软件杯教育版用户操作手册.md" "08-软件杯教育版用户操作手册.md"
copy_document "开源软件与许可证清单.md" "09-开源软件与许可证清单.md"
copy_document "部署与交付/AI-Coding工具使用说明.md" "10-AI-Coding工具使用说明.md"
copy_document "docs/API文档_教育版认证与会话.md" "API与测试/API文档_教育版认证与会话.md"
copy_document "docs/API文档_AI基座与知识库管理.md" "API与测试/API文档_AI基座与知识库管理.md"

for module_name in module-course module-profile module-assessment module-learningpath module-learningresource; do
  mkdir -p "$stage_dir/docs/API与测试/$module_name"
  rsync -a "$root_dir/docs/API与测试/$module_name/" "$stage_dir/docs/API与测试/$module_name/"
done

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
