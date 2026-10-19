package top.miragedge.miragefox.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import top.miragedge.miragefox.MirageFox;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * 抽象工具基类 —— JSON Schema 构建、玩家解析、目标授权、统一经济/冷却流程。
 *
 * 执行流程（v2，安全重构）：
 * 1. target 授权检查（非本人需 miragefox.admin）
 * 2. validate() 钩子：免费失败，不扣款（默认校验 target 是否在线）
 * 3. 冷却/日限检查（args 感知，支持动态定价/动态冷却）
 * 4. 扣款（仅付费工具，args 感知价格）
 * 5. 执行 executeImpl（网络工具 isAsyncOnly 直接在工具线程执行；其余切主线程）
 * 6. 失败 → 自动退款；成功 → 记录冷却/日限
 *
 * 网络工具（含 HTTP 请求）必须覆写 isAsyncOnly() 返回 true，绕过主线程调度。
 */
public abstract class AbstractTool implements Tool {

    protected final MirageFox plugin;

    protected AbstractTool(MirageFox plugin) {
        this.plugin = plugin;
    }

    protected abstract String getDescription();

    protected void buildParameters(JsonObject properties, JsonArray required) {
    }

    /** 工具执行（返回结构化结果，失败时 AbstractTool 自动退款） */
    protected abstract ToolResult executeImpl(JsonObject args, String caller);

    /** 网络工具覆写为 true：executeImpl 直接在工具线程执行，不切主线程 */
    protected boolean isAsyncOnly() {
        return false;
    }

    /**
     * 执行前校验钩子。返回 null 表示通过；返回非 null 表示校验失败（不扣款、不执行）。
     * 默认实现：校验 target 参数对应的玩家在线，避免「先扣款后才发现目标离线」。
     */
    protected String validate(JsonObject args, String caller) {
        if (args != null && args.has("target") && !args.get("target").isJsonNull()) {
            String target = getString(args, "target", "");
            if (!target.isEmpty() && findPlayer(target) == null) {
                return "找不到玩家 " + target + "，他可能不在线喵。";
            }
        }
        return null;
    }

    @Override
    public JsonObject getDefinition() {
        JsonObject function = new JsonObject();
        function.addProperty("name", getName());
        function.addProperty("description", getDescription());

        JsonObject parameters = new JsonObject();
        parameters.addProperty("type", "object");
        JsonObject properties = new JsonObject();
        JsonArray required = new JsonArray();
        buildParameters(properties, required);
        parameters.add("properties", properties);
        parameters.add("required", required);

        function.add("parameters", parameters);
        return function;
    }

    /**
     * 玩家标识参数名集合：所有可能指定"作用于谁"的参数。
     * 只要其中任意一个指向他人（且非 miragefox.admin），一律拒绝，
     * 防止 target/player 命名不一致导致授权绕过。
     */
    private static final String[] PLAYER_PARAM_KEYS = {"target", "player"};

