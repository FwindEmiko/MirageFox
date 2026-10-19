package top.miragedge.miragefox.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import top.miragedge.miragefox.MirageFox;

import java.util.concurrent.TimeUnit;

/**
 * 搜索历史对话 —— 搜索星玖和某玩家的历史对话记录。
 * 纯网络操作，isAsyncOnly 在工具线程直接执行。
 */
public class SearchConversationsTool extends AbstractTool {

    public SearchConversationsTool(MirageFox plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "search_conversations";
    }

    @Override
    protected String getDescription() {
        return "搜索你和某玩家之前的历史对话。query 是搜索关键词。";
    }

    @Override
    protected void buildParameters(JsonObject properties, JsonArray required) {
        addStringProp(properties, "player", "目标玩家名", true, required);
        addStringProp(properties, "query", "搜索关键词，描述要查找的对话内容", true, required);
    }

    @Override
    public int getPrice() { return 0; }

    @Override
    protected boolean isAsyncOnly() { return true; }

    @Override
    protected ToolResult executeImpl(JsonObject args, String caller) {
        String player = getString(args, "player", "");
        String query = getString(args, "query", "");

        if (player.isEmpty()) {
            return ToolResult.fail("请告诉我目标玩家是谁喵。");
        }

        if (query.isEmpty()) {
            return ToolResult.fail("搜索关键词不能为空喵。");
        }

        if (plugin.getCloudClient() == null) {
            return ToolResult.fail("云端服务未连接，无法搜索历史对话喵。");
        }

        try {
            String uuid = safeGetUUID(player);
            JsonArray results = plugin.getCloudClient()
                    .searchConversations(uuid, query)
                    .get(15, TimeUnit.SECONDS);
            if (results == null || results.size() == 0) {
                return ToolResult.fail("没有找到相关的历史对话喵。");
            }

            StringBuilder sb = new StringBuilder();
            sb.append("找到以下历史对话：\n");
            for (int i = 0; i < results.size(); i++) {
                JsonObject r = results.get(i).getAsJsonObject();
                if (sb.length() > 0) sb.append("\n---\n");
                String role = r.has("role") ? r.get("role").getAsString() : "unknown";
                String content = r.has("content") ? r.get("content").getAsString() : "";
                sb.append(role).append(": ").append(content);
            }
            return ToolResult.ok(sb.toString());
        } catch (Exception e) {
            return ToolResult.fail("搜索历史对话时出了点小问题喵。");
        }
    }
}
