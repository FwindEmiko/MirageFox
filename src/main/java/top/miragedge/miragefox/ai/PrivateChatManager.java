package top.miragedge.miragefox.ai;

import top.miragedge.miragefox.MirageFox;
import com.google.gson.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 私聊管理器 —— 管理玩家与星玖的一对一私聊会话。
 *
 * 每个玩家进入私聊后拥有独立的对话上下文，与公屏聊天完全隔离。
 * 私聊期间玩家不需要说「星玖」关键词，所有消息只发给星玖，其他玩家看不到。
 */
public class PrivateChatManager {

    private final MirageFox plugin;
    private final ChatManager chatManager;
    private final Gson gson = new GsonBuilder().create();

    // 每个玩家的私聊上下文
    private final ConcurrentHashMap<UUID, LinkedList<JsonObject>> privateContexts = new ConcurrentHashMap<>();

    // 每个玩家的最后活动时间（用于空闲超时检查）
    private final ConcurrentHashMap<UUID, Long> lastActivity = new ConcurrentHashMap<>();

    private static final String[] ENTER_KEYWORDS = {
            "和我私聊", "星玖私聊", "和星玖私聊", "私聊吧", "开始私聊", "悄悄话", "单独聊聊"
    };

    private static final String[] EXIT_KEYWORDS = {
            "退出私聊", "不想私聊了", "结束私聊", "停止私聊", "不私聊了"
    };

    public PrivateChatManager(MirageFox plugin, ChatManager chatManager) {
        this.plugin = plugin;
        this.chatManager = chatManager;
    }

    public boolean isInPrivateChat(UUID uuid) {
        return privateContexts.containsKey(uuid);
    }

    public boolean isEnterRequest(String message) {
        if (message == null) return false;
        for (String kw : ENTER_KEYWORDS) {
            if (message.contains(kw)) return true;
        }
        return false;
    }

    public boolean isExitRequest(String message) {
        if (message == null) return false;
        for (String kw : EXIT_KEYWORDS) {
            if (message.contains(kw)) return true;
        }
        return false;
    }

    public void enterPrivateChat(Player player) {
        UUID uuid = player.getUniqueId();
        if (isInPrivateChat(uuid)) {
            player.sendMessage(plugin.getConfigManager().getChatTag() + "我们已经在私聊了喵～");
            return;
        }
        privateContexts.put(uuid, new LinkedList<>());
        lastActivity.put(uuid, System.currentTimeMillis());
        player.sendMessage(plugin.getConfigManager().getChatTag() + "已进入私聊模式喵！现在你说的话只有我能看到。"
                + "不想私聊了就说「退出私聊」～");
    }

    public void exitPrivateChat(Player player) {
        UUID uuid = player.getUniqueId();
        if (!isInPrivateChat(uuid)) return;
        privateContexts.remove(uuid);
        lastActivity.remove(uuid);
        player.sendMessage(plugin.getConfigManager().getChatTag() + "已退出私聊模式喵～下次想私聊随时叫我。");
    }

    public void forceExit(UUID uuid) {
        privateContexts.remove(uuid);
        lastActivity.remove(uuid);
    }

    public CompletableFuture<String> processPrivateMessage(Player player, String message) {
        UUID uuid = player.getUniqueId();
        LinkedList<JsonObject> context = privateContexts.get(uuid);
        if (context == null) {
            return CompletableFuture.completedFuture(null);
        }

        lastActivity.put(uuid, System.currentTimeMillis());
        String playerName = player.getName();

        // 委托给 ChatManager 的统一流程（清洗、缓存、异步工具执行）
        return chatManager.processPrivateMessage(playerName, message, context);
    }

    /** 检查空闲超时，自动退出长时间不活跃的私聊 */
    public void checkTimeouts(int timeoutMinutes) {
        if (timeoutMinutes <= 0) return;
        long threshold = System.currentTimeMillis() - timeoutMinutes * 60_000L;
        for (Map.Entry<UUID, Long> entry : lastActivity.entrySet()) {
            if (entry.getValue() < threshold) {
                UUID uuid = entry.getKey();
                // CAS：仅当最后活动时间仍是本次读到的旧值时才移除，避免误删刚活跃的会话
                if (lastActivity.remove(uuid, entry.getValue())) {
                    privateContexts.remove(uuid);
                } else {
                    continue;
                }
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) {
                    p.sendMessage(plugin.getConfigManager().getChatTag() + "私聊已超时自动退出喵～下次想聊随时叫我。");
                }
            }
        }
    }

    public void clearAll() {
        privateContexts.clear();
        lastActivity.clear();
    }

    public int getActiveCount() {
        return privateContexts.size();
    }
}