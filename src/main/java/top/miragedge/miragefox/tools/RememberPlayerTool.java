package top.miragedge.miragefox.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import top.miragedge.miragefox.MirageFox;

import java.util.concurrent.TimeUnit;

/**
 * 记住玩家印象 —— 星玖记住关于玩家的印象/记忆。
 * 纯网络操作，isAsyncOnly 在工具线程直接执行。
 */
public class RememberPlayerTool extends AbstractTool {

    public RememberPlayerTool(MirageFox plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "remember_player";
    }

    @Override
    protected String getDescription() {
        return "记住关于玩家的一条印象。content 是印象内容（短句），memory_type 是类型。";
    }

    @Override
    protected void buildParameters(JsonObject properties, JsonArray required) {
        addStringProp(properties, "player", "目标玩家名", true, required);
        addStringProp(properties, "content", "印象内容，简短描述对玩家的印象", true, required);
        addEnumProp(properties, "memory_type", "印象类型：preference(偏好)/event(事件)/personality(性格)/relationship(关系)/promise(约定)/inside_joke(内部梗)",
                false, required, new String[]{"preference", "event", "personality", "relationship", "promise", "inside_joke", "general"});
    }

    @Override
    public int getPrice() { return 0; }

    @Override
    protected boolean isAsyncOnly() { return true; }

    @Override
    protected ToolResult executeImpl(JsonObject args, String caller) {
        String player = getString(args, "player", "");
        String content = getString(args, "content", "");
        String type = getString(args, "memory_type", "general");

        if (player.isEmpty()) {
            return ToolResult.fail("请告诉我目标玩家是谁喵。");
        }

        if (content.isEmpty()) {
            return ToolResult.fail("印象内容不能为空喵。");
        }
        if (content.length() > 500) {
            return ToolResult.fail("印象内容太长了喵，请简短一些（不超过500字）。");
        }

        if (plugin.getCloudClient() == null) {
            return ToolResult.fail("云端服务未连接，无法记住印象喵。");
        }

        try {
            String uuid = safeGetUUID(player);
            JsonObject result = plugin.getCloudClient()
                    .rememberPlayer(uuid, content, type)
                    .get(10, TimeUnit.SECONDS);
            if (result != null && result.has("ok")) {
                if (plugin.getChatManager() != null) {
                    plugin.getChatManager().invalidatePlayerContext(player);
                }
                return ToolResult.ok("已记住关于 " + player + " 的印象喵。");
            }
            return ToolResult.fail("记住印象时出了点问题喵。");
        } catch (Exception e) {
            return ToolResult.fail("记住印象时出了点小问题喵，请稍后再试。");
        }
    }
}