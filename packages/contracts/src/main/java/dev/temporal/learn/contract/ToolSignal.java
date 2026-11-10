package dev.temporal.learn.contract;

import java.util.List;

/**
 * Signal {@code notify} 的载荷。正式 JoyBot 按 kind 区分 TOOL、INPUT、CONTROL、RESOURCE。
 * 本 Demo 只发送 kind=TOOL。
 */
public record ToolSignal(
        String kind,
        String operationId,
        /** RECEIPTED 表示设备已写回执。 */
        String progress,
        /** 列目录的模拟结果。 */
        List<String> files) {
}
