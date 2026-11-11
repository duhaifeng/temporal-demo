package dev.temporal.learn.business;

import dev.temporal.learn.contract.BusinessActivities;
import dev.temporal.learn.contract.ExecutionSnapshot;
import dev.temporal.learn.contract.FinishRequest;
import dev.temporal.learn.contract.FlowLog;
import dev.temporal.learn.contract.ReviewRequest;
import dev.temporal.learn.contract.RunInput;
import dev.temporal.learn.contract.TurnResult;
import dev.temporal.learn.contract.WaitRequest;

/**
 * 控制面 Activity 的模拟实现，由 apps/business-worker 注册到 joybot-business-v2。
 * 正式系统这些方法会调用 apps/control-plane 的 HTTP，并由 MySQL 提交。
 * 本类只打印本来会写入的账，然后返回教学用的固定快照。
 */
public class BusinessActivitiesImpl implements BusinessActivities {
    /** 对应控制面 POST runs/{runId}/executions/7/begin。执行体在本类，不在 RunWorkflowImpl。 */
    @Override
    public ExecutionSnapshot v2BeginExecution(RunInput input) {
        FlowLog.log("BUSINESS",
                "Matching 把 Workflow Task 交给 joybot-workflow-v2；Worker 回放历史后执行到第一个 Activity",
                "History 事件：WorkflowExecutionStarted → WorkflowTaskScheduled → WorkflowTaskStarted");
        FlowLog.log("BUSINESS",
                "Activity v2BeginExecution  POST /internal/orchestration/v1/runs/" + input.runId()
                        + "/executions/7/begin",
                "队列 joybot-business-v2。控制面做准入：授权、配额、截止时间。通过后返回快照，不把 prompt 正文写入 Temporal");
        FlowLog.log("MYSQL",
                "读 agent_run / run_admission / execution_profile，写执行绑定",
                "runId=" + input.runId()
                        + " controlVersion=3 authorizationRevision=11 appliedInputSeq=1 resumeCheckpoint=none");
        return new ExecutionSnapshot("7", 3, 11, 1, input.deadlineAt());
    }

    /** 同一笔模拟事务提交步骤。TOOLS 记工具提议，其他 disposition 记最终输出引用。 */
    @Override
    public void v2CommitTurn(TurnResult turn) {
        if ("TOOLS".equals(turn.disposition())) {
            FlowLog.log("BUSINESS",
                    "Activity v2CommitTurn  同一个业务事务提交步骤、事件尾和工具提议",
                    "agent_step stepSeq=" + turn.stepSeq() + " status=COMMITTED；tool_operation id="
                            + turn.operationId() + " 尚未执行");
            FlowLog.log("MYSQL",
                    "thread_event 只追加「模型提议了 list_dir」。SSE 以后读这张表，不读 Temporal 历史",
                    "event_seq=18  kind=tool_proposal");
            return;
        }
        FlowLog.log("BUSINESS",
                "Activity v2CommitTurn  提交最终输出引用和输入水位",
                "stepSeq=" + turn.stepSeq() + " outputRef=" + turn.outputRef() + " appliedInputSeq=1");
        FlowLog.log("MYSQL",
                "thread_event 追加助手正文；artifact 表只保存引用，正文在对象存储",
                "event_seq=21  kind=assistant_message");
    }

    /** 派发原操作。回执还没到，所以这里不能 Signal。 */
    @Override
    public void v2DispatchTools(TurnResult turn) {
        FlowLog.log("BUSINESS",
                "Activity v2DispatchTools  控制面把原操作派到 DEVICE，不在 Workflow 里点击或列目录",
                "operationId=" + turn.operationId() + " route=" + turn.route() + " " + turn.toolName()
                        + " " + turn.toolInputPath());
        FlowLog.log("MYSQL",
                "写 tool_execution_command，并在同一事务写 event_outbox。此时还没有回执",
                "commandId=cmd-100 status=DISPATCHED");
    }

    /**
     * 这个 Activity 立即返回。随后 RunWorkflowImpl 才执行 Workflow.await。
     * 等待本身不占 Activity 槽。
     */
    @Override
    public void markWaitingForToolSignal(WaitRequest request) {
        FlowLog.log("BUSINESS",
                "已记下等待。真正的 Workflow.await 在 apps/workflow-worker/.../RunWorkflowImpl",
                "workflowId=" + request.workflowId() + " operationId=" + request.operationId()
                        + " 保险定时器在正式系统里是 120s，这里 Demo 用 30s");
    }

    /** 模拟把 agent_run 收成 SUCCEEDED。业务终态以这次打印所代表的控制面写入为准。 */
    @Override
    public void v2FinishExecution(FinishRequest request) {
        FlowLog.log("BUSINESS",
                "Activity v2FinishExecution  控制面把 Run 收成 SUCCEEDED，并释放准入名额",
                "runId=" + request.runId() + " executionId=" + request.agentExecutionId());
        FlowLog.log("MYSQL",
                "agent_run.status=SUCCEEDED。这一笔是业务终态；Temporal Workflow 完成只说明编排走完",
                "outputRef=" + request.outputRef());
    }

    /** 30 秒内没有 TOOL 信号时由 apps/workflow-worker/.../RunWorkflowImpl 调用。 */
    @Override
    public void v2MarkNeedsReview(ReviewRequest request) {
        FlowLog.log("BUSINESS",
                "工具回执超时。控制面标 NEEDS_REVIEW，不把超时当成目录已经列完",
                "runId=" + request.runId() + " reason=" + request.reason());
    }
}
