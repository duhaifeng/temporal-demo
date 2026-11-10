package dev.temporal.learn.contract;

/**
 * 三个任务队列，名字与 JoyBot {@code LoopV2Queues} 一致。
 * 队列只按能力分工：编排、控制面调用、单轮模型。公平、配额不由队列决定。
 */
public final class LearnQueues {
    /** RunWorkflowV2 所在队列，由 apps/workflow-worker 轮询。 */
    public static final String WORKFLOW = "joybot-workflow-v2";
    /** 控制面 Activity 队列，本 Demo 里仍由 apps/workflow-worker 轮询。 */
    public static final String BUSINESS = "joybot-business-v2";
    /** 单轮模型 Activity 队列，由 apps/agent-worker 轮询。 */
    public static final String MODEL = "joybot-model-v2";

    private LearnQueues() {
    }
}
