package dev.temporal.learn.worker;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import dev.temporal.learn.contract.BusinessActivities;
import dev.temporal.learn.contract.ExecutionSnapshot;
import dev.temporal.learn.contract.FinishRequest;
import dev.temporal.learn.contract.FlowLog;
import dev.temporal.learn.contract.LearnQueues;
import dev.temporal.learn.contract.ModelActivities;
import dev.temporal.learn.contract.ReviewRequest;
import dev.temporal.learn.contract.RunInput;
import dev.temporal.learn.contract.RunResult;
import dev.temporal.learn.contract.RunWorkflow;
import dev.temporal.learn.contract.ToolSignal;
import dev.temporal.learn.contract.TurnRequest;
import dev.temporal.learn.contract.TurnResult;
import dev.temporal.learn.contract.WaitRequest;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;

/**
 * 编排状态机，跑在 apps/workflow-worker 的 joybot-workflow-v2 队列。
 * 每个 Workflow Task 都会从 execute 开头回放；已经完成的 Activity 不会再次执行。
 * 这里不能写 HTTP、SQL 或当前时间，外部结果只能来自 Activity 返回值和 Signal。
 */
public class RunWorkflowImpl implements RunWorkflow {
    /** signal-name 的收件箱。回放时 Temporal 会按历史重新调用 notifyChange。 */
    private final List<ToolSignal> inbox = new ArrayList<>();
    /** Query phase 读这个字段。它必须在 Workflow 代码里赋值，不能放进 Activity。 */
    private String phase = "STARTED";

