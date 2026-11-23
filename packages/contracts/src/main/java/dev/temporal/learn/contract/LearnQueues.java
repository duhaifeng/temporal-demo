package dev.temporal.learn.contract;

/**
 * 三个任务队列。
 * 队列只按能力分工：编排、控制面调用、单轮模型。公平、配额不由队列决定。
 *
 * 同一条队列可以有多个进程一起长轮询。Matching 把任务交给当时领到它的那个进程，
 * 代码里没有轮流分配。Activity 任务（控制面、模型）因此会落在该队列的任一实例上。
 * 同一次 Run 的 Workflow Task 会粘在先处理它的 workflow-worker 上；
 * 该进程退出或执行被赶出缓存后，另一个轮询 joybot-workflow-v2 的进程凭历史接上。
 */
public final class LearnQueues {
    /** workflow-type-name 所在队列，由 apps/workflow-worker 轮询。 */
    public static final String WORKFLOW = "joybot-workflow-v2";
    /** 控制面 Activity 队列，由 apps/business-worker 轮询。 */
    public static final String BUSINESS = "joybot-business-v2";
    /** 单轮模型 Activity 队列，由 apps/agent-worker 轮询。 */
    public static final String MODEL = "joybot-model-v2";

    private LearnQueues() {
    }
}
