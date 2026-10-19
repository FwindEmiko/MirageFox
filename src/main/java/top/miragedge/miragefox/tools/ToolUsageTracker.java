package top.miragedge.miragefox.tools;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具使用限制器 —— 防止玩家操控星玖滥用状态改变类工具。
 *
 * 每玩家每工具追踪：上次使用时间 + 今日使用次数。
 * 改进（v2）：
 * 1. 单条 UsageEntry 原子更新（compute），消除 lastUse/dailyCount 撕裂读。
 * 2. 日重置按「自然日」判断（LocalDate.toEpochDay），不再依赖 86400000ms 单调时钟，
 *    时钟回拨/NTP 校时不会导致日限长期失效。
 */
public class ToolUsageTracker {

    private final Map<String, UsageEntry> usage = new ConcurrentHashMap<>();

    private static final class UsageEntry {
        volatile long lastUse;
        volatile int count;
        volatile long day;
        UsageEntry(long day) {
            this.day = day;
        }
    }

    private static long today() {
        return LocalDate.now().toEpochDay();
    }

    /** 检查是否可以使用。返回 null 表示允许，否则返回限制原因 */
    public String checkUsage(String playerName, String toolName, int cooldownSeconds, int dailyLimit) {
        if (cooldownSeconds <= 0 && dailyLimit <= 0) return null;
        if (playerName == null || toolName == null) return null;

        String key = playerName.toLowerCase() + ":" + toolName;
        long now = System.currentTimeMillis();
        long day = today();
        UsageEntry e = usage.get(key);
        if (e == null) return null; // 首次使用，允许

        if (cooldownSeconds > 0 && e.lastUse > 0) {
            long elapsed = (now - e.lastUse) / 1000;
            if (elapsed < cooldownSeconds) {
                return "这个法术需要冷却 " + cooldownSeconds + " 秒，还剩 "
                        + (cooldownSeconds - elapsed) + " 秒喵～";
            }
        }

        if (dailyLimit > 0) {
            int count = (e.day == day) ? e.count : 0;
            if (count >= dailyLimit) {
                return "这个法术今天已经用完了喵，明天再来吧～";
            }
        }
        return null;
    }

    /** 记录一次成功使用 */
    public void recordUsage(String playerName, String toolName) {
        if (playerName == null || toolName == null) return;
        String key = playerName.toLowerCase() + ":" + toolName;
        long now = System.currentTimeMillis();
        long day = today();
        usage.compute(key, (k, e) -> {
            if (e == null) e = new UsageEntry(day);
            e.lastUse = now;
            e.count = (e.day == day) ? e.count + 1 : 1;
            e.day = day;
            return e;
        });
    }
}