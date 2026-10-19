package top.miragedge.miragefox.cloud;

import com.google.gson.*;
import top.miragedge.miragefox.MirageFox;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 云端客户端 —— 通过 HTTP 从网站后端拉取配置/工具/知识库/人设，
 * 并把对话结果提交到云端持久化。
 *
 * 实现要点：
 * - 失败时记录 WARN 日志（不再完全静默），方便运维定位；
 * - 提供 close() 关闭 HttpClient 连接池（onDisable/reload 时调用）；
 * - fetchConfig 失败时与其他方法一致返回 null。
 */
public class CloudClient {

    private static final long TIMEOUT_SECONDS = 10;

    private final MirageFox plugin;
    private final String baseUrl;
    private final String token;
    private final boolean enabled;
    private final HttpClient httpClient;
    private final Gson gson = new GsonBuilder().create();
    private volatile boolean closed = false;

    public CloudClient(MirageFox plugin) {
        this.plugin = plugin;
        this.baseUrl = normalize(plugin.getConfigManager().getCloudBaseUrl());
        this.token = plugin.getConfigManager().getCloudPluginToken();
        this.enabled = plugin.getConfigManager().isCloudEnabled();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .build();
    }

    /** 关闭 HttpClient（插件关闭/reload 时释放连接池与线程）；关闭后重试链立即终止 */
    public void close() {
        closed = true;
        httpClient.close();
    }

    public boolean isClosed() {
        return closed;
    }

    /** 云端是否可用：开启 + 配了 baseUrl + 配了有效 token */
    public boolean isAvailable() {
        return enabled
                && baseUrl != null && !baseUrl.isEmpty()
                && token != null && !token.isEmpty()
                && !token.equals("CHANGE_ME_TO_REAL_TOKEN");
    }

    private String normalize(String url) {
        if (url == null) return null;
        String u = url.trim();
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u;
    }

    // ---------- GET ----------

    /** GET /miragefox/plugin/config -> JsonObject（失败返回 null） */
    public CompletableFuture<JsonObject> fetchConfig() {
        return getJson("/miragefox/plugin/config").thenApply(el -> {
            if (el == null || !el.isJsonObject() || isApiError(el)) return null;
            JsonObject root = el.getAsJsonObject();
            if (root.has("data") && root.get("data").isJsonObject()) {
                return root.getAsJsonObject("data");
            }
            if (root.has("code") && root.get("code").getAsInt() == 0) {
                return root; // 兼容 data 缺失但 code=0 的情况
            }
            plugin.getLogger().warning("云端配置响应格式异常: " + root.toString());
            return null;
        });
    }

    /** GET /miragefox/plugin/tools -> JsonArray（失败返回 null） */
    public CompletableFuture<JsonArray> fetchTools() {
        return getJson("/miragefox/plugin/tools").thenApply(el -> {
            if (el == null || !el.isJsonObject() || isApiError(el)) return null;
            JsonObject root = el.getAsJsonObject();
            if (root.has("data") && root.get("data").isJsonArray()) {
                return root.getAsJsonArray("data");
            }
            return null;
        });
    }

    /** GET /miragefox/plugin/knowledge -> JsonArray（失败返回 null） */
    public CompletableFuture<JsonArray> fetchKnowledge() {
        return getJson("/miragefox/plugin/knowledge").thenApply(el -> {
            if (el == null || !el.isJsonObject() || isApiError(el)) return null;
            JsonObject root = el.getAsJsonObject();
            if (root.has("data") && root.get("data").isJsonArray()) {
                return root.getAsJsonArray("data");
            }
            return null;
        });
    }

    /** GET /miragefox/plugin/chat/history?player_uuid=..&session_type=.. -> JsonArray（失败返回 null） */
    public CompletableFuture<JsonArray> fetchChatHistory(String uuid, String type) {
        StringBuilder q = new StringBuilder("/miragefox/plugin/chat/history?");
        if (uuid != null && !uuid.isEmpty()) q.append("player_uuid=").append(encode(uuid));
        if (type != null && !type.isEmpty()) q.append("&session_type=").append(encode(type));
        return getJson(q.toString()).thenApply(el -> {
            if (el == null || !el.isJsonObject() || isApiError(el)) return null;
            JsonObject root = el.getAsJsonObject();
            if (root.has("data") && root.get("data").isJsonArray()) {
                return root.getAsJsonArray("data");
            }
            return null;
        });
    }

