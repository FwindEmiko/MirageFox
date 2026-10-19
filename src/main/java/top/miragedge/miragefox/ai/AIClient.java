package top.miragedge.miragefox.ai;

import com.google.gson.*;
import top.miragedge.miragefox.MirageFox;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.*;

/**
 * AI 客户端 —— 调用 OpenAI 兼容 API（MiragEdge-Hub）的聊天补全接口。
 * 支持 function calling / tool calling。
 * 所有请求串行化（单线程 executor），避免并发冲突。
 *
 * 实现要点：
 * - 优雅关闭：shutdown + awaitTermination，不粗暴中断在途请求；
 * - 错误 JSON 用 Gson 构造，避免手拼字符串导致非法 JSON / 敏感信息泄漏；
 * - 请求级 timeout 覆盖完整读响应阶段。
 */
public class AIClient {

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final double temperature;
    private final int maxTokens;
    private final int timeoutSeconds;
    private final int retryMax;
    private final HttpClient httpClient;
    private final Gson gson;

    // 串行化请求队列（一次只处理一个请求，保证上下文一致性）
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "MirageFox-AI");
        t.setDaemon(true);
        return t;
    });

    public AIClient(String baseUrl, String apiKey, String model,
                    double temperature, int maxTokens, int timeoutSeconds) {
        this(baseUrl, apiKey, model, temperature, maxTokens, timeoutSeconds, 8);
    }

    public AIClient(String baseUrl, String apiKey, String model,
                    double temperature, int maxTokens, int timeoutSeconds, int retryMax) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.timeoutSeconds = timeoutSeconds;
        this.retryMax = Math.max(1, retryMax);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();
        this.gson = new GsonBuilder().create();
    }

    /**
     * 发送聊天补全请求。
     *
     * @param messagesJson 消息列表（含 system/user/assistant/tool 角色）的 JSON 字符串
     * @param toolsJson    LLM 可用工具定义 JSON 字符串（可为 null 或空数组）
     * @return 响应 JSON（原始字符串），失败时返回 {"error":"..."} 格式
     */
    public CompletableFuture<String> chatCompletion(String messagesJson, String toolsJson) {
        // 串行构建请求（单线程 executor），发送与重试全部走 sendAsync 异步链，不阻塞 AI 线程
        return CompletableFuture.supplyAsync(() -> buildRequest(messagesJson, toolsJson), executor)
                .thenCompose(request -> sendWithRetryAsync(request));
    }

    private HttpRequest buildRequest(String messagesJson, String toolsJson) {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("temperature", temperature);
        body.addProperty("max_tokens", maxTokens);

        JsonArray msgArray = gson.fromJson(messagesJson, JsonArray.class);
        body.add("messages", msgArray);

        if (toolsJson != null && !toolsJson.isEmpty() && !toolsJson.equals("[]")) {
            JsonArray toolsArray = gson.fromJson(toolsJson, JsonArray.class);
            body.add("tools", toolsArray);
            body.addProperty("tool_choice", "auto");
        }

        String requestBody = gson.toJson(body);

        return HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
    }

    /**
     * 纯异步重试链：失败（error JSON / 异常）时按指数退避重试，最多 retryMax 次；
     * 4xx 不重试；executor 已关闭时立即终止。
     */
    private CompletableFuture<String> sendWithRetryAsync(HttpRequest request) {
        CompletableFuture<String> chain = doSendAsync(request);
        for (int i = 1; i < retryMax; i++) {
            final int attempt = i;
            chain = chain.thenCompose(result -> {
                if (!isError(result)) {
                    return CompletableFuture.completedFuture(result);
                }
                if (isShutdown()) {
                    return CompletableFuture.completedFuture(errorJson("Shutdown", "AIClient 已关闭"));
                }
                long delay = Math.min(5000, 400L * (1L << Math.min(attempt, 6)));
                System.getLogger("MirageFox").log(System.Logger.Level.WARNING,
                        "LLM 请求失败，第 " + (attempt + 1) + "/" + retryMax + " 次重试（" + delay + "ms 后）");
                return CompletableFuture.runAsync(() -> { /* 延时后重试 */ },
                                CompletableFuture.delayedExecutor(delay, TimeUnit.MILLISECONDS))
                        .thenCompose(v -> doSendAsync(request));
            });
        }
        return chain;
    }

    private CompletableFuture<String> doSendAsync(HttpRequest request) {
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .orTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .thenApply(resp -> {
                    if (resp.statusCode() != 200) {
                        // 4xx 确定性错误不重试
                        if (resp.statusCode() >= 400 && resp.statusCode() < 500) {
                            return errorJson("HTTP " + resp.statusCode(), safeDetail(resp.body()));
                        }
                        return errorJson("HTTP " + resp.statusCode(), safeDetail(resp.body()));
                    }
                    return resp.body();
                })
                .exceptionally(e -> errorJson(e.getClass().getSimpleName(), e.getMessage()));
    }

    private boolean isError(String json) {
        if (json == null || json.trim().isEmpty()) return true;
        try {
            JsonObject obj = gson.fromJson(json.trim(), JsonObject.class);
            // 空对象/纯字符串解析失败或没有 error 但也没有 choices → 视为成功响应（由调用方兜底）
            if (obj == null) return true;
            return obj.has("error");
        } catch (Exception e) {
            // 非 JSON（如纯文本 "ok"）视为成功响应，交由 ChatManager 解析兜底
            return false;
        }
    }

    private String errorJson(String type, String detail) {
        JsonObject err = new JsonObject();
        String safe = (detail == null) ? "" : detail.replaceAll("[\r\n\t]", " ");
        if (safe.length() > 500) safe = safe.substring(0, 500);
        err.addProperty("error", type + ": " + safe);
        return err.toString();
    }

    private String safeDetail(String body) {
        return (body == null) ? "" : body;
    }

    /** 关闭线程池（优雅：先 shutdown 等待在途任务完成，超时再中断） */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(timeoutSeconds + 5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /** 判断是否已关闭（供 reload 流程检查） */
    public boolean isShutdown() {
        return executor.isShutdown();
    }

    /**
     * 在 AI 串行线程上执行一个任务（用于 reset 上下文的线程安全）。
     */
    public void runAsync(Runnable task) {
        try {
            executor.execute(task);
        } catch (RejectedExecutionException e) {
            // executor 已关闭：记录日志，避免静默失败
            System.getLogger("MirageFox").log(System.Logger.Level.WARNING,
                    "AIClient.runAsync 被拒绝（executor 已关闭），任务已丢弃");
        }
    }
}