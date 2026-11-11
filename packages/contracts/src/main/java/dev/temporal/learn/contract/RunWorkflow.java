package dev.temporal.learn.contract;

import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * 一次业务 Run 的根工作流。实现在 apps/workflow-worker 的 RunWorkflowImpl。
 * demo-client 只拿这个接口做 Start、Query 和 Signal，不执行方法体。
 */
@WorkflowInterface
public interface RunWorkflow {
    /**
     * name 是工作流类型名，用来在一个任务队列上区分不同的工作流代码。
     * 一个任务队列可以对应多个类型名，一个类型名只对应一个实现类。
     * Start 只把这个字符串写入历史。Worker 收到任务后，用它选择已注册的实现类。
     * 本 Demo 的类型名是 workflow-type-name，对应的实现类是 RunWorkflowImpl。
     * （备注：不用过多解读，其实就是给工作流起了个标识名字）
     */
    @WorkflowMethod(name = "workflow-type-name")
    RunResult execute(RunInput input);

    /**
     * 信号是 Workflow 外面的调用方按 Workflow ID 写给正在运行的这次工作流的消息。（备注：外部通知工作流的信号入口）
     * 本 Demo 的调用方是 demo-client：工作流停在 WAIT_TOOL 后，用它通知云电脑已经列完目录，载荷 kind=TOOL。
     * 被通知的是 joybot-workflow-v2 上的 RunWorkflowImpl；business-worker 和 agent-worker 不接收这条信号。
     * Activity 的结果走方法返回值，不走信号。信号没有返回值；还没执行到 await 时先留在历史里，回放时再交给 notifyChange。
     */
    @SignalMethod(name = "signal-name")
    void notifyChange(ToolSignal signal);

    /** 当
     * @QueryMethod 把 phase() 标成一次同步读取。demo-client 用它查看正在运行的工作流现在停在哪一步，这次调用不写入历史，也不推动工作流往下执行。
     * （备注：所以适合用来轮询状态等这种不干扰工作流的操作）
     */
    @QueryMethod
    String phase();
}