    // ---------- POST ----------

    /** POST /miragefox/plugin/chat/message -> 是否提交成功 */
    public CompletableFuture<Boolean> submitMessage(String uuid, String name, String type,
                                                    String role, String content,
                                                    String toolCallsJson, int tokensUsed) {
        JsonObject body = new JsonObject();
        if (uuid != null && !uuid.isEmpty()) body.addProperty("player_uuid", uuid);
        if (name != null && !name.isEmpty()) body.addProperty("player_name", name);
        if (type != null && !type.isEmpty()) body.addProperty("session_type", type);
        if (role != null && !role.isEmpty()) body.addProperty("role", role);
        // content 必填：即使为空也发空串，避免后端 ChatMessageIn.content 422
        body.addProperty("content", content != null ? content : "");
        if (toolCallsJson != null && !toolCallsJson.isEmpty()) body.addProperty("tool_calls", toolCallsJson);
        body.addProperty("tokens_used", tokensUsed);
        return postJson("/miragefox/plugin/chat/message", body)
                .thenApply(el -> el != null && !isApiError(el));
    }

    /** POST /miragefox/plugin/context/archive -> 提交被淘汰的上下文消息，供后端 LLM 压缩进玩家记忆 */
    public CompletableFuture<Boolean> archiveContext(String uuid, String name, JsonArray messages) {
        JsonObject body = new JsonObject();
        if (uuid != null && !uuid.isEmpty()) body.addProperty("player_uuid", uuid);
        if (name != null && !name.isEmpty()) body.addProperty("player_name", name);
        if (messages != null) body.add("messages", messages);
        return postJson("/miragefox/plugin/context/archive", body)
                .thenApply(el -> el != null && !isApiError(el));
    }

    /** POST /miragefox/plugin/search-memories -> 搜索该玩家记忆段落（返回 {results: [...]}） */
    public CompletableFuture<JsonArray> searchMemories(String uuid, String query) {
        JsonObject body = new JsonObject();
        if (uuid != null && !uuid.isEmpty()) body.addProperty("player_uuid", uuid);
        if (query != null && !query.isEmpty()) body.addProperty("query", query);
        return postJson("/miragefox/plugin/search-memories", body).thenApply(el -> {
            if (el == null || !el.isJsonObject()) return new JsonArray();
            JsonObject root = el.getAsJsonObject();
            if (root.has("data") && root.get("data").isJsonObject()) {
                JsonObject data = root.getAsJsonObject("data");
                if (data.has("results") && data.get("results").isJsonArray()) {
                    return data.getAsJsonArray("results");
                }
            }
            return new JsonArray();
        });
    }

    /** POST /miragefox/plugin/heartbeat -> 是否提交成功 */
    public CompletableFuture<Boolean> heartbeat(JsonObject stats) {
        JsonObject body = (stats != null) ? stats : new JsonObject();
        return postJson("/miragefox/plugin/heartbeat", body)
                .thenApply(el -> el != null && !isApiError(el));
    }

    /** POST /miragefox/plugin/search-docs -> 搜索结果 JsonArray（失败返回空数组） */
    public CompletableFuture<JsonArray> searchDocs(String query, int maxResults) {
        JsonObject body = new JsonObject();
        if (query != null) body.addProperty("query", query);
        body.addProperty("max_results", Math.max(1, Math.min(3, maxResults)));
        return postJson("/miragefox/plugin/search-docs", body).thenApply(el -> {
            if (el == null || !el.isJsonObject()) return new JsonArray();
            JsonObject root = el.getAsJsonObject();
            if (root.has("data") && root.get("data").isJsonObject()) {
                JsonObject data = root.getAsJsonObject("data");
                if (data.has("results") && data.get("results").isJsonArray()) {
                    return data.getAsJsonArray("results");
                }
            }
            return new JsonArray();
        });
    }

    // ---------- 记忆 & 情绪 API ----------

