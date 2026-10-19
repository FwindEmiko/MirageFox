package top.miragedge.miragefox.listener;

import top.miragedge.miragefox.MirageFox;
import top.miragedge.miragefox.ai.ChatManager;
import top.miragedge.miragefox.ai.ConversationManager;
import top.miragedge.miragefox.ai.PrivateChatManager;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 聊天监听器 —— 处理公屏聊天、私聊状态、连续对话、主动对话、玩家身份缓存记录。
 *
 * v3 交互设计（站在玩家视角）：
 * - 提到星玖 → 进入 90 秒连续对话窗口，窗口内免关键词续聊；
 * - 说「闭嘴/别说了/不聊了/再见」等 → 被动退出连续对话；
 * - 窗口超时 / 玩家离线 → 自动退出；
 * - 每满 20 条全服聊天，3% 概率星玖主动插话，回答玩家之间的疑问。
 */
public class ChatListener implements Listener {

    private final MirageFox plugin;
    private final PlainTextComponentSerializer textSerializer = PlainTextComponentSerializer.plainText();
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public ChatListener(MirageFox plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAsyncChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String message = textSerializer.serialize(event.message());
        if (message == null || message.isEmpty()) return;

        PrivateChatManager pcm = plugin.getPrivateChatManager();
        ConversationManager cm = plugin.getConversationManager();

        // 1. 玩家已在私聊状态——优先处理，不受 foxEnabled 影响（防止私聊泄露到公屏）
        if (pcm.isInPrivateChat(player.getUniqueId())) {
            if (pcm.isExitRequest(message)) {
                event.setCancelled(true);
                pcm.exitPrivateChat(player);
                return;
            }
            event.setCancelled(true);
            String textColor = plugin.getConfigManager().getTextColor();
            player.sendMessage("§" + textColor + "你: " + message);
            if (!plugin.isFoxEnabled()) {
                player.sendMessage("§" + plugin.getConfigManager().getErrorColor()
                        + "星玖正在休息，私聊暂不可用喵。");
                return;
            }
            if (!checkCooldown(player)) return;
            processPrivateChat(player, message);
            return;
        }

        // 2. 非私聊状态——检查是否是私聊进入请求
        if (plugin.isFoxEnabled() && isMentioningFox(message) && pcm.isEnterRequest(message)) {
            event.setCancelled(true);
            pcm.enterPrivateChat(player);
            return;
        }

        // 3. 连续对话：识别退出词 → 被动结束（消息照常公屏显示，不 cancel）
        if (cm != null && cm.isInConversation(player.getUniqueId()) && cm.isExitRequest(message)) {
            cm.endConversation(player.getUniqueId());
            return;
        }

        // 4. 星玖禁用时：仍统计主动对话计数，但不回复
        if (!plugin.isFoxEnabled()) {
            if (cm != null) cm.onPlayerChat(player.getUniqueId(), player.getName(), message);
            return;
        }

        // 5. 连续对话激活/免关键词：提到星玖 → 刷新窗口；窗口内 → 免关键词触发
        boolean inConversation = cm != null && cm.isInConversation(player.getUniqueId());
        boolean mentioned = isMentioningFox(message);

        if (mentioned) {
            if (cm != null) cm.refresh(player.getUniqueId());
            respondIfPossible(player, message);
            // 也参与主动对话统计
            if (cm != null) tryProactive(cm, player, message);
            return;
        }

        if (inConversation) {
            // 免关键词续聊，刷新窗口
            if (cm != null) cm.refresh(player.getUniqueId());
            respondIfPossible(player, message);
            return;
        }

        // 6. 主动对话统计（全服所有聊天）
        if (cm != null) tryProactive(cm, player, message);
    }

