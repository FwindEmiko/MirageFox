package top.miragedge.miragefox.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import top.miragedge.miragefox.tools.Tool;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具注册表 —— 管理所有可用的法术工具。
 * 提供：注册/反注册/查找/生成 LLM 工具定义 JSON。
 */
public class ToolRegistry {

    private final ConcurrentHashMap<String, Tool> tools = new ConcurrentHashMap<>();

    /** 注册一个工具 */
    public void register(Tool tool) {
        tools.put(tool.getName(), tool);
    }

    /** 按名字查找工具 */
    public Tool getTool(String name) {
        return tools.get(name);
    }

    /** 获取所有已注册的工具名 */
    public Set<String> getToolNames() {
        return tools.keySet();
    }

    /** 获取工具数量 */
    public int size() {
        return tools.size();
    }

    /**
     * 生成 LLM API 所需的 tools 参数（JSON Array）。
     * 每个工具包装为 {type: "function", function: {...}}
     */
    public JsonArray getToolsJson() {
        JsonArray array = new JsonArray();
        for (Tool tool : tools.values()) {
            JsonObject wrapper = new JsonObject();
            wrapper.addProperty("type", "function");
            wrapper.add("function", tool.getDefinition());
            array.add(wrapper);
        }
        return array;
    }
}