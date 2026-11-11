# Temporal Learn

对照 JoyBot 的一次 v2 Run，看数据怎样从页面走到 Temporal，再回到页面。Temporal 历史是真的。MySQL、模型和云电脑按正式链路打印，没有连接 JoyBot 的库。

## 和 JoyBot 的对应

| 本仓库 | JoyBot | 做什么 |
|---|---|---|
| `packages/contracts` | `packages/contracts` | Workflow / Activity 接口和跨进程顺序日志 |
| `apps/workflow-worker` | `apps/workflow-worker` | 工作流类型 `workflow-type-name`，轮询 `joybot-workflow-v2`。JoyBot 的类型名是 `RunWorkflowV2` |
| `apps/business-worker` | `apps/workflow-worker` 里的控制面轮询 | `BusinessActivitiesImpl`，轮询 `joybot-business-v2`。JoyBot 里这条队列仍由 workflow-worker 轮询，本 Demo 把它拆成独立进程 |
| `apps/agent-worker` | `apps/agent-worker` | 单轮模型 Activity，轮询 `joybot-model-v2`。JoyBot 这里是 Node，本 Demo 用 Java 模拟 |
| `apps/demo-client` | 员工 Web + 控制面 outbox | 写启动意图、Start、等 `phase=WAIT_TOOL`、用设备回执 Signal |

字节码目标是 Java 17。本机用 JDK 21 运行即可。

## 运行

```bash
cd /home/duhaifeng/Workspace/temporal-learn
./scripts/run-demo.sh
```

UI：http://127.0.0.1:8233。Namespace 是 `default`。每次启动 Demo 会使用新的 Workflow ID `joybot/run/<当前毫秒>`，业务 Run 仍是 `42`。Workflow ID 由调用方指定，一个 Workflow ID 可以对应多个 Run ID；Run ID 由 Temporal 在 Start 被接受时生成，只属于这一个 Workflow ID。本 Demo 使用 `REJECT_DUPLICATE` 且每次更换 Workflow ID，所以每次运行是一对一。同一 Workflow ID 出现多个 Run ID，发生在上次执行已结束且复用策略允许再次启动、Continue-As-New，或从历史 Reset。要看同一 ID 被 `REJECT_DUPLICATE` 拒绝时，设置 `LEARN_WORKFLOW_ID=joybot/run/42` 连续启动两次。

停服务：`container stop joybot-learn-temporal`，再执行 `kill $(cat /tmp/joybot-learn-temporal-proxy.pid)`。

`run-demo.sh` 把三种 Worker 各启动两份，日志里的 `pid` 是领到任务的那个 JVM。同一条队列上谁先从 Matching 领到任务谁处理，不是按进程轮流。控制面和模型的每次 Activity 都可能换一个 pid。同一次 Run 的编排会粘在先接到它的那个 `workflow-worker` 上，所以正常跑完时 `WORKFLOW` 行是同一个 pid；杀掉这个 pid 后，另一个 `workflow-worker` 凭事件历史从中断处继续。

## 分别调试

先执行 `./scripts/temporal-up.sh` 拉起 Temporal。然后在运行和调试里分别启动这四个配置，不要用 `run-demo.sh`：

1. `workflow-worker`：断点打在 `RunWorkflowImpl`。终端出现「已开始轮询」后再启动下一个。
2. `business-worker`：断点打在 `BusinessActivitiesImpl`。
3. `agent-worker`：断点打在 `ModelActivitiesImpl`。
4. `demo-client`：断点打在 `DemoMain`。它负责 Start 和发送工具回执。

Activity 断点在对应 Worker 进程里停一次。`RunWorkflowImpl` 会在每个 Workflow Task 回放时再执行，已经完成的 Activity 不会再次运行。每次重新调试 Demo 都是一条新的 Workflow，三个 Worker 保持原调试会话即可。
