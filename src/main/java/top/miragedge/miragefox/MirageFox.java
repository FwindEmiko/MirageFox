package top.miragedge.miragefox;

import com.google.gson.JsonObject;
import top.miragedge.miragefox.ai.AIClient;
import top.miragedge.miragefox.ai.ChatManager;
import top.miragedge.miragefox.ai.ToolRegistry;
import top.miragedge.miragefox.ai.MessageService;
import top.miragedge.miragefox.ai.ConversationManager;
import top.miragedge.miragefox.ai.PrivateChatManager;
import top.miragedge.miragefox.api.MirageFoxAPI;
import top.miragedge.miragefox.cloud.CloudClient;
import top.miragedge.miragefox.config.ConfigManager;
import top.miragedge.miragefox.command.MirageFoxCommand;
import top.miragedge.miragefox.economy.EconomyManager;
import top.miragedge.miragefox.listener.ChatListener;
import top.miragedge.miragefox.tools.*;
import top.miragedge.miragefox.util.DailyLimiter;
import top.miragedge.miragefox.util.PlayerIdentityCache;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.CompletableFuture;

/**
 * MirageFox 锐界灵狐 —— AI agent 狐仙「星玖」Paper 插件。
 *
 * 生命周期：
 * - /fox reload 不再关闭再新建 AIClient + 等待+原子替换，不阻塞主线程；
 * - 定时任务句柄保存，reload/onDisable 正确取消；
 * - CloudClient 的 HttpClient 在 onDisable 时关闭；
 * - 玩家身份缓存（PlayerIdentityCache）持久化名字→UUID 映射；
 * - 宠物实体移除监听器在 onEnable 注册。
 */
public class MirageFox extends JavaPlugin {

    private ConfigManager configManager;
    private volatile AIClient aiClient;
    private ToolRegistry toolRegistry;
    private volatile ChatManager chatManager;
    private ChatListener chatListener;
    private MessageService messageService;
    private PrivateChatManager privateChatManager;
    private ConversationManager conversationManager;
    private volatile CloudClient cloudClient;
    private ToolUsageTracker toolUsageTracker;
    private EconomyManager economyManager;
    private PlayerIdentityCache playerIdentityCache;
    private DailyLimiter dailyLimiter;

    private volatile boolean foxEnabled = true;
    private BukkitTask cloudSyncTask;
    private BukkitTask privateChatTimeoutTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.configManager = new ConfigManager(this);
        this.playerIdentityCache = new PlayerIdentityCache(this);
        this.dailyLimiter = new DailyLimiter(this);

        initAIClient();
        this.messageService = new MessageService(this);
        MirageFoxAPI.initialize(this);

        initCloud();
        this.toolUsageTracker = new ToolUsageTracker();
        this.economyManager = new EconomyManager(this);

        initTools();
        this.chatManager = new ChatManager(this, aiClient, toolRegistry, configManager.getContextSize());
        this.privateChatManager = new PrivateChatManager(this, chatManager);
        this.conversationManager = new ConversationManager(this);

        if (cloudClient != null && cloudClient.isAvailable()) {
            cloudSync();
        }

        this.chatListener = new ChatListener(this);
        getServer().getPluginManager().registerEvents(chatListener, this);
        getServer().getPluginManager().registerEvents(new top.miragedge.miragefox.listener.PetListener(this), this);

