package dev.temporal.learn.contract;

/** markWaitingForToolSignal 的参数。方法在 apps/business-worker 的 BusinessActivitiesImpl。 */
public record WaitRequest(String workflowId, String operationId) {
}
