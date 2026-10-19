package top.miragedge.miragefox.ai;

import top.miragedge.miragefox.MirageFox;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collection;

/**
 * 消息服务 —— 统一处理星玖的消息格式化、颜色、可见性范围。
 *
 * 可见性模式：
 *  - broadcast: 全服广播
 *  - whisper:   只发给触发玩家
 *  - nearby:    发给触发玩家附近一定范围内的玩家
 *
 * 颜色：bot名字固定配置色，正文用 text-color，前缀用 success-color。
 */
public class MessageService {

    private final MirageFox plugin;

    // 消息正文样式：灰色斜体，和普通玩家消息区分
    private static final String MSG_STYLE = "§7§o";

    public MessageService(MirageFox plugin) {
        this.plugin = plugin;
    }

    /**
     * 发送星玖的回复消息。
     *
     * @param caller 触发这次对话的玩家（可为 null 表示系统触发）
     * @param response 星玖的回复文本
     */
    public void sendFoxMessage(String caller, String response) {
        if (response == null || response.isEmpty()) return;

        String mode = plugin.getConfigManager().getVisibilityMode();
        switch (mode) {
            case "whisper":
                sendWhisper(caller, response);
                break;
            case "nearby":
                sendNearby(caller, response);
                break;
            default:
                sendBroadcast(response);
                break;
        }
    }

    /**
     * 插件触发的消息发送（支持指定可见性）。
     *
     * @param caller     触发玩家名（null=系统广播）
     * @param response   星玖的回复
     * @param visibility 可见性（PRIVATE/PUBLIC/NEARBY）
     */
    public void sendPluginMessage(String caller, String response, Object visibility) {
        if (response == null || response.isEmpty()) return;

        String visStr = (visibility != null) ? visibility.toString() : "PUBLIC";
        switch (visStr) {
            case "PRIVATE":
                sendWhisper(caller, response);
                break;
            case "NEARBY":
                sendNearby(caller, response);
                break;
            default:
                sendBroadcastWithCaller(caller, response);
                break;
        }
    }

    /** 公屏广播（带回复者名字） */
    private void sendBroadcastWithCaller(String caller, String response) {
        String botName = plugin.getConfigManager().getBotName();
        String textColor = plugin.getConfigManager().getTextColor();
        String accent = plugin.getConfigManager().getAccentColor();
        String prefix = (caller != null && !caller.isEmpty())
                ? " §" + accent + "@ " + caller + " §" + textColor + MSG_STYLE
                : " §" + textColor + MSG_STYLE;
        Bukkit.broadcastMessage(botName + prefix + response);
    }

    /** 全服广播 */
    private void sendBroadcast(String response) {
        Bukkit.broadcastMessage(formatMessage(response));
    }

    /** 私聊给触发玩家 */
    private void sendWhisper(String caller, String response) {
        if (caller == null) return;
        Player target = findPlayer(caller);
        if (target == null) return;
        target.sendMessage(formatWhisper(caller, response));
    }

    /** 发给触发玩家附近范围内的玩家 */
    private void sendNearby(String caller, String response) {
        if (caller == null) {
            sendBroadcast(response);
            return;
        }
        Player source = findPlayer(caller);
        if (source == null) {
            sendBroadcast(response);
            return;
        }
        int range = plugin.getConfigManager().getNearbyRange();
        String formatted = formatMessage(response);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getWorld().equals(source.getWorld())
                    && p.getLocation().distanceSquared(source.getLocation()) <= (double) range * range) {
                p.sendMessage(formatted);
            }
        }
    }

    /** 格式化公屏消息：bot名字 + 灰色斜体正文 */
    private String formatMessage(String response) {
        String botName = plugin.getConfigManager().getBotName();
        String textColor = plugin.getConfigManager().getTextColor();
        return botName + " §" + textColor + MSG_STYLE + response;
    }

    /** 格式化私聊消息：带 [私聊] 标记 + 灰色斜体正文 */
    private String formatWhisper(String caller, String response) {
        String botName = plugin.getConfigManager().getBotName();
        String accent = plugin.getConfigManager().getAccentColor();
        String textColor = plugin.getConfigManager().getTextColor();
        return "§" + accent + "[星玖私聊] " + botName + " §" + textColor + MSG_STYLE + response;
    }

    /** 按名字查找在线玩家 */
    private Player findPlayer(String name) {
        if (name == null) return null;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().equalsIgnoreCase(name)) {
                return p;
            }
        }
        return null;
    }
}