    /** POST /miragefox/plugin/player-context -> 玩家上下文 JsonObject（失败返回 null） */
    public CompletableFuture<JsonObject> fetchPlayerContext(String uuid, String name) {
        JsonObject body = new JsonObject();
        if (uuid != null && !uuid.isEmpty()) body.addProperty("player_uuid", uuid);
        if (name != null && !name.isEmpty()) body.addProperty("player_name", name);
        return postJson("/miragefox/plugin/player-context", body).thenApply(el -> {
            if (el == null || !el.isJsonObject() || isApiError(el)) return null;
            JsonObject root = el.getAsJsonObject();
            if (root.has("data") && root.get("data").isJsonObject()) {
                return root.getAsJsonObject("data");
            }
            return null;
        });
    }

    /** POST /miragefox/plugin/adjust-favorability -> JsonObject {ok:...}（失败返回 null） */
    public CompletableFuture<JsonObject> adjustFavorability(String uuid, String name, int change, String reason) {
        JsonObject body = new JsonObject();
        if (uuid != null && !uuid.isEmpty()) body.addProperty("player_uuid", uuid);
        if (name != null && !name.isEmpty()) body.addProperty("player_name", name);
        body.addProperty("change", change);
        if (reason != null && !reason.isEmpty()) body.addProperty("reason", reason);
        return postJson("/miragefox/plugin/adjust-favorability", body).thenApply(el -> {
            if (el == null || !el.isJsonObject() || isApiError(el)) return null;
            JsonObject root = el.getAsJsonObject();
            if (root.has("data") && root.get("data").isJsonObject()) {
                return root.getAsJsonObject("data");
            }
            return null;
        });
    }

    /** POST /miragefox/plugin/set-emotion -> JsonObject {ok:...}（失败返回 null） */
    public CompletableFuture<JsonObject> setEmotion(String uuid, String emotion, int intensity, String reason, String trigger) {
        JsonObject body = new JsonObject();
        if (uuid != null && !uuid.isEmpty()) body.addProperty("player_uuid", uuid);
        if (emotion != null && !emotion.isEmpty()) body.addProperty("emotion", emotion);
        body.addProperty("intensity", intensity);
        if (reason != null && !reason.isEmpty()) body.addProperty("reason", reason);
        if (trigger != null && !trigger.isEmpty()) body.addProperty("trigger_context", trigger);
        return postJson("/miragefox/plugin/set-emotion", body).thenApply(el -> {
            if (el == null || !el.isJsonObject() || isApiError(el)) return null;
            JsonObject root = el.getAsJsonObject();
            if (root.has("data") && root.get("data").isJsonObject()) {
                return root.getAsJsonObject("data");
            }
            return null;
        });
    }

    /** POST /miragefox/plugin/remember-player -> JsonObject {ok:...}（失败返回 null） */
    public CompletableFuture<JsonObject> rememberPlayer(String uuid, String content, String type) {
        JsonObject body = new JsonObject();
        if (uuid != null && !uuid.isEmpty()) body.addProperty("player_uuid", uuid);
        if (content != null && !content.isEmpty()) body.addProperty("content", content);
        if (type != null && !type.isEmpty()) body.addProperty("memory_type", type);
        return postJson("/miragefox/plugin/remember-player", body).thenApply(el -> {
            if (el == null || !el.isJsonObject() || isApiError(el)) return null;
            JsonObject root = el.getAsJsonObject();
            if (root.has("data") && root.get("data").isJsonObject()) {
                return root.getAsJsonObject("data");
            }
            return null;
        });
    }

    /** POST /miragefox/plugin/search-conversations -> 搜索结果 JsonArray（失败返回空数组） */
    public CompletableFuture<JsonArray> searchConversations(String uuid, String query) {
        JsonObject body = new JsonObject();
        if (uuid != null && !uuid.isEmpty()) body.addProperty("player_uuid", uuid);
        if (query != null && !query.isEmpty()) body.addProperty("query", query);
        return postJson("/miragefox/plugin/search-conversations", body).thenApply(el -> {
            if (el == null || !el.isJsonObject()) return new JsonArray();
            JsonObject root = el.getAsJsonObject();
            if (root.has("data") && root.get("data").isJsonObject()) {
                JsonObject data = root.getAsJsonObject("data");
                if (data.has("results") && data.get("results").isJsonArray()) {
                    return data.getAsJsonArray("results");
                }
            }
            return new JsonArray();
        });
    }

