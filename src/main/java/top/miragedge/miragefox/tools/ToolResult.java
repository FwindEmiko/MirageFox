package top.miragedge.miragefox.tools;

/**
 * 工具执行结果 —— 让工具能明确表达「成功/失败」，
 * 使 AbstractTool 可以在失败时统一退款、在成功时统一记录冷却/日限。
 */
public final class ToolResult {

    private final String message;
    private final boolean success;

    private ToolResult(String message, boolean success) {
        this.message = (message == null) ? "" : message;
        this.success = success;
    }

    /** 成功结果 */
    public static ToolResult ok(String message) {
        return new ToolResult(message, true);
    }

    /** 失败结果（AbstractTool 会据此退款/不记录使用） */
    public static ToolResult fail(String message) {
        return new ToolResult(message, false);
    }

    public boolean isSuccess() {
        return success;
    }

    public String message() {
        return message;
    }
}
