package top.miragedge.miragefox.config;

import top.miragedge.miragefox.MirageFox;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 配置管理 —— 加载、读取、重载 MirageFox 配置。
 *
 * 两个配置源：
 * - config.yml  行为配置（对话模式、可见性、工具开关、AI 端点）
 * - persona.yml 人设配置（角色名、服务器名、货币名、系统提示词）
 *
 * 所有默认值都保持通用，不指向任何私有服务或特定服务器。
 */
public class ConfigManager {

    private final MirageFox plugin;
    private volatile FileConfiguration config;
    private volatile FileConfiguration persona;

    private static final String VALID_COLOR = "[0-9a-fA-FkK-oOrR]";

    public ConfigManager(MirageFox plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
        loadPersona();
    }

    public void reload() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
        loadPersona();
    }

    // ---------- 人设 ----------

    /**
     * 加载 persona.yml。
     * 服务器上没有该文件时，从 jar 内默认模板复制一份（方便服主直接改）。
     */
    private void loadPersona() {
        File file = new File(plugin.getDataFolder(), "persona.yml");
        if (!file.exists()) {
            plugin.saveResource("persona.yml", false);
        }
        if (file.exists()) {
            this.persona = YamlConfiguration.loadConfiguration(file);
            // 合并 jar 内默认值，缺字段时兜底
            InputStream in = plugin.getResource("persona.yml");
            if (in != null) {
                this.persona.setDefaults(YamlConfiguration.loadConfiguration(
                        new InputStreamReader(in, StandardCharsets.UTF_8)));
            }
        } else {
            this.persona = new YamlConfiguration();
        }
    }

    private String personaString(String path, String def) {
        if (persona == null) return def;
        String v = persona.getString(path);
        return (v == null || v.isBlank()) ? def : v;
    }

    /** 角色名（用于唤醒词、显示名、系统提示词占位符） */
    public String getPersonaName() {
        return personaString("name", "Fox");
    }

    /** 角色显示名（聊天前缀，支持 § 颜色码） */
    public String getPersonaDisplayName() {
        String configured = personaString("display-name", "");
        if (!configured.isEmpty()) return configured;
        return "§d§l" + getPersonaName() + "§r";
    }

    /** 角色所在服务器名（系统提示词占位符 {server}） */
    public String getServerName() {
        return personaString("server-name", "this server");
    }

    /** 服务器货币名（系统提示词占位符 {currency}） */
    public String getCurrencyName() {
        return personaString("currency-name", "coins");
    }

    /** 系统提示词模板，支持 {name} / {server} / {currency} 占位符 */
    public String getSystemPromptTemplate() {
        return personaString("system-prompt", "");
    }

    // ---------- Cloud ----------
    public String getCloudBaseUrl() {
        return config.getString("cloud.api-base-url", "");
    }

    public String getCloudPluginToken() {
        String env = System.getenv("MIRAGEFOX_CLOUD_TOKEN");
        if (env != null && !env.isEmpty()) return env;
        return config.getString("cloud.plugin-token", "");
    }

    public boolean isCloudEnabled() {
        return config.getBoolean("cloud.enabled", false);
    }

    public int getCloudSyncInterval() {
        return Math.max(1, config.getInt("cloud.sync-interval-minutes", 5));
    }

    public int getCloudRetryMax() {
        return Math.max(1, config.getInt("cloud.retry-max", 8));
    }

    public long getCloudRetryBaseDelayMs() {
        return Math.max(100, config.getLong("cloud.retry-base-delay-ms", 500));
    }

    // ---------- AI ----------
    public String getBaseUrl() {
        return config.getString("ai.base-url", "");
    }

    public String getApiKey() {
        String env = System.getenv("MIRAGEFOX_API_KEY");
        if (env != null && !env.isEmpty()) return env;
        return config.getString("ai.api-key", "");
    }

    public String getModel() {
        return config.getString("ai.model", "gpt-4o-mini");
    }

    public double getTemperature() {
        double t = config.getDouble("ai.temperature", 0.9);
        return Math.max(0.0, Math.min(2.0, t));
    }

    public int getMaxTokens() {
        return Math.max(100, config.getInt("ai.max-tokens", 500));
    }

    public int getTimeoutSeconds() {
        return Math.max(5, config.getInt("ai.timeout-seconds", 30));
    }

    public int getAIRetryMax() {
        return Math.max(1, config.getInt("ai.retry-max", 8));
    }

    // ---------- 对话 ----------
    public int getContextSize() {
        return Math.max(2, config.getInt("chat.context-size", 12));
    }

    public String getMode() {
        String mode = config.getString("chat.mode", "mention_only");
        return mode != null && mode.equalsIgnoreCase("always") ? "always" : "mention_only";
    }

    public boolean isAlwaysMode() {
        return "always".equals(getMode());
    }

    /**
     * 唤醒词。config.yml 未配置时回退到 persona.yml 的角色名。
     */
    public List<String> getMentionPrefixes() {
        List<String> prefixes = config.getStringList("chat.mention-prefixes");
        if (prefixes == null || prefixes.isEmpty()) {
            return List.of(getPersonaName());
        }
        return prefixes;
    }

    // ---------- 消息显示 ----------
    /**
     * 聊天显示名。config.yml 显式配置优先，否则用 persona.yml 的 display-name。
     */
    public String getBotName() {
        String configured = config.getString("chat.display.bot-name", "");
        if (configured != null && !configured.isBlank()) return configured;
        return getPersonaDisplayName();
    }

    public String getTextColor() {
        return validColor(config.getString("chat.display.text-color"), "f");
    }

    public String getAccentColor() {
        return validColor(config.getString("chat.display.accent-color"), "d");
    }

    public String getErrorColor() {
        return validColor(config.getString("chat.display.error-color"), "c");
    }

    public String getSuccessColor() {
        return validColor(config.getString("chat.display.success-color"), "a");
    }

    /**
     * 统一的聊天标签前缀，如 {@code §d[Star] §f}。
     * 角色名取自 persona.yml，颜色取自 config.yml，服主改名后所有提示自动跟着变。
     */
    public String getChatTag() {
        return "§" + getAccentColor() + "[" + getPersonaName() + "] §" + getTextColor();
    }

    private String validColor(String value, String def) {
        if (value == null || value.isEmpty()) return def;
        String v = value.trim();
        if (v.length() == 1 && v.matches(VALID_COLOR)) return v;
        if (v.length() == 2 && v.startsWith("§") && v.substring(1).matches(VALID_COLOR)) {
            return v.substring(1);
        }
        return def;
    }

    // ---------- 消息可见性 ----------
    public String getVisibilityMode() {
        String mode = config.getString("chat.visibility", "broadcast");
        if (mode == null) return "broadcast";
        if (mode.equals("whisper") || mode.equals("nearby")) return mode;
        return "broadcast";
    }

    public int getNearbyRange() {
        return Math.max(16, Math.min(512, config.getInt("chat.nearby-range", 64)));
    }

    // ---------- 冷却 ----------
    public int getCooldownSeconds() {
        return Math.max(0, config.getInt("chat.cooldown-seconds", 3));
    }

    // ---------- 私聊 ----------
    public int getPrivateChatTimeoutMinutes() {
        return Math.max(0, config.getInt("chat.private-chat.timeout-minutes", 10));
    }

    // ---------- 工具 ----------
    public boolean isToolsEnabled() {
        return config.getBoolean("tools.enabled", true);
    }

    public boolean isLightningDamage() {
        return config.getBoolean("tools.lightning-damage", false);
    }

    public int getLaunchMaxPower() {
        return Math.max(1, Math.min(10, config.getInt("tools.launch-max-power", 5)));
    }
}
