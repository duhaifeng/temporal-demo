#!/usr/bin/env bash
# 非调试的一键路径：拉起 Temporal、打包三个应用、先后台启动两个 Worker，再跑 demo-client。
# 要逐步断点时不要用本脚本，改用 .vscode/launch.json 里的三个配置。
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
cd "$root"

docker compose up -d
for _ in $(seq 1 30); do
  if (echo >/dev/tcp/127.0.0.1/7233) >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

mvn -q -DskipTests package
# 压住 Temporal SDK 的普通日志，只留 Demo 自己的顺序输出。
opts=(-Dorg.slf4j.simpleLogger.defaultLogLevel=error)
java "${opts[@]}" -jar apps/workflow-worker/target/workflow-worker.jar &
workflow_pid=$!
java "${opts[@]}" -jar apps/agent-worker/target/agent-worker.jar &
agent_pid=$!
cleanup() {
  kill "$workflow_pid" "$agent_pid" >/dev/null 2>&1 || true
  wait "$workflow_pid" "$agent_pid" 2>/dev/null || true
}
trap cleanup EXIT
# Worker 完成长轮询注册需要一点时间，再让 demo-client 发 Start。
sleep 2
java "${opts[@]}" -jar apps/demo-client/target/demo-client.jar