        // 连续对话窗口清理（每 30 秒）
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            if (conversationManager != null) conversationManager.tick();
        }, 600L, 600L);

        // 私聊超时检查（每 60 秒）
        int timeoutMin = configManager.getPrivateChatTimeoutMinutes();
        if (timeoutMin > 0) {
            privateChatTimeoutTask = getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
                if (privateChatManager != null) {
                    privateChatManager.checkTimeouts(timeoutMin);
                }
            }, 1200L, 1200L);
        }

        MirageFoxCommand command = new MirageFoxCommand(this);
        getCommand("miragefox").setExecutor(command);
        getCommand("miragefox").setTabCompleter(command);

        getLogger().info("MirageFox 锐界灵狐已启动，星玖苏醒喵~");
        getLogger().info("已注册 " + toolRegistry.size() + " 个法术工具，模型: " + configManager.getModel());
        if (MirageFoxAPI.getInstance() != null && MirageFoxAPI.getInstance().isPlaceholderAPIEnabled()) {
            getLogger().info("PlaceholderAPI 已接入");
        }
    }

    private void initCloud() {
        if (configManager.isCloudEnabled()) {
            this.cloudClient = new CloudClient(this);
            getLogger().info("云端模式已启用，API: " + configManager.getCloudBaseUrl());
            int intervalMin = configManager.getCloudSyncInterval();
            if (intervalMin > 0) {
                cancelCloudSyncTask();
                cloudSyncTask = getServer().getScheduler().runTaskTimerAsynchronously(this, this::cloudSync,
                        intervalMin * 60L * 20L, intervalMin * 60L * 20L);
            }
        } else {
            getLogger().info("云端模式未启用，使用本地配置");
        }
    }

    private void cancelCloudSyncTask() {
        if (cloudSyncTask != null) {
            cloudSyncTask.cancel();
            cloudSyncTask = null;
        }
    }

    private void cloudSync() {
        if (cloudClient == null) return;

        CompletableFuture<Boolean> configFuture = cloudClient.fetchConfig().thenApply(cfg -> {
            if (cfg != null && cfg.has("system_prompt")) {
                String prompt = cfg.get("system_prompt").getAsString();
                if (chatManager != null && prompt != null && !prompt.isEmpty()) {
                    chatManager.updateSystemPrompt(prompt);
                }
                return true;
            }
            return false;
        }).exceptionally(e -> {
            getLogger().warning("云端同步-配置拉取失败: " + e.getMessage());
            return false;
        });

        // 填充心跳数据
        JsonObject stats = new JsonObject();
        stats.addProperty("online_players", Bukkit.getOnlinePlayers().size());
        stats.addProperty("private_chat_count",
                privateChatManager != null ? privateChatManager.getActiveCount() : 0);
        stats.addProperty("total_messages_today",
                chatManager != null ? chatManager.getMessagesToday() : 0);
        stats.addProperty("tool_calls_today",
                chatManager != null ? chatManager.getToolCallsToday() : 0);

        CompletableFuture<Boolean> hbFuture = cloudClient.heartbeat(stats)
                .thenApply(v -> true)
                .exceptionally(e -> {
                    getLogger().warning("云端同步-心跳上报失败: " + e.getMessage());
                    return false;
                });

        CompletableFuture.allOf(configFuture, hbFuture).thenRun(() -> {
            boolean allOk = configFuture.join() && hbFuture.join();
            if (!allOk) {
                getLogger().warning("云端同步部分失败，插件将使用本地缓存继续运行");
            }
        });
    }

    @Override
    public void onDisable() {
        if (chatManager != null) chatManager.shutdown();
        if (aiClient != null) aiClient.shutdown();
        cancelCloudSyncTask();
        if (privateChatTimeoutTask != null) {
            privateChatTimeoutTask.cancel();
            privateChatTimeoutTask = null;
        }
        if (cloudClient != null) cloudClient.close();
        if (playerIdentityCache != null) playerIdentityCache.save();
        if (dailyLimiter != null) dailyLimiter.save();
        getLogger().info("MirageFox 已关闭");
    }

    private void initAIClient() {
        String baseUrl = configManager.getBaseUrl();
        String apiKey = configManager.getApiKey();
        String model = configManager.getModel();
        double temperature = configManager.getTemperature();
        int maxTokens = configManager.getMaxTokens();
        int timeout = configManager.getTimeoutSeconds();
        int retryMax = configManager.getAIRetryMax();

        if (apiKey == null || apiKey.isEmpty()) {
            getLogger().warning("未配置 API Key");
        }
        this.aiClient = new AIClient(baseUrl, apiKey, model, temperature, maxTokens, timeout, retryMax);
    }

    private void initTools() {
        this.toolRegistry = new ToolRegistry();
        toolRegistry.register(new StrikeLightningTool(this));
        toolRegistry.register(new LaunchPlayerTool(this));
        toolRegistry.register(new GiveEffectTool(this));
        toolRegistry.register(new PlaySoundTool(this));
        toolRegistry.register(new SpawnParticlesTool(this));
        toolRegistry.register(new SendTitleTool(this));
        toolRegistry.register(new SetWeatherTool(this));
        toolRegistry.register(new SetTimeTool(this));
        toolRegistry.register(new ListPlayersTool(this));
        toolRegistry.register(new PlayerInfoTool(this));
        toolRegistry.register(new SpawnFireworkTool(this));
        toolRegistry.register(new HealPlayerTool(this));
        toolRegistry.register(new FeedPlayerTool(this));
        toolRegistry.register(new RandomTeleportTool(this));
        toolRegistry.register(new SummonMobTool(this));
        toolRegistry.register(new HighFiveTool(this));
        toolRegistry.register(new WhisperPlayerTool(this));
        toolRegistry.register(new SearchDocsTool(this));
        toolRegistry.register(new AdjustFavorabilityTool(this));
        toolRegistry.register(new RememberPlayerTool(this));
        toolRegistry.register(new SearchConversationsTool(this));
    }

    /**
     * 重新加载配置（/fox reload）。
     * v2：不再关闭再新建 AIClient—旧客户端异步优雅关闭，新客户端立即就位。
     */
    public void reloadFoxConfig() {
        configManager.reload();

        // 1. 替换 AI 客户端（旧客户端异步排空，不阻塞主线程）
        AIClient old = aiClient;
        initAIClient();
        if (chatManager != null) {
            chatManager.setAIClient(aiClient);
        }
        if (old != null) {
            // 旧客户端在 ForkJoinPool 后台线程优雅排空，不阻塞主线程
            CompletableFuture.runAsync(old::shutdown);
        }

        // 2. 更新上下文大小
        if (chatManager != null) {
            chatManager.setContextSize(configManager.getContextSize());
        }

        // 3. 清空私聊会话
        if (privateChatManager != null) privateChatManager.clearAll();

        // 4. 重建云端客户端并重置定时器（直接调度，避免 initCloud 重复创建实例）
        cancelCloudSyncTask();
        CloudClient oldCloud = this.cloudClient;
        if (configManager.isCloudEnabled()) {
            this.cloudClient = new CloudClient(this);
            if (oldCloud != null) {
                oldCloud.close(); // 释放旧 HttpClient 连接池与在途重试链
            }
            int intervalMin = configManager.getCloudSyncInterval();
            if (intervalMin > 0) {
                cloudSyncTask = getServer().getScheduler().runTaskTimerAsynchronously(this, this::cloudSync,
                        intervalMin * 60L * 20L, intervalMin * 60L * 20L);
            }
            if (cloudClient.isAvailable()) {
                cloudSync();
            }
        } else {
            this.cloudClient = null;
        }
    }

    public boolean toggleFox() { foxEnabled = !foxEnabled; return foxEnabled; }
    public void resetFoxChat() { if (chatManager != null) chatManager.resetContextAsync(); if (privateChatManager != null) privateChatManager.clearAll(); }

    public boolean isFoxEnabled() { return foxEnabled; }
    public ConfigManager getConfigManager() { return configManager; }
    public AIClient getAIClient() { return aiClient; }
    public ToolRegistry getToolRegistry() { return toolRegistry; }
    public ChatManager getChatManager() { return chatManager; }
    public MessageService getMessageService() { return messageService; }
    public PrivateChatManager getPrivateChatManager() { return privateChatManager; }
    public ConversationManager getConversationManager() { return conversationManager; }
    public CloudClient getCloudClient() { return cloudClient; }
    public ToolUsageTracker getToolUsageTracker() { return toolUsageTracker; }
    public EconomyManager getEconomyManager() { return economyManager; }
    public PlayerIdentityCache getPlayerIdentityCache() { return playerIdentityCache; }
    public DailyLimiter getDailyLimiter() { return dailyLimiter; }
}