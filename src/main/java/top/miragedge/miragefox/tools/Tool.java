package top.miragedge.miragefox.tools;

import com.google.gson.JsonObject;

/**
 * 星玖的法术工具接口。
 * 每个工具定义一个 LLM 可调用的函数（JSON Schema），
 * 以及执行逻辑（直接操作 Bukkit API，不经过命令系统 → 天然防注入）。
 *
 * 注意：所有工具 execute 在异步线程被调用，但 Bukkit API 必须切回主线程，
 * 因此 execute 内部用 Bukkit.getScheduler().runTask() 包裹实际动作。
 */
public interface Tool {

    /** 工具名（函数名，LLM 用这个调用） */
    String getName();

    /** 返回给 LLM 的 JSON Schema 定义（含 description） */
    JsonObject getDefinition();

    /**
     * 执行工具（在异步线程调用）。
     *
     * @param args   LLM 填充的参数（已解析为 JsonObject）
     * @param caller 触发这次对话的玩家名（可为 null 表示系统）
     * @return 返回给 LLM 的结果描述（字符串）
     */
    String execute(JsonObject args, String caller);
}