    /**
     * 业务 Activity 代理，不是 gRPC 连接。连接在 WorkflowWorkerMain 里由 LearnTemporal.stubs 建立。
     * newActivityStub 只能在工作流回放中调用。执行 business.xxx 时，SDK 按这里的队列、超时和重试
     * 生成“调度 Activity”命令；workflow-worker 完成 Workflow Task 时，用已有通道交给 Frontend。
     * 真正的方法在 apps/business-worker/.../BusinessActivitiesImpl，队列是 joybot-business-v2。
     * 回放再次走到同一次调用时，代理从历史取回结果，不再投新任务。
     */
    private final BusinessActivities business = Workflow.newActivityStub(BusinessActivities.class,
            ActivityOptions.newBuilder()
                    .setTaskQueue(LearnQueues.BUSINESS)
                    .setStartToCloseTimeout(Duration.ofSeconds(30))
                    .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(3).build())
                    .build());

    /**
     * 模型 Activity 代理，机制与 business 相同：调用只产生调度命令，回放命中历史则直接返回。
     * 真正的方法在 apps/agent-worker 的 ModelActivitiesImpl.modelTurn，队列是 joybot-model-v2。
     * 心跳 10 秒是正式预算的缩小版，本 Demo 的方法立即返回。
     */
    private final ModelActivities model = Workflow.newActivityStub(ModelActivities.class,
            ActivityOptions.newBuilder()
                    .setTaskQueue(LearnQueues.MODEL)
                    .setStartToCloseTimeout(Duration.ofMinutes(2))
                    .setHeartbeatTimeout(Duration.ofSeconds(10))
                    .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(2).build())
                    .build());

    @Override
    public RunResult execute(RunInput input) {
        phase = "ADMISSION";
        // 回放时 isReplaying 为 true，不再打日志，避免同一步骤印多次。
        if (!Workflow.isReplaying()) {
            FlowLog.log("WORKFLOW", "编排开始，向 joybot-business-v2 调度 v2BeginExecution",
                    "workflowId=" + input.workflowId());
        }
        // 这不是对 BusinessActivitiesImpl 的直接调用。SDK 拦截后记下调度命令：
        // 类型名 V2BeginExecution，载荷是 input，任务队列来自 business 代理上的 joybot-business-v2。
        // 当前 Workflow Task 在此结束并把命令交给 History，History 写入 ActivityTaskScheduled，
        // Matching 按该队列名放任务。apps/business-worker 执行完并产生 ActivityTaskCompleted 后，
        // 回放再次走到这一行，才把 ExecutionSnapshot 赋给 snapshot。
        ExecutionSnapshot snapshot = business.v2BeginExecution(input);

        phase = "MODEL_TURN";
        if (!Workflow.isReplaying()) {
            FlowLog.log("WORKFLOW", "准入快照已返回，向 joybot-model-v2 调度第一轮 modelTurn",
                    "agentExecutionId=" + snapshot.agentExecutionId());
        }
        // 同一拦截机制。命令里的队列是 model 代理上的 joybot-model-v2，类型名 ModelTurn。
        // 第一轮没有工具回执。modelWorker 的完成事件进入历史后，这一行才得到 proposal。
        TurnResult proposal = model.modelTurn(new TurnRequest(
                input.runId(), snapshot.agentExecutionId(), 1, snapshot.appliedInputSeq(),
                List.of(), input.userText(), null));
        // 再次走 business 代理，类型名 V2CommitTurn，队列仍是 joybot-business-v2。
        // 返回值为空，同样要等 ActivityTaskCompleted 才执行后面的派发。
        business.v2CommitTurn(proposal);

        phase = "DISPATCH";
        if (!Workflow.isReplaying()) {
            FlowLog.log("WORKFLOW", "第一轮提议已提交，向 joybot-business-v2 调度派发和等待标记",
                    "operationId=" + proposal.operationId());
        }
        // 先派发原操作，再进入等待。回执由 demo-client 稍后用 Signal 送来。
        business.v2DispatchTools(proposal);
        business.markWaitingForToolSignal(new WaitRequest(input.workflowId(), proposal.operationId()));
        // phase 在 await 之前赋值，demo-client 的 Query 才能看到 WAIT_TOOL。
        phase = "WAIT_TOOL";
        if (!Workflow.isReplaying()) {
            FlowLog.log("WORKFLOW", "进入 Workflow.await，等待 signal-name 且 kind=TOOL",
                    "workflowId=" + input.workflowId() + " 超时 30 秒");
        }
        // 正式系统的保险定时器是 120 秒。到点返回 false，不把超时当成工具成功。
        boolean woke = Workflow.await(Duration.ofSeconds(30), () -> toolReceipt() != null);
        if (!woke) {
            phase = "NEEDS_REVIEW";
            if (!Workflow.isReplaying()) {
                FlowLog.log("WORKFLOW", "30 秒内没有工具回执，调度 v2MarkNeedsReview",
                        "workflowId=" + input.workflowId());
            }
            business.v2MarkNeedsReview(new ReviewRequest(input.runId(), "TOOL_RECEIPT_TIMEOUT"));
            return new RunResult("NEEDS_REVIEW", phase, null);
        }

        phase = "MODEL_TURN";
        if (!Workflow.isReplaying()) {
            FlowLog.log("WORKFLOW", "收到工具回执，向 joybot-model-v2 调度第二轮 modelTurn",
                    "operationId=" + toolReceipt().operationId());
        }
        // 第二轮把原回执交给模型队列，由模型活动先应用到会话再给出最终回答。
        TurnResult answer = model.modelTurn(new TurnRequest(
                input.runId(), snapshot.agentExecutionId(), 2, snapshot.appliedInputSeq(),
                List.of(toolReceipt().operationId()), input.userText(), toolReceipt()));
        business.v2CommitTurn(answer);

        phase = "FINISH";
        if (!Workflow.isReplaying()) {
            FlowLog.log("WORKFLOW", "第二轮已提交，向 joybot-business-v2 调度 v2FinishExecution",
                    "workflowId=" + input.workflowId());
        }
        business.v2FinishExecution(new FinishRequest(
                input.runId(), snapshot.agentExecutionId(), answer.outputText(), answer.outputRef()));
        phase = "COMPLETED";
        if (!Workflow.isReplaying()) {
            FlowLog.log("WORKFLOW", "编排结束", "status=SUCCEEDED phase=" + phase);
        }
        return new RunResult("SUCCEEDED", phase, answer.outputText());
    }

    /** 信号入口。线上信号名是 signal-name，见 RunWorkflow.notifyChange。 */
    @Override
    public void notifyChange(ToolSignal signal) {
        inbox.add(signal);
    }

    @Override
    public String phase() {
        return phase;
    }

    /** 只认 kind=TOOL。其他信号留在收件箱里，不唤醒这次等待。 */
    private ToolSignal toolReceipt() {
        for (ToolSignal signal : inbox) {
            if ("TOOL".equals(signal.kind())) {
                return signal;
            }
        }
        return null;
    }
}
