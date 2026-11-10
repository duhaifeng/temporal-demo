package dev.temporal.learn.worker;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import dev.temporal.learn.contract.BusinessActivities;
import dev.temporal.learn.contract.FinishRequest;
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
    /** notify 信号的收件箱。回放时 Temporal 会按历史重新调用 notifyChange。 */
    private final List<ToolSignal> inbox = new ArrayList<>();
    /** Query phase 读这个字段。它必须在 Workflow 代码里赋值，不能放进 Activity。 */
    private String phase = "STARTED";

    /**
     * 业务 Activity 桩。真正的方法在同进程的 BusinessActivitiesImpl，
     * 但调度到 joybot-business-v2，和编排任务分开排队。
     */
    private final BusinessActivities business = Workflow.newActivityStub(BusinessActivities.class,
            ActivityOptions.newBuilder()
                    .setTaskQueue(LearnQueues.BUSINESS)
                    .setStartToCloseTimeout(Duration.ofSeconds(30))
                    .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(3).build())
                    .build());

    /**
     * 模型 Activity 桩。真正的方法在 apps/agent-worker 的 ModelActivitiesImpl.modelTurn，
     * 队列是 joybot-model-v2。心跳 10 秒是正式预算的缩小版，本 Demo 的方法立即返回。
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
        // apps/workflow-worker/.../BusinessActivitiesImpl.v2BeginExecution
        var snapshot = business.v2BeginExecution(input);

        phase = "MODEL_TURN";
        // apps/agent-worker/.../ModelActivitiesImpl.modelTurn，第一轮没有工具回执
        TurnResult proposal = model.modelTurn(new TurnRequest(
                input.runId(), snapshot.agentExecutionId(), 1, snapshot.appliedInputSeq(),
                List.of(), input.userText(), null));
        // apps/workflow-worker/.../BusinessActivitiesImpl.v2CommitTurn
        business.v2CommitTurn(proposal);

        phase = "DISPATCH";
        // 先派发原操作，再进入等待。回执由 demo-client 稍后用 Signal 送来。
        business.v2DispatchTools(proposal);
        business.markWaitingForToolSignal(new WaitRequest(input.workflowId(), proposal.operationId()));
        // phase 在 await 之前赋值，demo-client 的 Query 才能看到 WAIT_TOOL。
        phase = "WAIT_TOOL";
        // 正式系统的保险定时器是 120 秒。到点返回 false，不把超时当成工具成功。
        boolean woke = Workflow.await(Duration.ofSeconds(30), () -> toolReceipt() != null);
        if (!woke) {
            phase = "NEEDS_REVIEW";
            business.v2MarkNeedsReview(new ReviewRequest(input.runId(), "TOOL_RECEIPT_TIMEOUT"));
            return new RunResult("NEEDS_REVIEW", phase, null);
        }

        phase = "MODEL_TURN";
        // 第二轮把原回执交给模型队列，由模型活动先应用到会话再给出最终回答。
        TurnResult answer = model.modelTurn(new TurnRequest(
                input.runId(), snapshot.agentExecutionId(), 2, snapshot.appliedInputSeq(),
                List.of(toolReceipt().operationId()), input.userText(), toolReceipt()));
        business.v2CommitTurn(answer);

        phase = "FINISH";
        business.v2FinishExecution(new FinishRequest(
                input.runId(), snapshot.agentExecutionId(), answer.outputText(), answer.outputRef()));
        phase = "COMPLETED";
        return new RunResult("SUCCEEDED", phase, answer.outputText());
    }

    /** 信号入口。正式信号名是 notify，见 RunWorkflow.notifyChange。 */
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
