# Temporal Learn

对照 JoyBot 的一次 v2 Run，看数据怎样从页面走到 Temporal，再回到页面。Temporal 历史是真的。MySQL、模型和云电脑按正式链路打印，没有连接 JoyBot 的库。

## 和 JoyBot 的对应

| 本仓库 | JoyBot | 做什么 |
|---|---|---|
| `packages/contracts` | `packages/contracts` | Workflow / Activity 接口和跨进程顺序日志 |
| `apps/workflow-worker` | `apps/workflow-worker` | `RunWorkflowV2`，轮询 `joybot-workflow-v2` 和 `joybot-business-v2` |
| `apps/agent-worker` | `apps/agent-worker` | 单轮模型 Activity，轮询 `joybot-model-v2`。JoyBot 这里是 Node，本 Demo 用 Java 模拟 |
| `apps/demo-client` | 员工 Web + 控制面 outbox | 写启动意图、Start、等 `phase=WAIT_TOOL`、用设备回执 Signal |

字节码目标是 Java 17。本机用 JDK 21 运行即可。

## 运行

```bash
cd /home/duhaifeng/Workspace/temporal-learn
./scripts/run-demo.sh
```

UI：http://127.0.0.1:8233。Namespace 是 `default`。每次启动 Demo 会使用新的 Workflow ID `joybot/run/<当前毫秒>`，业务 Run 仍是 `42`。要看同一 ID 被 `REJECT_DUPLICATE` 拒绝时，设置 `LEARN_WORKFLOW_ID=joybot/run/42` 连续启动两次。

停服务：`docker compose down`。

## 分别调试

先执行 `docker compose up -d`。然后在运行和调试里分别启动这三个配置，不要用 `run-demo.sh`：

1. `workflow-worker`：断点打在 `RunWorkflowImpl` 和 `BusinessActivitiesImpl`。终端出现「已开始轮询」后再启动下一个。
2. `agent-worker`：断点打在 `ModelActivitiesImpl`。
3. `demo-client`：断点打在 `DemoMain`。它负责 Start 和发送工具回执。

Activity 断点在对应 Worker 进程里停一次。`RunWorkflowImpl` 会在每个 Workflow Task 回放时再执行，已经完成的 Activity 不会再次运行。每次重新调试 Demo 都是一条新的 Workflow，两个 Worker 保持原调试会话即可。
