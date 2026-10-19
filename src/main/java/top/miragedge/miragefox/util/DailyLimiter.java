package top.miragedge.miragefox.util;

import org.bukkit.configuration.file.YamlConfiguration;
import top.miragedge.miragefox.MirageFox;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 每日限次器 —— 让「按命令/API 触发的 AI 回复」支持每日只执行一次。
 *
 * 典型场景：早安/晚安问候等定时消息，一天只希望发一次。
 * 用法：
 *   boolean first = limiter.tryRunToday("morning_greeting"); // 今天首次调用返回 true 并记录
 *   boolean already = limiter.isRunToday("morning_greeting");
 *
 * 数据持久化到 plugins/MirageFox/daily.yml（key → yyyy-MM-dd），重启不丢。
 * 线程安全：ConcurrentHashMap + compute 原子更新。
 */
public class DailyLimiter {

    private final MirageFox plugin;
    private final File file;
    private final Map<String, String> runs = new ConcurrentHashMap<>(); // key -> "yyyy-MM-dd"

    public DailyLimiter(MirageFox plugin) {
        this.plugin = plugin;
        File dir = plugin.getDataFolder();
        if (!dir.exists()) {
            dir.mkdirs();
        }
        this.file = new File(dir, "daily.yml");
        load();
    }

    private void load() {
        if (!file.exists()) return;
        try {
            YamlConfiguration conf = YamlConfiguration.loadConfiguration(file);
            if (conf.getConfigurationSection("runs") != null) {
                for (String key : conf.getConfigurationSection("runs").getKeys(false)) {
                    String date = conf.getString("runs." + key);
                    if (key != null && date != null) {
                        runs.put(key, date);
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("每日限次记录加载失败: " + e.getMessage());
        }
    }

    /**
     * 尝试在今天执行 key 对应的动作。
     * @return true = 今天还没有执行过，已占用今天的配额并返回 true；false = 今天已执行过，跳过。
     */
    public boolean tryRunToday(String key) {
        if (key == null || key.isEmpty()) return true; // 未指定 key 不限制
        String today = LocalDate.now().toString();
        boolean[] allowed = {false};
        runs.compute(key, (k, old) -> {
            if (today.equals(old)) {
                return old; // 今天已执行，不允许
            }
            allowed[0] = true;
            return today;
        });
        if (allowed[0]) {
            markDirty();
        }
        return allowed[0];
    }

    /** 查询 key 今天是否已执行 */
    public boolean isRunToday(String key) {
        if (key == null || key.isEmpty()) return false;
        return LocalDate.now().toString().equals(runs.get(key));
    }

    /** 手动重置 key（供管理命令使用） */
    public void reset(String key) {
        if (key == null) return;
        runs.remove(key);
        markDirty();
    }

    // ---------- 持久化（节流 + 异步） ----------

    private long lastSaveRequest = 0;

    private void markDirty() {
        long now = System.currentTimeMillis();
        if (now - lastSaveRequest < 5000) return;
        lastSaveRequest = now;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::save);
    }

    public void save() {
        YamlConfiguration conf = new YamlConfiguration();
        for (Map.Entry<String, String> e : runs.entrySet()) {
            conf.set("runs." + e.getKey(), e.getValue());
        }
        try {
            conf.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("每日限次记录保存失败: " + e.getMessage());
        }
    }
}
