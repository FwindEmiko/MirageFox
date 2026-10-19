package top.miragedge.miragefox.api;

import top.miragedge.miragefox.MirageFox;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.concurrent.CompletableFuture;

/**
 * MirageFox 公开 API —— 供其他插件调用星玖的能力。
 *
 * 用法：
 *   MirageFoxAPI api = MirageFoxAPI.getInstance();
 *   api.sendPluginMessage(player, "今天玩家附魔了{papi变量}次，请问候他", Visibility.PRIVATE);
 *
 * ⚠️ 线程契约：返回的 CompletableFuture 在 AI 线程/工具线程完成。
 * 调用方【禁止】在主线程对该 future 执行 .get()/.join() 阻塞等待，
 * 否则可能造成主线程冻结（回复链内含主线程工具执行）。
 * 请使用 thenAccept/whenComplete + Bukkit.getScheduler().runTask 回主线程。
 *
 * prompt 支持 PlaceholderAPI 变量（如 %player_name%），会自动解析。
 */
public class MirageFoxAPI {

    private static volatile MirageFoxAPI instance;
    private final MirageFox plugin;

    private MirageFoxAPI(MirageFox plugin) {
        this.plugin = plugin;
    }

    public static MirageFoxAPI getInstance() {
        return instance;
    }

    /** 插件启动时初始化（由 MirageFox 主类调用） */
    public static void initialize(MirageFox plugin) {
        instance = new MirageFoxAPI(plugin);
    }

    /** 消息可见性 */
    public enum Visibility {
        PRIVATE,
        PUBLIC,
        NEARBY
    }

    /**
     * 给指定玩家发送一条插件触发的 AI 消息。
     *
     * @param player     目标玩家
     * @param prompt     提示词（支持 PAPI 变量）
     * @param visibility 回复可见性
     * @return CompletableFuture，完成后返回星玖的回复文本；未初始化/未就绪时返回 null 值 future
     */
    public CompletableFuture<String> sendPluginMessage(Player player, String prompt, Visibility visibility) {
        if (player == null || !player.isOnline()) {
            return CompletableFuture.completedFuture(null);
        }
        if (plugin.getChatManager() == null) {
            plugin.getLogger().warning("MirageFoxAPI.sendPluginMessage 被调用但 ChatManager 未就绪");
            return CompletableFuture.completedFuture(null);
        }

        String playerName = player.getName();
        String resolvedPrompt = resolvePlaceholders(player, prompt);

        return plugin.getChatManager().processMessage("[系统触发] " + playerName, resolvedPrompt)
                .thenApply(response -> {
                    if (response != null && !response.isEmpty()) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            plugin.getMessageService().sendPluginMessage(playerName, response, visibility);
                        });
                    }
                    return response;
                })
                .exceptionally(e -> {
                    plugin.getLogger().warning("MirageFoxAPI 消息失败: " + e.getMessage());
                    return null;
                });
    }

    /**
     * 给指定玩家发送一条插件触发的 AI 消息（简写，私聊模式）。
     */
    public CompletableFuture<String> sendPrivateMessage(Player player, String prompt) {
        return sendPluginMessage(player, prompt, Visibility.PRIVATE);
    }

    /**
     * 广播一条 AI 消息给全服。
     *
     * @param prompt 提示词（不含 PAPI，或用控制台解析）
     */
    public CompletableFuture<String> broadcastMessage(String prompt) {
        if (plugin.getChatManager() == null) {
            plugin.getLogger().warning("MirageFoxAPI.broadcastMessage 被调用但 ChatManager 未就绪");
            return CompletableFuture.completedFuture(null);
        }
        return plugin.getChatManager().processMessage("[广播]", prompt)
                .thenApply(response -> {
                    if (response != null && !response.isEmpty()) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            plugin.getMessageService().sendPluginMessage(null, response, Visibility.PUBLIC);
                        });
                    }
                    return response;
                })
                .exceptionally(e -> {
                    plugin.getLogger().warning("MirageFoxAPI 广播失败: " + e.getMessage());
                    return null;
                });
    }

    /**
     * 给指定玩家发送一条插件触发的 AI 消息，并按 dailyKey 每日限一次。
     * 当天已经执行过该 key 时直接跳过（返回 null 值 future）。
     *
     * @param player     目标玩家
     * @param prompt     提示词（支持 PAPI 变量）
     * @param visibility 回复可见性
     * @param dailyKey   每日限次标识（如 "morning_greeting"）；null/空 表示不限制
     * @return 星玖的回复文本；今日已执行过则该 future 以 null 完成
     */
    public CompletableFuture<String> sendPluginMessageDaily(Player player, String prompt,
                                                            Visibility visibility, String dailyKey) {
        if (dailyKey == null || dailyKey.isEmpty()) {
            return sendPluginMessage(player, prompt, visibility);
        }
        if (plugin.getDailyLimiter() == null
                || !plugin.getDailyLimiter().tryRunToday(dailyKey)) {
            // 今天已经执行过，跳过
            return CompletableFuture.completedFuture(null);
        }
        return sendPluginMessage(player, prompt, visibility);
    }

    /**
     * 广播一条插件触发的 AI 消息，并按 dailyKey 每日限一次。
     * 当天已经执行过该 key 时直接跳过（返回 null 值 future）。
     *
     * @param prompt   提示词
     * @param dailyKey 每日限次标识（如 "morning_greeting"）；null/空 表示不限制
     * @return 星玖的回复文本；今日已执行过则该 future 以 null 完成
     */
    public CompletableFuture<String> broadcastMessageDaily(String prompt, String dailyKey) {
        if (dailyKey == null || dailyKey.isEmpty()) {
            return broadcastMessage(prompt);
        }
        if (plugin.getDailyLimiter() == null
                || !plugin.getDailyLimiter().tryRunToday(dailyKey)) {
            // 今天已经执行过，跳过
            return CompletableFuture.completedFuture(null);
        }
        return broadcastMessage(prompt);
    }

    /** 查询某个每日限次 key 今天是否已执行（供外部插件判断） */
    public boolean isDailyRunToday(String dailyKey) {
        return plugin.getDailyLimiter() != null && plugin.getDailyLimiter().isRunToday(dailyKey);
    }

    /** 手动重置某个每日限次 key（管理用途） */
    public void resetDailyKey(String dailyKey) {
        if (plugin.getDailyLimiter() != null) {
            plugin.getDailyLimiter().reset(dailyKey);
        }
    }

    /** 检查 PlaceholderAPI 是否可用。 */
    public boolean isPlaceholderAPIEnabled() {
        return plugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI");
    }

    /** 解析 PAPI 变量（需在主线程调用）。如果 PlaceholderAPI 未安装则原样返回。 */
    public String resolvePlaceholders(Player player, String text) {
        if (player == null || text == null) return text;
        if (!isPlaceholderAPIEnabled()) return text;
        try {
            return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
        } catch (NoClassDefFoundError e) {
            return text;
        } catch (Exception e) {
            plugin.getLogger().warning("PAPI 解析失败: " + e.getMessage());
            return text;
        }
    }
}