    @Override
    public final String execute(JsonObject args, String caller) {
        // 1. 安全：检查所有玩家标识参数，只允许作用于自己（除非 caller 有 admin 权限）
        if (args != null) {
            for (String key : PLAYER_PARAM_KEYS) {
                if (!args.has(key) || args.get(key).isJsonNull()) continue;
                String target = args.get(key).getAsString();
                if (target == null || target.isEmpty() || target.equalsIgnoreCase(caller)) continue;
                String permResult = ToolExecutor.runOnMain(plugin, () -> {
                    Player p = Bukkit.getPlayerExact(caller);
                    return (p != null && p.hasPermission("miragefox.admin")) ? "true" : "false";
                }, "false");
                if (!"true".equals(permResult)) {
                    return plugin.getConfigManager().getPersonaName() + " 只能对你自己施展这个法术，不能对别人用。";
                }
                break; // 已确认是本人或 admin，无需继续检查其余参数
            }
        }

        // 2. 工具级校验（失败即返回，不扣款）
        String validationError = validate(args, caller);
        if (validationError != null) {
            return validationError;
        }

        // 3. 冷却/日限检查
        int cooldown = getRateLimitSeconds(args);
        int dailyLimit = getDailyLimit(args);
        if ((cooldown > 0 || dailyLimit > 0) && caller != null
                && plugin.getToolUsageTracker() != null) {
            String limitReason = plugin.getToolUsageTracker()
                    .checkUsage(caller, getName(), cooldown, dailyLimit);
            if (limitReason != null) {
                return "法术被限制了：" + limitReason;
            }
        }

        // 4. 扣款（付费工具；价格可依参数动态计算）
        int price = getPrice(args);
        boolean charged = false;
        if (price > 0 && caller != null && plugin.getEconomyManager() != null
                && plugin.getEconomyManager().isEnabled()) {
            Player p = Bukkit.getPlayerExact(caller);
            if (p != null) {
                String chargeResult = ToolExecutor.runOnMain(plugin, () -> {
                    if (!plugin.getEconomyManager().hasBalance(p, price)) {
                        return "INSUFFICIENT";
                    }
                    return plugin.getEconomyManager().withdraw(p, price) ? "OK" : "FAIL";
                }, "FAIL");
                if ("INSUFFICIENT".equals(chargeResult)) {
                    return "你的" + plugin.getConfigManager().getCurrencyName() + "不够施展这个法术，需要 " + price + " " + plugin.getConfigManager().getCurrencyName() + "。";
                }
                if (!"OK".equals(chargeResult)) {
                    return plugin.getConfigManager().getCurrencyName() + "扣款失败，法术没有生效。";
                }
                charged = true;
            }
        }

        // 5. 执行
        ToolResult result;
        if (isAsyncOnly()) {
            // 网络工具：直接在工具线程执行（不阻塞主线程）
            try {
                result = executeImpl(args, caller);
            } catch (Exception e) {
                plugin.getLogger().warning("工具 " + getName() + " 执行异常: " + e.getMessage());
                result = ToolResult.fail("法术施放出了点小问题喵，请稍后再试。");
            }
        } else {
            result = ToolExecutor.runOnMain(plugin, () -> executeImpl(args, caller),
                    ToolResult.fail("法术施放被打断了，请稍后再试。"));
        }

        // 6. 失败退款；成功记录使用
        if (!result.isSuccess()) {
            if (charged) {
                refund(caller, price);
            }
            return result.message();
        }
        if ((cooldown > 0 || dailyLimit > 0) && caller != null
                && plugin.getToolUsageTracker() != null) {
            plugin.getToolUsageTracker().recordUsage(caller, getName());
        }
        return result.message();
    }

    /** 扣款成功后执行失败时退款 */
    private void refund(String caller, int price) {
        if (price <= 0 || caller == null || plugin.getEconomyManager() == null) return;
        Player p = Bukkit.getPlayerExact(caller);
        if (p == null) return;
        ToolExecutor.runOnMain(plugin, () -> {
            boolean ok = plugin.getEconomyManager().deposit(p, price);
            if (!ok) {
                plugin.getLogger().warning("退款失败: " + caller + " " + price + " " + plugin.getConfigManager().getCurrencyName() + "（工具 " + getName() + "）");
            }
            return null;
        }, null);
    }

    // ---------- 价格 / 冷却 / 日限（支持按参数动态取值） ----------

    /** 工具的价格（服务器货币单位，取自 Vault / persona.yml），0=免费 */
    public int getPrice() { return 0; }

    /** 工具价格（按调用参数计算，如 GiveEffectTool 分级定价） */
    public int getPrice(JsonObject args) { return getPrice(); }

    /** 工具的冷却秒数（0=无冷却） */
    public int getRateLimitSeconds() { return 0; }

    /** 工具冷却（按调用参数计算） */
    public int getRateLimitSeconds(JsonObject args) { return getRateLimitSeconds(); }

