package dev.temporal.learn.contract;

/**
 * 一次 Run 的启动参数，会进入 Temporal 历史。
 * 这里只放标识和用户原文摘要；正式系统不会把 token 流、截图放进历史。
 */
public record RunInput(
        /** Temporal Workflow ID，默认 joybot/run/当前毫秒。不是业务 Run ID。 */
        String workflowId,
        /** 模拟的 agent_thread.id。 */
        String threadId,
        /** 模拟的业务 agent_run.id，固定为 42。 */
        String runId,
        /** 员工输入。正式系统正文在 MySQL / 对象存储，这里为了阅读直接带着。 */
        String userText,
        /** 业务截止时间。Demo 不据此取消。 */
        String deadlineAt) {
}
