package top.miragedge.miragefox.util;

import org.bukkit.configuration.file.YamlConfiguration;
import top.miragedge.miragefox.MirageFox;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

/**
 * 玩家身份缓存 —— 维护 玩家名→UUID 映射以及每玩家的宠物计数。
 *
 * 用途：
 * 1. 修复云端档案碎片化：离线玩家也能拿到真实 UUID（而非回退玩家名）。
 * 2. 宠物计数持久化：重启后仍能正确限制每玩家宠物上限。
 *
 * 数据落盘到 plugins/MirageFox/identities.yml（异步保存）。
 */
public class PlayerIdentityCache {

    private final MirageFox plugin;
    private final File file;
    private final Map<String, String> nameToUuid = new ConcurrentHashMap<>();
    private final Map<String, Integer> petCounts = new ConcurrentHashMap<>();

    public PlayerIdentityCache(MirageFox plugin) {
        this.plugin = plugin;
        File dir = plugin.getDataFolder();
        if (!dir.exists()) {
            dir.mkdirs();
        }
        this.file = new File(dir, "identities.yml");
        load();
    }

    private void load() {
        if (!file.exists()) return;
        try {
            YamlConfiguration conf = YamlConfiguration.loadConfiguration(file);
            if (conf.getConfigurationSection("uuid") != null) {
                for (String name : conf.getConfigurationSection("uuid").getKeys(false)) {
                    String uuid = conf.getString("uuid." + name);
                    if (name != null && uuid != null && !uuid.isEmpty()) {
                        nameToUuid.put(name.toLowerCase(), uuid);
                    }
                }
            }
            if (conf.getConfigurationSection("pets") != null) {
                for (String uuid : conf.getConfigurationSection("pets").getKeys(false)) {
                    petCounts.put(uuid, conf.getInt("pets." + uuid, 0));
                }
            }
            plugin.getLogger().info("身份缓存已加载: " + nameToUuid.size() + " 个名字映射, "
                    + petCounts.size() + " 个宠物计数");
        } catch (Exception e) {
            plugin.getLogger().warning("身份缓存加载失败: " + e.getMessage());
        }
    }

    /** 玩家加入时记录 名字→UUID 映射 */
    public void record(String name, UUID uuid) {
        if (name == null || uuid == null) return;
        nameToUuid.put(name.toLowerCase(), uuid.toString());
        markDirty();
    }

    /** 按玩家名查 UUID（不存在返回 null） */
    public String getUuid(String playerName) {
        if (playerName == null) return null;
        return nameToUuid.get(playerName.toLowerCase());
    }

    // ---------- 宠物计数持久化 ----------

    public int getPetCount(String ownerUuid) {
        if (ownerUuid == null) return 0;
        return petCounts.getOrDefault(ownerUuid, 0);
    }

    /** 增加（正数）或减少（负数）宠物计数，最小 0 */
    public void addPetCount(String ownerUuid, int delta) {
        if (ownerUuid == null) return;
        petCounts.compute(ownerUuid, (k, v) -> Math.max(0, (v == null ? 0 : v) + delta));
        markDirty();
    }

    // ---------- 持久化（节流 + 异步） ----------

    private long lastSaveRequest = 0;

    private void markDirty() {
        long now = System.currentTimeMillis();
        if (now - lastSaveRequest < 5000) return; // 5 秒节流
        lastSaveRequest = now;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::save);
    }

    public void save() {
        YamlConfiguration conf = new YamlConfiguration();
        for (Map.Entry<String, String> e : nameToUuid.entrySet()) {
            conf.set("uuid." + e.getKey(), e.getValue());
        }
        for (Map.Entry<String, Integer> e : petCounts.entrySet()) {
            conf.set("pets." + e.getKey(), e.getValue());
        }
        try {
            conf.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("身份缓存保存失败: " + e.getMessage());
        }
    }
}
