#!/usr/bin/env bash
# 非调试的一键路径：拉起 Temporal、打包应用。
# workflow-worker、business-worker、agent-worker 各启动两个进程，轮询同一组队列。
# 同一队列谁领到任务谁处理。Activity 的 pid 会在两个实例间变化。
# 同一次 Run 的编排粘在先接到的 workflow-worker 上；杀掉该 pid 后，另一个进程凭历史接上。
# 要逐步断点时不要用本脚本，改用 .vscode/launch.json 里的配置。
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
cd "$root"

./scripts/temporal-up.sh
for _ in $(seq 1 60); do
  if (echo >/dev/tcp/127.0.0.1/7233) >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

# JDK 25 对 Maven 的 Jansi/Guava，以及 gRPC Netty 的 sun.misc.Unsafe 会打兼容警告。
jvm=(--enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow)
MAVEN_OPTS="${jvm[*]}" mvn -q -DskipTests package
# 压住 Temporal SDK 的普通日志，只留 Demo 自己的顺序输出。
opts=("${jvm[@]}" -Dorg.slf4j.simpleLogger.defaultLogLevel=error)
java "${opts[@]}" -jar apps/workflow-worker/target/workflow-worker.jar &
workflow_pid_1=$!
java "${opts[@]}" -jar apps/workflow-worker/target/workflow-worker.jar &
workflow_pid_2=$!
java "${opts[@]}" -jar apps/business-worker/target/business-worker.jar &
business_pid_1=$!
java "${opts[@]}" -jar apps/business-worker/target/business-worker.jar &
business_pid_2=$!
java "${opts[@]}" -jar apps/agent-worker/target/agent-worker.jar &
agent_pid_1=$!
java "${opts[@]}" -jar apps/agent-worker/target/agent-worker.jar &
agent_pid_2=$!
echo "workflow-worker pid=${workflow_pid_1} ${workflow_pid_2}"
echo "business-worker pid=${business_pid_1} ${business_pid_2}"
echo "agent-worker pid=${agent_pid_1} ${agent_pid_2}"
cleanup() {
  kill "$workflow_pid_1" "$workflow_pid_2" "$business_pid_1" "$business_pid_2" "$agent_pid_1" "$agent_pid_2" >/dev/null 2>&1 || true
  wait "$workflow_pid_1" "$workflow_pid_2" "$business_pid_1" "$business_pid_2" "$agent_pid_1" "$agent_pid_2" 2>/dev/null || true
}
trap cleanup EXIT
# Worker 完成长轮询注册需要一点时间，再让 demo-client 发 Start。
sleep 2
java "${opts[@]}" -jar apps/demo-client/target/demo-client.jar
