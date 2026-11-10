package dev.temporal.learn.contract;

/** v2FinishExecution 的参数。方法在 apps/workflow-worker 的 BusinessActivitiesImpl。 */
public record FinishRequest(
        String runId,
        String agentExecutionId,
        String outputText,
        String outputRef) {
}
