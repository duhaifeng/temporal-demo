package dev.temporal.learn.contract;

import java.util.List;

/**
 * 信号 signal-name 的载荷。本 Demo 只发送 kind=TOOL。
 */
public record ToolSignal(
        String kind,
        String operationId,
        /** RECEIPTED 表示设备已写回执。 */
        String progress,
        /** 列目录的模拟结果。 */
        List<String> files) {
}
