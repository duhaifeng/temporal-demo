package dev.temporal.learn.contract;

import java.util.List;

/**
 * 交给模型队列的一轮请求。由 RunWorkflowImpl 在编排队列里构造，
 * 实际方法在 apps/agent-worker 的 ModelActivitiesImpl.modelTurn。
 */
public record TurnRequest(
        String runId,
        String agentExecutionId,
        /** 逻辑步骤号。重试仍用同一个 stepSeq，不因 Activity attempt 加一。 */
        int stepSeq,
        long appliedInputSeq,
        /** 本轮开始前要应用到会话的原工具操作。第一轮为空。 */
        List<String> applyToolOperationIds,
        String userText,
        /** 第二轮携带的 TOOL 信号。第一轮为 null。 */
        ToolSignal toolReceipt) {
}
