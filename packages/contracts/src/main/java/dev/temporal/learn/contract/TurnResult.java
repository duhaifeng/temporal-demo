package dev.temporal.learn.contract;

/**
 * 一轮模型提交给控制面的结果。TOOLS 表示还要派发工具，FINAL 表示可以收尾。
 */
public record TurnResult(
        int stepSeq,
        /** TOOLS 或 FINAL。 */
        String disposition,
        /** 工具操作身份。FINAL 时为空。 */
        String operationId,
        /** 执行目标。本故事是 DEVICE。 */
        String route,
        String toolName,
        String toolInputPath,
        /** 最终回答在对象存储中的引用。正文不进 Temporal 历史。 */
        String outputRef,
        String outputText) {
}
