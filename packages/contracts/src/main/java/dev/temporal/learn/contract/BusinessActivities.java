package dev.temporal.learn.contract;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/**
 * 控制面 Activity。实现在 apps/workflow-worker 的 BusinessActivitiesImpl，
 * 注册到 joybot-business-v2。正式系统里这些方法是发往控制面的 HTTP。
 */
@ActivityInterface
public interface BusinessActivities {
    /** 根执行入场一次：准入、授权、恢复快照。 */
    @ActivityMethod
    ExecutionSnapshot v2BeginExecution(RunInput input);

    /** 提交本轮步骤、事件尾和工具提议，或提交最终输出引用。 */
    @ActivityMethod
    void v2CommitTurn(TurnResult turn);

    /** 把已提交的工具操作派到 DEVICE。此时还没有回执。 */
    @ActivityMethod
    void v2DispatchTools(TurnResult turn);

    /** 只记录“接下来 Workflow 会等待信号”。真正的等待在 RunWorkflowImpl.await。 */
    @ActivityMethod
    void markWaitingForToolSignal(WaitRequest request);

    /** 把业务 Run 收成 SUCCEEDED，并释放准入名额。 */
    @ActivityMethod
    void v2FinishExecution(FinishRequest request);

    /** 回执超时。保留待复核，不把超时当成工具已经成功。 */
    @ActivityMethod
    void v2MarkNeedsReview(ReviewRequest request);
}
