package top.miragedge.miragefox.ai;

import top.miragedge.miragefox.MirageFox;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 对话状态管理器 —— 统一管理「连续对话」与「主动对话」两种机制。
 *
 * 连续对话：
 * - 玩家提到星玖（或主动对话命中）后进入对话窗口，窗口内免关键词续聊；
 * - 识别退出词（闭嘴/别说了/不聊了/结束…）被动退出；
 * - 窗口超时（默认 90 秒无新消息）自动退出；若玩家已离线也退出。
 *
 * 主动对话：
 * - 统计全服玩家聊天消息，每满 20 条做一次 3% 概率抽奖；
 * - 命中后从最近消息里挑一条"像疑问句"的消息，让星玖主动插话解答（无需关键词）。
 *
 * 线程安全：所有状态由异步聊天线程调用，使用 ConcurrentHashMap + 弱一致迭代即可。
 */
public class ConversationManager {

    private final MirageFox plugin;

    // ---- 连续对话状态：玩家 -> 窗口截止时间戳 ----
    private final Map<UUID, Long> activeUntil = new ConcurrentHashMap<>();

    // ---- 主动对话：全服聊天计数 + 最近消息环形缓冲 ----
    private final AtomicInteger chatCountSinceRoll = new AtomicInteger(0);
    private final ArrayDeque<RecentMsg> recentMessages = new ArrayDeque<>(PROACTIVE_WINDOW);
    private long nextProactiveAt;

    private static final long CONVERSATION_WINDOW_MS = 90_000L;       // 连续对话窗口 90 秒
    private static final int PROACTIVE_ROLL_EVERY = 20;                // 每 20 条玩家消息抽一次
    private static final double PROACTIVE_CHANCE = 0.03;               // 3% 概率
    private static final int PROACTIVE_WINDOW = 20;                    // 最近 20 条消息内挑选
    private static final long PROACTIVE_COOLDOWN_MS = 60_000L;         // 主动插话冷却 60 秒
    private volatile long lastProactiveAt = 0;

    /** 退出连续对话的触发词 */
    private static final List<String> EXIT_WORDS = List.of(
            "闭嘴", "别说了", "不要说了", "不聊了", "结束对话", "再见", "拜拜", "先这样", "忙去了"
    );

    /** 像疑问句的触发词 */
    private static final List<String> QUESTION_MARKERS = List.of(
            "?", "？", "吗", "呢", "怎么", "为什么", "啥", "什么", "如何", "怎么做", "在哪"
    );

    /** 最近一条聊天记录 */
    private record RecentMsg(String sender, String content, long time) {}

    public ConversationManager(MirageFox plugin) {
        this.plugin = plugin;
        // 首次抽奖时间：累计满 PROACTIVE_ROLL_EVERY 后再判断（避免启动即触发）
    }

    // ============ 连续对话 ============

    /** 玩家是否处于连续对话窗口内 */
    public boolean isInConversation(UUID uuid) {
        Long until = activeUntil.get(uuid);
        if (until == null) return false;
        if (System.currentTimeMillis() > until) {
            // CAS：仅当窗口仍是本次读到的值才移除，避免误删并发刷新后的新窗口
            activeUntil.remove(uuid, until);
            return false;
        }
        return true;
    }

    /** 进入/刷新连续对话窗口 */
    public void refresh(UUID uuid) {
        activeUntil.put(uuid, System.currentTimeMillis() + CONVERSATION_WINDOW_MS);
    }

    /** 退出连续对话 */
    public void endConversation(UUID uuid) {
        activeUntil.remove(uuid);
    }

    /** 消息是否包含退出词 */
    public boolean isExitRequest(String message) {
        if (message == null) return false;
        for (String w : EXIT_WORDS) {
            if (message.contains(w)) return true;
        }
        return false;
    }

    /** 定时清理超时窗口（由 MirageFox 定时任务调用） */
    public void tick() {
        long now = System.currentTimeMillis();
        activeUntil.entrySet().removeIf(e -> now > e.getValue());
    }

    // ============ 主动对话 ============

    /**
     * 上报一条玩家聊天消息（无论是否提到星玖），返回是否需要主动插话。
     * @return 主动插话要针对的上下文 prompt（null = 本次不触发）
     */
    public String onPlayerChat(UUID senderUuid, String sender, String content) {
        long now = System.currentTimeMillis();
        synchronized (recentMessages) {
            recentMessages.addLast(new RecentMsg(sender, content, now));
            while (recentMessages.size() > PROACTIVE_WINDOW) {
                recentMessages.pollFirst();
            }
        }

        // 主动插话冷却期内不再触发
        if (now - lastProactiveAt < PROACTIVE_COOLDOWN_MS) {
            chatCountSinceRoll.set(0); // 冷却期不累计，避免堆触发
            return null;
        }

        if (chatCountSinceRoll.incrementAndGet() < PROACTIVE_ROLL_EVERY) {
            return null;
        }
        chatCountSinceRoll.set(0);

        // 3% 概率抽奖
        if (Math.random() >= PROACTIVE_CHANCE) {
            return null;
        }

        // 从最近消息中挑一条疑问句（排除最后一条本人——避免刚说完就抢答）
        String target = pickQuestion(senderUuid);
        if (target == null) {
            return null;
        }
        lastProactiveAt = now;
        return target;
    }

    /** 从环形缓冲挑一条像疑问句的消息，拼成给 AI 的上下文 */
    private String pickQuestion(UUID self) {
        synchronized (recentMessages) {
            RecentMsg best = null;
            Iterator<RecentMsg> it = recentMessages.descendingIterator();
            int guard = 0;
            while (it.hasNext() && guard < 8) {
                RecentMsg m = it.next();
                guard++;
                if (m.content() == null || m.content().isBlank()) continue;
                // 跳过明确对星玖说的话（避免抢答）
                if (mentionsFox(m.content())) continue;
                if (looksLikeQuestion(m.content())) {
                    best = m;
                    break;
                }
            }
            if (best == null) return null;
            return "服务器玩家「" + best.sender() + "」在公屏问道：" + best.content();
        }
    }

    private boolean mentionsFox(String text) {
        for (String p : plugin.getConfigManager().getMentionPrefixes()) {
            if (text.contains(p)) return true;
        }
        return false;
    }

    private boolean looksLikeQuestion(String text) {
        for (String marker : QUESTION_MARKERS) {
            if (text.contains(marker)) return true;
        }
        return false;
    }
}