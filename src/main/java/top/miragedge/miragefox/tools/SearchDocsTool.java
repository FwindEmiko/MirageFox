package top.miragedge.miragefox.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import top.miragedge.miragefox.MirageFox;

import java.util.concurrent.TimeUnit;

/**
 * 文档站搜索 —— 通过云端 API 搜索 MiragEdge 文档站获取游戏玩法信息。
 * 纯网络操作，isAsyncOnly 在工具线程直接执行，不阻塞主线程。
 */
public class SearchDocsTool extends AbstractTool {

    public SearchDocsTool(MirageFox plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "search_docs";
    }

    @Override
    protected String getDescription() {
        return "搜索 MiragEdge 文档站获取服务器玩法、攻略、系统介绍等信息。"
                + "query 是搜索关键词，越具体越好。"
                + "返回相关文档页面的内容摘要。";
    }

    @Override
    protected void buildParameters(JsonObject properties, JsonArray required) {
        addStringProp(properties, "query", "搜索关键词，用自然语言描述要找的内容", true, required);
        addIntProp(properties, "max_results", "最多返回几条结果，默认1，最大3", false, required);
    }

    @Override
    public int getPrice() { return 0; }

    @Override
    protected boolean isAsyncOnly() { return true; }

    @Override
    protected ToolResult executeImpl(JsonObject args, String caller) {
        String query = getString(args, "query", "");
        if (query.isEmpty()) {
            return ToolResult.fail("搜索关键词不能为空喵。");
        }
        int maxResults = Math.max(1, Math.min(3, getInt(args, "max_results", 1)));

        if (plugin.getCloudClient() == null) {
            return ToolResult.fail("云端服务未连接，无法搜索文档站喵。");
        }

        try {
            JsonArray results = plugin.getCloudClient().searchDocs(query, maxResults)
                    .get(15, TimeUnit.SECONDS);
            if (results == null || results.size() == 0) {
                return ToolResult.fail("在文档站上没有找到关于「" + query + "」的内容喵。");
            }

            StringBuilder sb = new StringBuilder();
            sb.append("文档站找到了以下内容：\n");
            for (int i = 0; i < results.size(); i++) {
                JsonObject r = results.get(i).getAsJsonObject();
                if (i > 0) sb.append("\n---\n");
                sb.append("【").append(r.get("title").getAsString()).append("】\n");
                sb.append(r.get("content").getAsString());
            }
            return ToolResult.ok(sb.toString());
        } catch (Exception e) {
            return ToolResult.fail("搜索文档站时出了点小问题喵。");
        }
    }
}
