package dev.temporal.learn.agent;

import java.util.concurrent.CountDownLatch;

import dev.temporal.learn.contract.FlowLog;
import dev.temporal.learn.contract.LearnQueues;
import dev.temporal.learn.contract.LearnTemporal;

import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;

/**
 * agent-worker 调试入口。只轮询 joybot-model-v2，执行 ModelActivitiesImpl。
 * 它不持有循环状态，也不注册 Workflow。
 *
 * 与 workflow-worker 没有对象引用。两边对齐的是 learn-contracts 里的三样东西：
 * 队列名 joybot-model-v2、Activity 类型名 modelTurn、可序列化的 TurnRequest / TurnResult。
 * apps/workflow-worker/.../RunWorkflowImpl 调用 ModelActivities 桩时，Temporal 把任务放到该队列；
 * 本进程拉到后按类型名交给本目录的 ModelActivitiesImpl。实现类不进入 workflow-worker 的 classpath。
 */
public final class AgentWorkerMain {
    private AgentWorkerMain() {
    }

    public static void main(String[] args) throws InterruptedException {
        // packages/contracts/.../LearnTemporal.stubs：到底层 gRPC 通道。
        // 地址默认 127.0.0.1:7233，可用环境变量 TEMPORAL_ADDRESS 覆盖。退出前要 shutdown。
        WorkflowServiceStubs stubs = LearnTemporal.stubs();
        // packages/contracts/.../LearnTemporal.client：在这条通道上带上 namespace。
        // 默认 default，可用 TEMPORAL_NAMESPACE 覆盖。stubs 本身不带 namespace。
        WorkflowClient client = LearnTemporal.client(stubs);
        // 工厂持有 client，负责创建并统一启停 Worker，本身不轮询。
        WorkerFactory factory = WorkerFactory.newInstance(client);
        // SDK 的 Worker，只登记任务队列 joybot-model-v2。newWorker 只登记，还不拉任务。
        // 发任务的一方是 apps/workflow-worker/.../RunWorkflowImpl 的 ModelActivities 桩，
        // 桩的 setTaskQueue 同样是 LearnQueues.MODEL。
        Worker modelWorker = factory.newWorker(LearnQueues.MODEL);
        // 注册本进程的实现。入站载荷是序列化后的 TurnRequest，返回值序列化成 TurnResult 写回历史。
        modelWorker.registerActivitiesImplementations(new ModelActivitiesImpl());
        // 停调试时先停轮询线程，再放开 main，避免进程先退出而 Worker 线程还在跑。
        CountDownLatch running = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            factory.shutdown();
            stubs.shutdown();
            running.countDown();
        }));
        // 后台线程向 Matching 长轮询 joybot-model-v2，请求里带着 namespace 和队列名，只拉 Activity 任务。
        // 没有任务时轮询挂在服务端，超时后立刻再拉。start 本身立即返回。
        // 任务来自 RunWorkflowImpl.model.modelTurn：类型名 modelTurn、序列化的 TurnRequest、回传用的 task token。
        // SDK 还原参数后调用 apps/agent-worker/.../ModelActivitiesImpl.modelTurn，
        // 再把 TurnResult 序列化，用同一个 task token 完成这次 Activity。
        factory.start();
        FlowLog.log("AGENT-WORKER", "已开始轮询 " + LearnQueues.MODEL, null);
        // 主线程停在这里维持进程。轮询在 factory.start 拉起的后台线程里。
        running.await();
    }
}