    /** 尝试触发主动对话（3% 概率插话回答疑问） */
    private void tryProactive(ConversationManager cm, Player player, String message) {
        String proactivePrompt = cm.onPlayerChat(player.getUniqueId(), player.getName(), message);
        if (proactivePrompt == null) return;
        plugin.getChatManager().processBroadcast(proactivePrompt).whenComplete((response, throwable) -> {
            try {
                if (!plugin.isEnabled()) return;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (throwable != null) {
                        plugin.getLogger().warning("主动对话出错: " + throwable.getMessage());
                        return;
                    }
                    if (response != null && !response.isEmpty()) {
                        plugin.getMessageService().sendFoxMessage(null, response);
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().warning("主动对话回调异常: " + e.getMessage());
            }
        });
    }

    /** 满足条件就触发星玖回应（含冷却） */
    private void respondIfPossible(Player player, String message) {
        if (!checkCooldown(player)) return;
        String cleanedMessage = ChatManager.sanitizeInput(message);
        if (cleanedMessage.isEmpty()) return;
        processFoxResponse(player, cleanedMessage);
    }

    /** 判断消息是否提到星玖 */
    private boolean isMentioningFox(String message) {
        List<String> prefixes = plugin.getConfigManager().getMentionPrefixes();
        for (String prefix : prefixes) {
            if (message.contains(prefix)) return true;
        }
        return false;
    }

    private boolean checkCooldown(Player player) {
        int cooldownSec = plugin.getConfigManager().getCooldownSeconds();
        if (cooldownSec <= 0) return true;

        long now = System.currentTimeMillis();
        Long last = cooldowns.get(player.getUniqueId());
        if (last != null && (now - last) < cooldownSec * 1000L) {
            player.sendMessage("§" + plugin.getConfigManager().getErrorColor()
                    + "星玖需要休息一下喵，等" + cooldownSec + "秒后再召唤吧。");
            return false;
        }
        cooldowns.put(player.getUniqueId(), now);
        return true;
    }

    /** 处理私聊消息 */
    private void processPrivateChat(Player player, String message) {
        String playerName = player.getName();

        plugin.getPrivateChatManager().processPrivateMessage(player, message).whenComplete((response, throwable) -> {
            try {
                if (!plugin.isEnabled()) return;
                Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                if (throwable != null) {
                    plugin.getLogger().severe("私聊回应出错: " + throwable.getMessage());
                    player.sendMessage("§" + plugin.getConfigManager().getErrorColor()
                            + "星玖的法力波动了喵，请稍后再试。");
                    return;
                }
                if (response != null && !response.isEmpty()) {
                    String botName = plugin.getConfigManager().getBotName();
                    String accent = plugin.getConfigManager().getAccentColor();
                    String textColor = plugin.getConfigManager().getTextColor();
                    player.sendMessage("§" + accent + "[星玖私聊] " + botName + " §" + textColor + response);
                }
            });
            } catch (Exception e) {
                plugin.getLogger().warning("私聊回调异常: " + e.getMessage());
            }
        });
    }

    /** 处理公屏星玖回应 */
    private void processFoxResponse(Player player, String message) {
        String playerName = player.getName();

        plugin.getChatManager().processMessage(playerName, message).whenComplete((response, throwable) -> {
            try {
                if (!plugin.isEnabled()) return;
                Bukkit.getScheduler().runTask(plugin, () -> {
                if (throwable != null) {
                    plugin.getLogger().severe("星玖回应出错: " + throwable.getMessage());
                    if (player.isOnline()) {
                        player.sendMessage("§" + plugin.getConfigManager().getErrorColor()
                                + "星玖的法力波动了喵，请稍后再试。");
                    }
                    return;
                }
                if (response != null && !response.isEmpty() && player.isOnline()) {
                    plugin.getMessageService().sendFoxMessage(playerName, response);
                }
            });
            } catch (Exception e) {
                plugin.getLogger().warning("星玖回应回调异常: " + e.getMessage());
            }
        });
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (plugin.getPlayerIdentityCache() != null) {
            plugin.getPlayerIdentityCache().record(player.getName(), player.getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        String name = event.getPlayer().getName();
        cooldowns.remove(uuid);
        // 退出时结束连续对话与私聊
        if (plugin.getConversationManager() != null) {
            plugin.getConversationManager().endConversation(uuid);
        }
        if (plugin.getPrivateChatManager().isInPrivateChat(uuid)) {
            plugin.getPrivateChatManager().forceExit(uuid);
        }
        // 清理 ChatManager 中该玩家的上下文/缓存/归档缓冲，防内存泄漏
        if (plugin.getChatManager() != null) {
            plugin.getChatManager().cleanupPlayer(name);
        }
    }
}