package dev.temporal.learn.contract;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;

/**
 * 连接本机 Temporal 开发服务。三个应用都走这里，避免各写一份地址。
 * 本机开发服务使用 default。
 */
public final class LearnTemporal {
    private LearnTemporal() {
    }

    /** gRPC 地址。可用环境变量 TEMPORAL_ADDRESS 覆盖。 */
    public static String address() {
        String address = System.getenv("TEMPORAL_ADDRESS");
        return address == null || address.isBlank() ? "127.0.0.1:7233" : address;
    }

    /** 可用环境变量 TEMPORAL_NAMESPACE 覆盖。 */
    public static String namespace() {
        String namespace = System.getenv("TEMPORAL_NAMESPACE");
        return namespace == null || namespace.isBlank() ? "default" : namespace;
    }

    /** 底层 gRPC 通道。进程退出前要 shutdown。 */
    public static WorkflowServiceStubs stubs() {
        return WorkflowServiceStubs.newServiceStubs(WorkflowServiceStubsOptions.newBuilder()
                .setTarget(address())
                .build());
    }

    /** 在已有通道上创建带 namespace 的客户端。 */
    public static WorkflowClient client(WorkflowServiceStubs stubs) {
        return WorkflowClient.newInstance(stubs, WorkflowClientOptions.newBuilder()
                .setNamespace(namespace())
                .build());
    }
}
