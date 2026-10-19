package top.miragedge.miragefox.tools;

import top.miragedge.miragefox.MirageFox;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * 工具执行辅助 —— 把 Bukkit API 操作切回主线程，并同步等待结果。
 * 工具 execute() 在工具线程池被调用，但 Bukkit API 必须在主线程执行。
 *
 * 改进（v2）：
 * 1. 泛型化，支持任意返回类型（String / ToolResult / 其他）。
 * 2. 超时后取消未执行的主线程任务，避免「提示失败后任务仍在队列稍后执行」导致重复施法/重复扣款。
 * 3. 调度本身 try/catch，插件禁用等场景下不抛未捕获异常。
 */
public final class ToolExecutor {

    private static final long MAIN_TIMEOUT_SECONDS = 5;

    private ToolExecutor() {
        throw new AssertionError("No instances");
    }

    /**
     * 在主线程执行任务并同步等待结果。
     *
     * @param plugin      插件实例
     * @param mainThread  在主线程执行的操作
     * @param fallback    超时/失败时的兜底结果
     * @return 执行结果
     */
    public static <T> T runOnMain(MirageFox plugin, Supplier<T> mainThread, T fallback) {
        if (Bukkit.isPrimaryThread()) {
            // 已经在主线程，直接执行（避免死锁）
            try {
                return mainThread.get();
            } catch (Exception e) {
                plugin.getLogger().warning("工具执行异常: " + e.getMessage());
                return fallback;
            }
        }

        CompletableFuture<T> future = new CompletableFuture<>();
        BukkitTask task;
        try {
            task = Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    future.complete(mainThread.get());
                } catch (Exception e) {
                    plugin.getLogger().warning("工具执行异常: " + e.getMessage());
                    future.complete(fallback);
                }
            });
        } catch (Exception e) {
            plugin.getLogger().warning("无法调度主线程任务（插件已禁用?）: " + e.getMessage());
            return fallback;
        }

        try {
            return future.get(MAIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            plugin.getLogger().warning("工具执行超时: " + e.getMessage());
            return fallback;
        } catch (Exception e) {
            plugin.getLogger().warning("工具执行失败: " + e.getMessage());
            return fallback;
        } finally {
            // 统一取消：若主线程任务尚未开始则不再执行，防止超时后效果延迟落地
            task.cancel();
        }
    }
}