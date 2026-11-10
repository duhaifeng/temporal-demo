package dev.temporal.learn.demo;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import dev.temporal.learn.contract.FlowLog;
import dev.temporal.learn.contract.LearnQueues;
import dev.temporal.learn.contract.LearnTemporal;
import dev.temporal.learn.contract.RunInput;
import dev.temporal.learn.contract.RunResult;
import dev.temporal.learn.contract.RunWorkflow;
import dev.temporal.learn.contract.ToolSignal;

import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.api.enums.v1.WorkflowIdReusePolicy;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowExecutionAlreadyStarted;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.serviceclient.WorkflowServiceStubs;

/**
 * 调试入口，不注册 Worker。一个进程按顺序扮演四个角色：
 * 员工页面、控制面事务、outbox publisher、设备回执。
 * 真正的 Workflow 和 Activity 在另外两个调试进程里执行。
 */
public final class DemoMain {
    private DemoMain() {
    }

    public static void main(String[] args) throws Exception {
        FlowLog.reset();
        System.out.println();
        System.out.println("JoyBot 数据流 MVP    Temporal 是真的，MySQL / 设备 / 模型是按正式链路模拟的");
        // 每次调试用新的 Workflow ID，才能反复打断点。固定 ID 时才会看到 REJECT_DUPLICATE。
        String configuredWorkflowId = System.getenv("LEARN_WORKFLOW_ID");
        String workflowId = configuredWorkflowId == null || configuredWorkflowId.isBlank()
                ? "joybot/run/" + System.currentTimeMillis()
                : configuredWorkflowId;
        RunInput input = new RunInput(
                workflowId,
                "88001",
                "42",
                "列出我云电脑桌面上的文件",
                "2026-11-09T12:00:00Z");
        FlowLog.log("TEMPORAL",
                "SDK 连接 " + LearnTemporal.address() + "  namespace=" + LearnTemporal.namespace()
                        + "；workflow-worker 轮询编排/业务队列，agent-worker 轮询模型队列",
                "预发正式名是 joybot-pre，本机用 default。joybot-workflow-v2=编排  joybot-business-v2=控制面 HTTP  joybot-model-v2=单轮模型");
        // 下面三条只打印正式系统会落库的事实，事务里不调用 Temporal。
        FlowLog.log("WEB", "员工在 Thread " + input.threadId() + " 发送输入", input.userText());
        FlowLog.log("CONTROL_PLANE",
                "一个数据库事务写入会话事实和启动意图。事务里不调用 Temporal",
                "agent_thread + run_input seq=1 + agent_run id=42 + agent_execution id=7 + event_outbox id=9001");
        FlowLog.log("OUTBOX",
                "publisher 领取 outbox 9001 后发起 StartWorkflowExecution；Frontend 校验 namespace 后转给 History，History 写入启动事件并向 Matching 登记 Workflow Task",
                "WorkflowType=RunWorkflowV2  WorkflowId=" + input.workflowId()
                        + "  IdReusePolicy=REJECT_DUPLICATE；service/frontend/workflow_handler.go StartWorkflowExecution → historyClient.StartWorkflowExecution");

        WorkflowServiceStubs stubs = LearnTemporal.stubs();
        WorkflowClient client = LearnTemporal.client(stubs);
        // 桩只描述 Workflow ID 和队列。execute 的方法体在 RunWorkflowImpl，不在本进程。
        RunWorkflow workflow = client.newWorkflowStub(RunWorkflow.class, WorkflowOptions.newBuilder()
                .setTaskQueue(LearnQueues.WORKFLOW)
                .setWorkflowId(input.workflowId())
                .setWorkflowIdReusePolicy(WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_REJECT_DUPLICATE)
                .setMemo(Map.of("outboxId", "9001", "story", "temporal-learn"))
                .build());
        try {
            // start 只把 RunWorkflowV2 交给 Frontend，然后立刻返回，不等待工作流结束。
            WorkflowExecution execution = WorkflowClient.start(workflow::execute, input);
            FlowLog.log("TEMPORAL",
                    "Start 已被接受。业务 Run 仍要等 v2BeginExecution 写完才算准入通过",
                    "temporalRunId=" + execution.getRunId());
            waitUntil(workflow, "WAIT_TOOL");
            signalDevice(workflow, input.workflowId());
            // 第二次调用工作流方法会再次 Start。这里只用已绑定的执行取结果。
            RunResult result = WorkflowStub.fromTyped(workflow).getResult(RunResult.class);
            FlowLog.log("WEB", "员工页面经控制面 SSE 看到助手消息。页面不订阅 Temporal 历史", result.outputText());
            FlowLog.log("DONE",
                    "Workflow 结果 status=" + result.status() + " phase=" + result.phase(),
                    "打开 http://127.0.0.1:8233 查看这次执行的事件历史");
        } catch (WorkflowExecutionAlreadyStarted ex) {
            FlowLog.log("TEMPORAL",
                    "同一 WorkflowId 已有执行，History 按 REJECT_DUPLICATE 拒绝第二次 Start",
                    "这就是 outbox 重投不会复制 Run 的原因。清掉这次历史：docker compose down && docker compose up -d");
        } finally {
            stubs.shutdown();
        }
    }

    /** Query phase。Workflow 还没完成第一个任务时查询会失败，忽略后继续等。 */
    private static void waitUntil(RunWorkflow workflow, String expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + Duration.ofSeconds(30).toMillis();
        String phase = "";
        while (System.currentTimeMillis() < deadline) {
            try {
                phase = workflow.phase();
            } catch (RuntimeException ex) {
                phase = "";
            }
            if (expected.equals(phase)) {
                return;
            }
            Thread.sleep(200);
        }
        throw new IllegalStateException("30 秒内没有进入 " + expected + "，当前 phase=" + phase
                + "。先确认 apps/workflow-worker 和 apps/agent-worker 已启动");
    }

    /**
     * 模拟设备写完 journal 后，由 outbox 发 Signal notify。
     * workflow.notifyChange 不执行 RunWorkflowImpl 的方法体，只向 Frontend 提交信号。
     */
    private static void signalDevice(RunWorkflow workflow, String workflowId) {
        FlowLog.log("DEVICE",
                "Electron / Computer Host 先写 journal intent，再执行 list_dir，然后写 receipt",
                "operationId=tool-100 path=/home/user/Desktop files=值班表.xlsx,笔记.txt");
        FlowLog.log("CONTROL_PLANE",
                "设备回执进控制面：校验原 operation、设备代次和 fence，同事务写账本与 TOOL outbox",
                "outboxId=9002 kind=SIGNAL signal=notify payload.kind=TOOL");
        FlowLog.log("OUTBOX",
                "publisher 在事务外调用 SignalWorkflowExecution。published_at 只表示 Temporal 已接受",
                "workflowId=" + workflowId + " operationId=tool-100");
        workflow.notifyChange(new ToolSignal(
                "TOOL", "tool-100", "RECEIPTED", List.of("值班表.xlsx", "笔记.txt")));
        FlowLog.log("TEMPORAL",
                "Frontend 收到 Signal，History 追加 WorkflowExecutionSignaled，Matching 再投递一个 Workflow Task",
                "service/frontend/workflow_handler.go SignalWorkflowExecution；信号可重复，Workflow 按 operationId 去重");
    }
}
