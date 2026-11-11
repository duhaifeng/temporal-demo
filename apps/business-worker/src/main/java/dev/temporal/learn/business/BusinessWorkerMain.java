package dev.temporal.learn.business;

import java.util.concurrent.CountDownLatch;

import dev.temporal.learn.contract.FlowLog;
import dev.temporal.learn.contract.LearnQueues;
import dev.temporal.learn.contract.LearnTemporal;

import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;

/**
 * business-worker 调试入口。只轮询 joybot-business-v2，执行 BusinessActivitiesImpl。
 * 它不注册 Workflow。编排在 apps/workflow-worker 的 RunWorkflowImpl，靠队列名把任务投到这里。
 */
public final class BusinessWorkerMain {
    private BusinessWorkerMain() {
    }

    public static void main(String[] args) throws InterruptedException {
        WorkflowServiceStubs stubs = LearnTemporal.stubs();
        WorkflowClient client = LearnTemporal.client(stubs);
        WorkerFactory factory = WorkerFactory.newInstance(client);
        Worker businessWorker = factory.newWorker(LearnQueues.BUSINESS);
        businessWorker.registerActivitiesImplementations(new BusinessActivitiesImpl());
        // 停调试时先停轮询线程，再放开 main，避免进程先退出而 Worker 线程还在跑。
        CountDownLatch running = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            factory.shutdown();
            stubs.shutdown();
            running.countDown();
        }));
        factory.start();
        FlowLog.log("BUSINESS", "已开始轮询 " + LearnQueues.BUSINESS, null);
        running.await();
    }
}
