package dev.temporal.learn.contract;

import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * 一次业务 Run 的根工作流。实现在 apps/workflow-worker 的 RunWorkflowImpl。
 * demo-client 只拿这个接口做 Start、Query 和 Signal，不执行方法体。
 */
@WorkflowInterface
public interface RunWorkflow {
    /** 工作流类型名固定为 RunWorkflowV2，与 JoyBot 的根工作流同名。 */
    @WorkflowMethod(name = "RunWorkflowV2")
    RunResult execute(RunInput input);

    /**
     * 信号名是 notify，对应 JoyBot {@code @SignalMethod(name = "notify")}。
     * 信号由 History 持久化，Workflow 尚未执行到 await 时也会被缓冲。
     */
    @SignalMethod(name = "notify")
    void notifyChange(ToolSignal signal);

    /** 当前阶段。demo-client 轮询它，直到 WAIT_TOOL 才发送设备回执。 */
    @QueryMethod
    String phase();
}
