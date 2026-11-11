package dev.temporal.learn.worker;

import java.util.concurrent.CountDownLatch;

import dev.temporal.learn.contract.FlowLog;
import dev.temporal.learn.contract.LearnQueues;
import dev.temporal.learn.contract.LearnTemporal;

import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;

/**
 * workflow-worker 调试入口。只轮询 joybot-workflow-v2，执行 RunWorkflowImpl。
 * 控制面 Activity 在 apps/business-worker。main 停在 await，调试会话才会一直挂着。
 */
public final class WorkflowWorkerMain {
    private WorkflowWorkerMain() {
    }

    public static void main(String[] args) throws InterruptedException {
        WorkflowServiceStubs stubs = LearnTemporal.stubs();
        WorkflowClient client = LearnTemporal.client(stubs);
        WorkerFactory factory = WorkerFactory.newInstance(client);
        Worker workflowWorker = factory.newWorker(LearnQueues.WORKFLOW);
        workflowWorker.registerWorkflowImplementationTypes(RunWorkflowImpl.class);
        // 停调试时钩子先撤轮询，再放开 main，避免进程先退出而 Worker 线程还在跑。
        CountDownLatch running = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            factory.shutdown();
            stubs.shutdown();
            running.countDown();
        }));
        factory.start();
        FlowLog.log("WORKFLOW", "已开始轮询 " + LearnQueues.WORKFLOW, null);
        running.await();
    }
}
