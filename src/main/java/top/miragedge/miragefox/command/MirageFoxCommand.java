package top.miragedge.miragefox.command;

import top.miragedge.miragefox.MirageFox;
import top.miragedge.miragefox.api.MirageFoxAPI;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * /fox reload|toggle|reset|status|admin
 * /fox admin pluginchat <prompt...> <player> [public|private|nearby] [daily:<key>]
 * /fox admin broadcast <prompt...> [daily:<key>]
 * /fox admin dailyreset <key>
 *
 * 实现要点：
 * - 审计日志：记录谁在何时执行了什么 admin 操作；
 * - pluginchat/broadcast 显式检查 miragefox.pluginchat 权限；
 * - 异步回调统一回主线程再 sendMessage；
 * - prompt 长度上限 500。
 */
public class MirageFoxCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUB_COMMANDS = Arrays.asList("reload", "toggle", "reset", "status", "admin");
    private static final List<String> ADMIN_SUB = Arrays.asList("pluginchat", "broadcast", "dailyreset");
    private static final List<String> VISIBILITY_OPTIONS = Arrays.asList("public", "private", "nearby");
    private static final String DAILY_PREFIX = "daily:";
    private static final String DAILY_RESET_SUB = "dailyreset";
    private static final String PLUGIN_VERSION = "1.0.0";
    private static final int MAX_PROMPT_LENGTH = 500;

    private final MirageFox plugin;

    public MirageFoxCommand(MirageFox plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("miragefox.admin")) {
            sender.sendMessage("§c你没有权限使用这个命令喵。");
            return true;
        }
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "reload":
                audit(sender, "reload", "");
                plugin.reloadFoxConfig();
                sender.sendMessage("§a配置已重新加载喵～");
                break;
            case "toggle":
                audit(sender, "toggle", "");
                sender.sendMessage(plugin.toggleFox() ? "§a星玖已唤醒喵～" : "§c星玖去睡觉了喵～");
                break;
            case "reset":
                audit(sender, "reset", "");
                plugin.resetFoxChat();
                sender.sendMessage("§a星玖的记忆已清空喵～");
                break;
            case "status":
                showStatus(sender);
                break;
            case "admin":
                handleAdmin(sender, Arrays.copyOfRange(args, 1, args.length));
                break;
            default:
                sendUsage(sender);
                break;
        }
        return true;
    }

    private void audit(CommandSender sender, String action, String detail) {
        String who = (sender instanceof Player) ? ((Player) sender).getName() : "CONSOLE";
        plugin.getLogger().info("【审计】" + who + " 执行 /fox " + action
                + (detail.isEmpty() ? "" : " -> " + detail));
    }

    private void handleAdmin(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§e/fox admin pluginchat <prompt> <player> [public|private|nearby]");
            sender.sendMessage("§e/fox admin broadcast <prompt>");
            return;
        }
        switch (args[0].toLowerCase()) {
            case "pluginchat":
                handlePluginChat(sender, Arrays.copyOfRange(args, 1, args.length));
                break;
            case "broadcast":
                handleBroadcast(sender, Arrays.copyOfRange(args, 1, args.length));
                break;
            case DAILY_RESET_SUB:
                handleDailyReset(sender, Arrays.copyOfRange(args, 1, args.length));
                break;
            default:
                sender.sendMessage("§c未知子命令。可用: pluginchat, broadcast, dailyreset");
                break;
        }
    }

    private void handlePluginChat(CommandSender sender, String[] args) {
        if (!sender.hasPermission("miragefox.pluginchat")) {
            sender.sendMessage("§c你没有使用 pluginchat 的权限。");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("§c用法: /fox admin pluginchat <prompt> <player> [public|private|nearby]");
            return;
        }

        String visibility = "private";
        String dailyKey = null;
        String playerArg;
        int promptEnd = args.length;

        // 解析尾部可选参数：先看 daily:<key>，再看可见性
        int tail = args.length;
        String lastArg = args[tail - 1].toLowerCase();
        if (lastArg.startsWith(DAILY_PREFIX)) {
            dailyKey = lastArg.substring(DAILY_PREFIX.length()).trim();
            if (dailyKey.isEmpty()) {
                sender.sendMessage("§c用法: daily:<key> 的 key 不能为空，例如 daily:morning_greeting");
                return;
            }
            tail--;
            lastArg = (tail >= 1) ? args[tail - 1].toLowerCase() : "";
        }
        if (tail >= 1 && VISIBILITY_OPTIONS.contains(lastArg)) {
            visibility = lastArg;
            tail--;
        }
        if (tail < 1) {
            sender.sendMessage("§c用法: /fox admin pluginchat <prompt> <player> [public|private|nearby] [daily:<key>]");
            return;
        }
        playerArg = args[tail - 1];
        promptEnd = tail - 1;

        StringBuilder promptBuilder = new StringBuilder();
        for (int i = 0; i < promptEnd; i++) {
            if (i > 0) promptBuilder.append(" ");
            promptBuilder.append(args[i]);
        }
        String prompt = promptBuilder.toString();
        if (prompt.length() > MAX_PROMPT_LENGTH) {
            prompt = prompt.substring(0, MAX_PROMPT_LENGTH);
        }

        if (prompt.isEmpty()) {
            sender.sendMessage("§c提示词不能为空。");
            return;
        }

        audit(sender, "admin pluginchat", "target=" + playerArg + " vis=" + visibility
                + (dailyKey != null ? " daily=" + dailyKey : "") + " prompt=" + prompt);

        List<Player> targets = resolveTargets(sender, playerArg);
        if (targets.isEmpty()) {
            sender.sendMessage("§c找不到目标玩家: " + playerArg);
            return;
        }

        MirageFoxAPI.Visibility vis;
        try {
            vis = MirageFoxAPI.Visibility.valueOf(visibility.toUpperCase());
        } catch (IllegalArgumentException e) {
            vis = MirageFoxAPI.Visibility.PRIVATE;
        }

        final String visLabel = visibility;
        final MirageFoxAPI.Visibility finalVis = vis;
        final String finalDailyKey = dailyKey;
        MirageFoxAPI api = MirageFoxAPI.getInstance();
        if (api == null) {
            sender.sendMessage("§cMirageFoxAPI 未初始化。");
            return;
        }

        for (Player target : targets) {
            api.sendPluginMessageDaily(target, prompt, finalVis, finalDailyKey).whenComplete((response, throwable) -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (throwable != null) {
                        sender.sendMessage("§c向 " + target.getName() + " 发送AI消息失败。");
                        return;
                    }
                    if (response != null && !response.isEmpty()) {
                        sender.sendMessage("§a已向 " + target.getName() + " 发送AI消息（" + visLabel + "）");
                    } else {
                        sender.sendMessage("§c向 " + target.getName() + " 发送AI消息失败。");
                    }
                });
            });
        }
    }

    private void handleBroadcast(CommandSender sender, String[] args) {
        if (!sender.hasPermission("miragefox.pluginchat")) {
            sender.sendMessage("§c你没有使用 broadcast 的权限。");
            return;
        }
        String dailyKey = null;
        int promptEnd = args.length;
        String lastArg = args[args.length - 1].toLowerCase();
        if (lastArg.startsWith(DAILY_PREFIX)) {
            dailyKey = lastArg.substring(DAILY_PREFIX.length()).trim();
            if (dailyKey.isEmpty()) {
                sender.sendMessage("§c用法: daily:<key> 的 key 不能为空，例如 daily:morning_greeting");
                return;
            }
            promptEnd = args.length - 1;
        }

        StringBuilder promptBuilder = new StringBuilder();
        for (int i = 0; i < promptEnd; i++) {
            if (i > 0) promptBuilder.append(" ");
            promptBuilder.append(args[i]);
        }
        String prompt = promptBuilder.toString();
        if (prompt.length() > MAX_PROMPT_LENGTH) {
            prompt = prompt.substring(0, MAX_PROMPT_LENGTH);
        }
        if (prompt.isEmpty()) {
            sender.sendMessage("§c提示词不能为空。");
            return;
        }

        final String finalDailyKey = dailyKey;
        audit(sender, "admin broadcast", "prompt=" + prompt
                + (dailyKey != null ? " daily=" + dailyKey : ""));

        MirageFoxAPI api = MirageFoxAPI.getInstance();
        if (api == null) {
            sender.sendMessage("§cMirageFoxAPI 未初始化。");
            return;
        }
        api.broadcastMessageDaily(prompt, dailyKey).whenComplete((response, throwable) -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (throwable != null) {
                    sender.sendMessage("§c广播AI消息失败。");
                    return;
                }
                if (response != null && !response.isEmpty()) {
                    sender.sendMessage("§a已广播AI消息。");
                } else {
                    sender.sendMessage("§c广播AI消息失败。");
                }
            });
        });
    }

    private void handleDailyReset(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("§c用法: /fox admin dailyreset <key>");
            return;
        }
        String key = args[0];
        MirageFoxAPI api = MirageFoxAPI.getInstance();
        if (api == null) {
            sender.sendMessage("§cMirageFoxAPI 未初始化。");
            return;
        }
        api.resetDailyKey(key);
        audit(sender, "admin dailyreset", "key=" + key);
        sender.sendMessage("§a已重置每日限次 key: " + key + "（当天可再次执行）");
    }

    private List<Player> resolveTargets(CommandSender sender, String target) {
        if (target.startsWith("@")) {
            try {
                if (target.equalsIgnoreCase("@a")) {
                    return new ArrayList<>(Bukkit.getOnlinePlayers());
                } else if (target.equalsIgnoreCase("@p") || target.equalsIgnoreCase("@r")) {
                    if (sender instanceof Player) {
                        Player p = (Player) sender;
                        Player nearest = null;
                        double minDist = Double.MAX_VALUE;
                        for (Player online : Bukkit.getOnlinePlayers()) {
                            double dist = online.getLocation().distanceSquared(p.getLocation());
                            if (dist < minDist) {
                                minDist = dist;
                                nearest = online;
                            }
                        }
                        if (nearest != null) return List.of(nearest);
                    }
                    if (!Bukkit.getOnlinePlayers().isEmpty()) {
                        return List.of(Bukkit.getOnlinePlayers().iterator().next());
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("选择器解析失败: " + e.getMessage());
            }
            return new ArrayList<>();
        } else {
            Player p = Bukkit.getPlayerExact(target);
            return (p != null) ? List.of(p) : new ArrayList<>();
        }
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage("§6=== §d星玖 §6管理命令 ===");
        sender.sendMessage("§e/fox reload §f- 重新加载配置");
        sender.sendMessage("§e/fox toggle §f- 启用/暂停星玖");
        sender.sendMessage("§e/fox reset §f- 重置对话记忆");
        sender.sendMessage("§e/fox status §f- 查看状态");
        sender.sendMessage("§e/fox admin pluginchat <prompt> <player> [public|private|nearby] [daily:<key>]");
        sender.sendMessage("§7  §f- 给指定玩家发送AI消息（prompt支持PAPI变量）");
        sender.sendMessage("§e/fox admin broadcast <prompt> [daily:<key>]");
        sender.sendMessage("§7  §f- 广播AI消息给全服");
        sender.sendMessage("§e/fox admin dailyreset <key>");
        sender.sendMessage("§7  §f- 重置某个每日限次 key（当天可再次执行）");
    }

    private void showStatus(CommandSender sender) {
        String apiKey = plugin.getConfigManager().getApiKey();
        String apiKeyStatus = (apiKey != null && !apiKey.isEmpty()) ? "§a已配置" : "§c未配置";
        String cloudStatus = (plugin.getCloudClient() != null && plugin.getCloudClient().isAvailable())
                ? "§a已连接" : "§c未连接/未启用";

        sender.sendMessage("§6=== §d星玖 §6状态 ===");
        sender.sendMessage("§e版本: §f" + PLUGIN_VERSION);
        sender.sendMessage("§e启用: §f" + (plugin.isFoxEnabled() ? "§a是" : "§c否"));
        sender.sendMessage("§e触发模式: §f" + plugin.getConfigManager().getMode());
        sender.sendMessage("§e可见性: §f" + plugin.getConfigManager().getVisibilityMode());
        sender.sendMessage("§e私聊中: §f" + plugin.getPrivateChatManager().getActiveCount() + " 人");
        sender.sendMessage("§e云端: §f" + cloudStatus);
        sender.sendMessage("§e工具: §f" + (plugin.getConfigManager().isToolsEnabled() ? "§a已启用" : "§c已禁用") + " §7(" + plugin.getToolRegistry().size() + " 个)");
        sender.sendMessage("§e冷却: §f" + plugin.getConfigManager().getCooldownSeconds() + " 秒");
        sender.sendMessage("§e上下文: §f" + plugin.getConfigManager().getContextSize() + " 条");
        sender.sendMessage("§e模型: §f" + plugin.getConfigManager().getModel());
        sender.sendMessage("§eAPI Key: §f" + apiKeyStatus);
        MirageFoxAPI api = MirageFoxAPI.getInstance();
        sender.sendMessage("§ePAPI: §f" + (api != null && api.isPlaceholderAPIEnabled() ? "§a已接入" : "§c未安装"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return SUB_COMMANDS.stream().filter(s -> s.startsWith(partial)).collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            String partial = args[1].toLowerCase();
            return ADMIN_SUB.stream().filter(s -> s.startsWith(partial)).collect(Collectors.toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("dailyreset")) {
            return List.of("<key>");
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("pluginchat")) {
            String lastArg = args[args.length - 1].toLowerCase();
            return VISIBILITY_OPTIONS.stream().filter(s -> s.startsWith(lastArg)).collect(Collectors.toList());
        }
        return List.of();
    }
}