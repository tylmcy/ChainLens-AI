#!/bin/sh
set -eu

# 从脚本自身定位仓库，所有路径都加引号，可正常处理 macOS 含空格/中文的目录。
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROJECT_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
APP_JAR="$PROJECT_DIR/chainlens-app/target/chainlens-app-0.1.0-SNAPSHOT.jar"

if [ ! -f "$APP_JAR" ]; then
  echo "未找到 ChainLens 可执行 jar：$APP_JAR" >&2
  echo "请先在项目根目录执行 mvn -B -pl chainlens-app -am package" >&2
  exit 2
fi

# 不在脚本中安装依赖、写入秘密或向 stdout 打印提示；stdout 保留给 MCP 帧。
exec java -Dspring.profiles.active=mcp-stdio -jar "$APP_JAR" "$@"
