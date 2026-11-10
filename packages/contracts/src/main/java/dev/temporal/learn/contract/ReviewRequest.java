package dev.temporal.learn.contract;

/** v2MarkNeedsReview 的参数。工具回执超时后使用，方法在 BusinessActivitiesImpl。 */
public record ReviewRequest(String runId, String reason) {
}
