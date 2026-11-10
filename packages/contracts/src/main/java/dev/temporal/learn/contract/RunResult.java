package dev.temporal.learn.contract;

/**
 * Workflow 方法的返回值。status 是编排结果；
 * 正式系统里业务终态以控制面写入的 agent_run 为准，二者可能不一致。
 */
public record RunResult(String status, String phase, String outputText) {
}
