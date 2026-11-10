package dev.temporal.learn.contract;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/**
 * 单轮模型 Activity。实现在 apps/agent-worker 的 ModelActivitiesImpl，
 * 注册到 joybot-model-v2。它不决定下一步，下一步由 RunWorkflowImpl 决定。
 */
@ActivityInterface
public interface ModelActivities {
    @ActivityMethod
    TurnResult modelTurn(TurnRequest request);
}