    /** 工具的每日使用上限（0=无限制） */
    public int getDailyLimit() { return 0; }

    /** 工具日限（按调用参数计算） */
    public int getDailyLimit(JsonObject args) { return getDailyLimit(); }

    // ---------- 参数辅助 ----------

    protected String getString(JsonObject args, String key, String def) {
        if (args != null && args.has(key) && !args.get(key).isJsonNull()) {
            return args.get(key).getAsString();
        }
        return def;
    }

    protected int getInt(JsonObject args, String key, int def) {
        if (args != null && args.has(key) && !args.get(key).isJsonNull()) {
            try {
                return args.get(key).getAsInt();
            } catch (Exception e) {
                return def;
            }
        }
        return def;
    }

    protected double getDouble(JsonObject args, String key, double def) {
        if (args != null && args.has(key) && !args.get(key).isJsonNull()) {
            try {
                return args.get(key).getAsDouble();
            } catch (Exception e) {
                return def;
            }
        }
        return def;
    }

    protected boolean getBoolean(JsonObject args, String key, boolean def) {
        if (args != null && args.has(key) && !args.get(key).isJsonNull()) {
            try {
                return args.get(key).getAsBoolean();
            } catch (Exception e) {
                return def;
            }
        }
        return def;
    }

    protected Player findPlayer(String name) {
        if (name == null) return null;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().equalsIgnoreCase(name)) {
                return p;
            }
        }
        try {
            return Bukkit.getPlayer(UUID.fromString(name));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /**
     * 安全获取玩家 UUID：优先在线查找，其次查本地身份缓存，
     * 最后才回退玩家名（避免 getOfflinePlayer 阻塞主线程，也避免云端档案碎片化）。
     */
    protected String safeGetUUID(String playerName) {
        if (playerName == null || playerName.isEmpty()) return null;
        Player online = findPlayer(playerName);
        if (online != null) {
            return online.getUniqueId().toString();
        }
        String cached = plugin.getPlayerIdentityCache().getUuid(playerName);
        if (cached != null) {
            return cached;
        }
        return playerName; // 最后兜底：后端按名字建临时档案
    }

    // ---------- JSON Schema 构建辅助 ----------

    protected void addStringProp(JsonObject properties, String name, String desc, boolean required, JsonArray requiredArr) {
        JsonObject prop = new JsonObject();
        prop.addProperty("type", "string");
        prop.addProperty("description", desc);
        properties.add(name, prop);
        if (required) requiredArr.add(name);
    }

    protected void addIntProp(JsonObject properties, String name, String desc, boolean required, JsonArray requiredArr) {
        JsonObject prop = new JsonObject();
        prop.addProperty("type", "integer");
        prop.addProperty("description", desc);
        properties.add(name, prop);
        if (required) requiredArr.add(name);
    }

    protected void addNumberProp(JsonObject properties, String name, String desc, boolean required, JsonArray requiredArr) {
        JsonObject prop = new JsonObject();
        prop.addProperty("type", "number");
        prop.addProperty("description", desc);
        properties.add(name, prop);
        if (required) requiredArr.add(name);
    }

    protected void addBooleanProp(JsonObject properties, String name, String desc, boolean required, JsonArray requiredArr) {
        JsonObject prop = new JsonObject();
        prop.addProperty("type", "boolean");
        prop.addProperty("description", desc);
        properties.add(name, prop);
        if (required) requiredArr.add(name);
    }

    protected void addEnumProp(JsonObject properties, String name, String desc, boolean required,
                               JsonArray requiredArr, String[] enumValues) {
        JsonObject prop = new JsonObject();
        prop.addProperty("type", "string");
        prop.addProperty("description", desc);
        JsonArray enumArr = new JsonArray();
        for (String v : enumValues) enumArr.add(v);
        prop.add("enum", enumArr);
        properties.add(name, prop);
        if (required) requiredArr.add(name);
    }
}