package dev.temporal.learn.contract;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/**
 * 单轮模型 Activity。实现在 apps/agent-worker 的 ModelActivitiesImpl，
 * 注册到 joybot-model-v2。它不决定下一步，下一步由 RunWorkflowImpl 决定。
 *
 * workflow-worker 只有本接口生成的调用桩，agent-worker 只有 ModelActivitiesImpl。
 * 两边不传递实现类。SDK 1.32 的 DefaultDataConverter 把参数和返回值交给
 * JacksonJsonPayloadConverter：Jackson 按字段写成 JSON（编码 json/plain），载荷里没有实现类名。
 * 收到任务或结果的进程用自己方法签名上的类型做还原：agent-worker 用
 * ModelActivitiesImpl.modelTurn 的参数类型 TurnRequest，workflow-worker 用本接口的返回类型 TurnResult。
 * 这两个 record 都在 learn-contracts，两个进程的 classpath 上都有。
 */
@ActivityInterface
public interface ModelActivities {
    /**
     * 类型名默认是方法名 modelTurn。入参、返回值经 Jackson 做对象与 JSON 的转换后过队列。
     */
    @ActivityMethod
    TurnResult modelTurn(TurnRequest request);
}
