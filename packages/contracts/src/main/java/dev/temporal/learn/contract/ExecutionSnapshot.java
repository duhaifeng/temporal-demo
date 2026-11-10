package dev.temporal.learn.contract;

/**
 * v2BeginExecution 的返回值，对应控制面准入通过后的执行快照。
 * Demo 不访问 MySQL，数字是写死的教学样本。
 */
public record ExecutionSnapshot(
        /** 模拟的 agent_execution.id。 */
        String agentExecutionId,
        /** 撤权和写入校验用的控制版本。 */
        int controlVersion,
        /** 授权修订号。 */
        int authorizationRevision,
        /** 已经应用到本执行的输入水位。 */
        long appliedInputSeq,
        String deadlineAt) {
}
