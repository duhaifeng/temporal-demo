package dev.temporal.learn.worker;

import java.util.concurrent.CountDownLatch;

import dev.temporal.learn.contract.LearnQueues;
import dev.temporal.learn.contract.LearnTemporal;

import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.worker.WorkerFactory;

/**
 * workflow-worker 调试入口。一个进程轮询两条队列：
 * joybot-workflow-v2 执行 RunWorkflowImpl，joybot-business-v2 执行 BusinessActivitiesImpl。
 * main 停在 await，调试会话才会一直挂着。
 */
public final class WorkflowWorkerMain {
    private WorkflowWorkerMain() {
    }

    public static void main(String[] args) throws InterruptedException {
        WorkflowServiceStubs stubs = LearnTemporal.stubs();
        WorkflowClient client = LearnTemporal.client(stubs);
        WorkerFactory factory = WorkerFactory.newInstance(client);
        var workflows = factory.newWorker(LearnQueues.WORKFLOW);
        workflows.registerWorkflowImplementationTypes(RunWorkflowImpl.class);
        var business = factory.newWorker(LearnQueues.BUSINESS);
        business.registerActivitiesImplementations(new BusinessActivitiesImpl());
        // 停调试时钩子先撤轮询，再放开 main，避免进程先退出而 Worker 线程还在跑。
        CountDownLatch running = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            factory.shutdown();
            stubs.shutdown();
            running.countDown();
        }));
        factory.start();
        System.out.println("workflow-worker 已开始轮询 " + LearnQueues.WORKFLOW + " 和 " + LearnQueues.BUSINESS);
        running.await();
    }
}
