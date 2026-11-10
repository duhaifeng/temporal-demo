package dev.temporal.learn.agent;

import java.util.concurrent.CountDownLatch;

import dev.temporal.learn.contract.LearnQueues;
import dev.temporal.learn.contract.LearnTemporal;

import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.worker.WorkerFactory;

/**
 * agent-worker 调试入口。只轮询 joybot-model-v2，执行 ModelActivitiesImpl。
 * 它不持有循环状态，也不注册 Workflow。
 */
public final class AgentWorkerMain {
    private AgentWorkerMain() {
    }

    public static void main(String[] args) throws InterruptedException {
        WorkflowServiceStubs stubs = LearnTemporal.stubs();
        WorkflowClient client = LearnTemporal.client(stubs);
        WorkerFactory factory = WorkerFactory.newInstance(client);
        var model = factory.newWorker(LearnQueues.MODEL);
        model.registerActivitiesImplementations(new ModelActivitiesImpl());
        // 与 workflow-worker 相同：停调试时先关闭 Worker，再结束 main。
        CountDownLatch running = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            factory.shutdown();
            stubs.shutdown();
            running.countDown();
        }));
        factory.start();
        System.out.println("agent-worker 已开始轮询 " + LearnQueues.MODEL);
        running.await();
    }
}
