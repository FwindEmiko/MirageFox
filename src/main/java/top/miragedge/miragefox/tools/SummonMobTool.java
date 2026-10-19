package top.miragedge.miragefox.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import top.miragedge.miragefox.MirageFox;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 灵兽术 —— 召唤可爱的小动物（有数量上限防卡服）。
 *
 * 实现要点：
 * - 每只宠物打上 PersistentDataContainer 归属标记（miragefox:pet_owner）；
 * - 每玩家活跃宠物数记录到 identity 缓存（重启不丢失）；
 * - 宠物死亡/被移除时自动减计数（由监听器调用）。
 */
public class SummonMobTool extends AbstractTool {

    private static final String[] PETS = {"cat", "fox", "wolf", "rabbit", "parrot",
                            "chicken", "sheep"};

    private static final int MAX_SUMMON = 3;
    private static final int MAX_ACTIVE_PETS = 5; // 每玩家最多同时存在5只

    /** 宠物归属标记 key */
    public static final String PET_OWNER_KEY = "pet_owner";
    private static NamespacedKey petKey;

    private final ConcurrentHashMap<String, Integer> playerPetCount = new ConcurrentHashMap<>();

    public SummonMobTool(MirageFox plugin) {
        super(plugin);
        if (petKey == null) {
            petKey = new NamespacedKey(plugin, PET_OWNER_KEY);
        }
    }

    public static NamespacedKey getPetKey() {
        return petKey;
    }

    @Override
    public int getPrice() { return 300; }

    @Override
    public int getRateLimitSeconds() { return 600; }

    @Override
    public int getDailyLimit() { return 3; }

    @Override
    public String getName() {
        return "summon_pet";
    }

    @Override
    protected String getDescription() {
        return "在目标玩家附近召唤一只可爱的小动物。"
                        + "可用: cat（猫）, fox（狐狸）, wolf（狼）, rabbit（兔子）, parrot（鹦鹉）, "
                        + "chicken（鸡）, sheep（羊）。"
                        + "每次最多召唤" + MAX_SUMMON + "只。";
    }

    @Override
    protected void buildParameters(JsonObject properties, JsonArray required) {
        addStringProp(properties, "target", "目标玩家用户名", true, required);
        addEnumProp(properties, "pet_type", "宠物类型", true, required, PETS);
        addIntProp(properties, "amount", "数量，1-" + MAX_SUMMON + "，默认1", false, required);
    }

    @Override
    protected ToolResult executeImpl(JsonObject args, String caller) {
        String targetName = getString(args, "target", "");
        String petType = getString(args, "pet_type", "cat").toLowerCase();
        int amount = Math.max(1, Math.min(MAX_SUMMON, getInt(args, "amount", 1)));

        Player target = findPlayer(targetName);
        if (target == null) {
            return ToolResult.fail("找不到玩家 " + targetName + "，他可能不在线喵。");
        }

        EntityType type = getEntityType(petType);
        if (type == null) {
            return ToolResult.fail("宠物 " + petType + " 不存在喵。可用: cat, fox, wolf, rabbit 等。");
        }

        // 检查玩家当前已有宠物数量（含持久化计数）
        String key = target.getUniqueId().toString();
        int current = getActiveCount(key);
        int available = MAX_ACTIVE_PETS - current;
        if (available <= 0) {
            return ToolResult.fail(target.getName() + " 已经有 " + current + " 只灵兽了，不能再召唤更多喵。");
        }
        int toSpawn = Math.min(amount, available);

        int spawned = 0;
        for (int i = 0; i < toSpawn; i++) {
            try {
                double offsetX = (Math.random() - 0.5) * 4;
                double offsetZ = (Math.random() - 0.5) * 4;
                Entity entity = target.getWorld().spawnEntity(target.getLocation().add(offsetX, 0, offsetZ), type);
                // 打上归属标记，供移除事件减计数
                entity.getPersistentDataContainer().set(petKey, PersistentDataType.STRING, key);
                spawned++;
            } catch (Exception e) {
                break;
            }
        }

        if (spawned > 0) {
            addCount(key, spawned);
        }

        target.sendMessage("§" + plugin.getConfigManager().getAccentColor()
                + "[灵兽术] §" + plugin.getConfigManager().getTextColor()
                + "星玖召唤了" + spawned + "只" + getPetName(petType) + "来陪你喵～");

        if (spawned == 0) {
            return ToolResult.fail("召唤失败了喵，请换个地方再试试。");
        }
        return ToolResult.ok("在 " + target.getName() + " 附近召唤了 " + spawned + " 只" + getPetName(petType)
                + "（当前共存 " + (current + spawned) + "/" + MAX_ACTIVE_PETS + " 只）。");
    }

    // ---- 宠物计数管理 ----

    public int getActiveCount(String ownerUuid) {
        int inMemory = playerPetCount.getOrDefault(ownerUuid, 0);
        int persisted = plugin.getPlayerIdentityCache().getPetCount(ownerUuid);
        return Math.max(inMemory, persisted);
    }

    /** 计数器取两者最大值以避免重复统计 */
    private void addCount(String ownerUuid, int delta) {
        playerPetCount.merge(ownerUuid, delta, Integer::sum);
        plugin.getPlayerIdentityCache().addPetCount(ownerUuid, delta);
    }

    /** 宠物移除/死亡时减计数（监听器调用） */
    public void onPetRemoved(Entity entity) {
        if (entity == null || petKey == null) return;
        String owner = entity.getPersistentDataContainer().get(petKey, PersistentDataType.STRING);
        if (owner == null || owner.isEmpty()) return;
        playerPetCount.computeIfPresent(owner, (k, v) -> Math.max(0, v - 1));
        plugin.getPlayerIdentityCache().addPetCount(owner, -1);
    }

    private EntityType getEntityType(String name) {
        switch (name) {
            case "cat": return EntityType.CAT;
            case "fox": return EntityType.FOX;
            case "wolf": return EntityType.WOLF;
            case "rabbit": return EntityType.RABBIT;
            case "parrot": return EntityType.PARROT;
            case "chicken": return EntityType.CHICKEN;
            case "sheep": return EntityType.SHEEP;
            default: return null;
        }
    }

    private String getPetName(String name) {
        switch (name) {
            case "cat": return "猫猫";
            case "fox": return "小狐狸";
            case "wolf": return "狼狼";
            case "rabbit": return "兔兔";
            case "parrot": return "鹦鹉";
            case "chicken": return "鸡";
            case "sheep": return "羊羊";
            default: return name;
        }
    }
}
