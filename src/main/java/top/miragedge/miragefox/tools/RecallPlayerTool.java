package top.miragedge.miragefox.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import top.miragedge.miragefox.MirageFox;

/**
 * 回忆玩家印象 —— 读取星玖对某玩家的印象记忆。
 * 注意：印象已在上文中自动注入，此工具作为AI可调用的回退。
 */
public class RecallPlayerTool extends AbstractTool {

    public RecallPlayerTool(MirageFox plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "recall_player";
    }

    @Override
    protected String getDescription() {
        return "读取你对某玩家的所有印象记忆。player 是玩家名。";
    }

    @Override
    protected void buildParameters(JsonObject properties, JsonArray required) {
        addStringProp(properties, "player", "目标玩家名", true, required);
    }

    @Override
    public int getPrice() { return 0; }

    @Override
    protected ToolResult executeImpl(JsonObject args, String caller) {
        // 印象已在上文中自动注入，无需主动调用
        return ToolResult.ok("印象已在上文中");
    }
}
