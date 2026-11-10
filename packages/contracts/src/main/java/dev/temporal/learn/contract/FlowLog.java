package dev.temporal.learn.contract;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * 三个进程共用的顺序日志。序号存在本机文件里，打印时持有文件锁，
 * 所以 workflow-worker、agent-worker、demo-client 的输出仍按发生顺序编号。
 * 这只服务于阅读，不是业务账。
 */
public final class FlowLog {
    /** 可用 -Dlearn.flow.seq=路径 换掉默认序号文件。 */
    private static final Path SEQ = Path.of(
            System.getProperty("learn.flow.seq", "/tmp/temporal-learn-flow.seq"));

    private FlowLog() {
    }

    /** demo-client 每次启动时把序号清零，避免接上一次调试的编号。 */
    public static void reset() {
        try (FileChannel channel = FileChannel.open(SEQ,
                StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            try (FileLock ignored = channel.lock()) {
                channel.truncate(0);
            }
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** actor 是链路角色，detail 另起一行。锁覆盖编号和打印，避免两行交叉。 */
    public static void log(String actor, String message, String detail) {
        try (FileChannel channel = FileChannel.open(SEQ,
                StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            try (FileLock ignored = channel.lock()) {
                int seq = readAndIncrement(channel);
                System.out.printf("[%02d] %-16s %s%n", seq, actor, message);
                if (detail != null && !detail.isBlank()) {
                    System.out.println("     " + detail);
                }
                System.out.flush();
            }
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** 调用方已持有文件锁。空文件从 1 开始。 */
    private static int readAndIncrement(FileChannel channel) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(32);
        int read = channel.read(buffer);
        int seq = 1;
        if (read > 0) {
            String current = new String(buffer.array(), 0, read, StandardCharsets.UTF_8).trim();
            if (!current.isEmpty()) {
                seq = Integer.parseInt(current) + 1;
            }
        }
        byte[] next = Integer.toString(seq).getBytes(StandardCharsets.UTF_8);
        channel.position(0);
        channel.write(ByteBuffer.wrap(next));
        channel.truncate(next.length);
        return seq;
    }
}
