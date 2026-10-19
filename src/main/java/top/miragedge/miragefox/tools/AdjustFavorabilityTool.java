package top.miragedge.miragefox.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import top.miragedge.miragefox.MirageFox;

import java.util.concurrent.TimeUnit;

/**
 * 好感度调整 —— 调整星玖对玩家的好感度。
 * 纯网络操作，isAsyncOnly 在工具线程直接执行。
 */
public class AdjustFavorabilityTool extends AbstractTool {

    public AdjustFavorabilityTool(MirageFox plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "adjust_favorability";
    }

    @Override
    protected String getDescription() {
        return "调整你对玩家的好感度。change 是变化值 +1~+5（好感）或 -1~-5（反感）。reason 是简短原因。";
    }

    @Override
    protected void buildParameters(JsonObject properties, JsonArray required) {
        addStringProp(properties, "player", "目标玩家名", true, required);
        addIntProp(properties, "change", "好感度变化值，正数增加好感，负数减少好感，范围 -5~+5", true, required);
        addStringProp(properties, "reason", "好感度变化的原因，简短描述", true, required);
    }

    @Override
    public int getPrice() { return 0; }

    @Override
    protected boolean isAsyncOnly() { return true; }

    @Override
    protected ToolResult executeImpl(JsonObject args, String caller) {
        String player = getString(args, "player", "");
        int change = getInt(args, "change", 0);
        String reason = getString(args, "reason", "");

        if (player.isEmpty()) {
            return ToolResult.fail("请告诉我目标玩家是谁喵。");
        }

        if (change < -5 || change > 5 || change == 0) {
            return ToolResult.fail("好感度变化值应在 -5 到 +5 之间，且不能为 0 喵。");
        }

        if (reason.isEmpty()) {
            return ToolResult.fail("请告诉我为什么要调整好感度喵。");
        }

        if (plugin.getCloudClient() == null) {
            return ToolResult.fail("云端服务未连接，无法调整好感度喵。");
        }

        try {
            String uuid = safeGetUUID(player);
            JsonObject result = plugin.getCloudClient()
                    .adjustFavorability(uuid, player, change, reason)
                    .get(10, TimeUnit.SECONDS);
            // 后端返回 {favorability, change, level}（无 ok 字段），有 favorability 即视为成功
            if (result != null && (result.has("ok") || result.has("favorability"))) {
                // 使上下文缓存失效，玩家下次查询读到最新好感度
                if (plugin.getChatManager() != null) {
                    plugin.getChatManager().invalidatePlayerContext(player);
                }
                return ToolResult.ok("好感度已调整喵。");
            }
            return ToolResult.fail("调整好感度时出了点问题喵。");
        } catch (Exception e) {
            return ToolResult.fail("调整好感度时出了点小问题喵，请稍后再试。");
        }
    }
}