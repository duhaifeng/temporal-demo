package dev.temporal.learn.agent;

import java.util.stream.Collectors;

import dev.temporal.learn.contract.FlowLog;
import dev.temporal.learn.contract.ModelActivities;
import dev.temporal.learn.contract.TurnRequest;
import dev.temporal.learn.contract.TurnResult;

/**
 * 单轮模型活动，注册在 joybot-model-v2。
 * JoyBot 这里是 Node agent-worker 调 Pi；本类按 stepSeq 返回固定提议或最终回答，不调用模型。
 */
public class ModelActivitiesImpl implements ModelActivities {
    /**
     * 由 RunWorkflowImpl 调度。stepSeq=1 只提议 list_dir；
     * stepSeq=2 使用请求里的 toolReceipt，返回 FINAL。
     */
    @Override
    public TurnResult modelTurn(TurnRequest request) {
        if (request.stepSeq() == 1) {
            FlowLog.log("MODEL",
                    "Activity modelTurn  队列 joybot-model-v2，模拟 Node agent-worker 的单轮 Pi",
                    "先 v2OpenTurn：按 execution+stepSeq 幂等领步骤，并为这次 Activity attempt 分配 fenceEpoch=1");
            FlowLog.log("MODEL",
                    "本轮没有工具回执。模型只提议一个动作，不执行 shell",
                    "提议 route=DEVICE tool=list_dir path=/home/user/Desktop");
            return new TurnResult(1, "TOOLS", "tool-100", "DEVICE", "list_dir",
                    "/home/user/Desktop", null, null);
        }
        String files = request.toolReceipt().files().stream().collect(Collectors.joining(","));
        FlowLog.log("MODEL",
                "第二轮 modelTurn：v2OpenTurn 带回上一轮原工具回执，先应用到会话再请求模型",
                "applyToolOperationIds=" + String.join(",", request.applyToolOperationIds()) + " files=" + files);
        FlowLog.log("MODEL",
                "模型给出最终回答。token 流写 thread_event，Temporal 只收提交引用",
                "outputRef=artifact://run/42/step/2/output  disposition=FINAL");
        return new TurnResult(2, "FINAL", null, null, null, null,
                "artifact://run/42/step/2/output",
                "桌面上有两份文件：值班表.xlsx、笔记.txt。");
    }
}