    private CompletableFuture<JsonElement> getJson(String path) {
        if (!isAvailable()) return CompletableFuture.completedFuture(null);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                    .GET()
                    .build();
            return sendWithRetry(request, true, path);
        } catch (Exception e) {
            plugin.getLogger().warning("云端 GET 请求构建失败 " + path + ": " + e.getMessage());
            return CompletableFuture.completedFuture(null);
        }
    }

    private CompletableFuture<JsonElement> postJson(String path, JsonObject body) {
        if (!isAvailable()) return CompletableFuture.completedFuture(null);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + token)
                    .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                    .build();
            return sendWithRetry(request, false, path);
        } catch (Exception e) {
            plugin.getLogger().warning("云端 POST 请求构建失败 " + path + ": " + e.getMessage());
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * 发送请求并在失败（网络异常/超时/5xx）时按配置重试（默认最多 8 次，指数退避）。
     * 4xx（如 401/422/404）为确定性错误，不重试。
     */
    private CompletableFuture<JsonElement> sendWithRetry(HttpRequest request, boolean isGet, String path) {
        if (closed) return CompletableFuture.completedFuture(null);
        int retryMax = plugin.getConfigManager().getCloudRetryMax();
        long baseDelay = plugin.getConfigManager().getCloudRetryBaseDelayMs();

        CompletableFuture<JsonElement> attempt = doSend(request, isGet, path);
        for (int i = 1; i < retryMax; i++) {
            final int attemptNo = i;
            attempt = attempt.thenCompose(result -> {
                // 只有 null 结果才重试（说明网络/超时/5xx 失败）
                if (result != null) return CompletableFuture.completedFuture(result);
                if (closed) return CompletableFuture.completedFuture(null);
                long delay = Math.min(5000, baseDelay * (1L << Math.min(attemptNo, 6)));
                plugin.getLogger().warning("云端 " + (isGet ? "GET" : "POST") + " " + path
                        + " 失败，第 " + (attemptNo + 1) + "/" + retryMax + " 次重试（" + delay + "ms 后）");
                return CompletableFuture.supplyAsync(() -> sleepQuietly(delay))
                        .thenCompose(v -> closed ? CompletableFuture.completedFuture((JsonElement) null) : doSend(request, isGet, path));
            });
        }
        return attempt;
    }

    private CompletableFuture<JsonElement> doSend(HttpRequest request, boolean isGet, String path) {
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .thenApply(resp -> {
                    int code = resp.statusCode();
                    boolean ok = isGet ? (code == 200) : (code >= 200 && code < 300);
                    if (!ok) {
                        // 4xx 确定性错误不重试（直接返回一个"非空哨兵"避免误重试）
                        if (code >= 400 && code < 500) {
                            plugin.getLogger().warning("云端 " + (isGet ? "GET" : "POST") + " 失败 " + path + ": HTTP " + code);
                            // 哨兵需要避免与真实响应混淆：加标记字段（NEW_SENTINEL 为静态共享对象，此处不得变更）
                            JsonObject err = new JsonObject();
                            err.addProperty("_api_error_sentinel", true);
                            err.addProperty("http_code", code);
                            return err;
                        }
                        plugin.getLogger().warning("云端 " + (isGet ? "GET" : "POST") + " 失败 " + path + ": HTTP " + code);
                        return null; // 触发重试
                    }
                    return parse(resp.body());
                })
                .exceptionally(e -> {
                    plugin.getLogger().warning("云端 " + (isGet ? "GET" : "POST") + " 异常 " + path + ": " + e.getMessage());
                    return null; // 触发重试
                });
    }

    /** 4xx 确定性错误的哨兵：表示"已明确失败，无需重试"（带标记供调用方识别） */
    private static final JsonObject NEW_SENTINEL = new JsonObject();

    /** 判断响应是否为 4xx 确定性错误哨兵 */
    private static boolean isApiError(JsonElement el) {
        return el != null && el.isJsonObject()
                && ((JsonObject) el).has("_api_error_sentinel");
    }

    private static Void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return null;
    }
    private JsonElement parse(String body) {
        if (body == null || body.isEmpty()) return null;
        try {
            return gson.fromJson(body, JsonElement.class);
        } catch (Exception e) {
            plugin.getLogger().warning("云端响应 JSON 解析失败: " + e.getMessage());
            return null;
        }
    }

    private String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}