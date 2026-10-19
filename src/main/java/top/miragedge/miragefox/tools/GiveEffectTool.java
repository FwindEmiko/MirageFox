package top.miragedge.miragefox.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import top.miragedge.miragefox.MirageFox;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * 灵效术 —— 效果分级定价，好的更贵、冷却更长。
 *
 * 便宜（30 货币单位，2分钟冷却）：speed, jump_boost, haste, luck, night_vision
 * 中等（80 货币单位，5分钟冷却）：fire_resistance, water_breathing, slow_falling, glow
 * 昂贵（150 货币单位，10分钟冷却）：invisibility, dolphin_grace
 *
 * 价格与冷却由 getPrice(args) / getRateLimitSeconds(args) 动态返回，
 * 统一走 AbstractTool 的「校验→扣款→执行→失败退款」流程，
 * 不在 executeImpl 里手动扣款或记录冷却。
 */
public class GiveEffectTool extends AbstractTool {

    private static final int TIER_CHEAP_PRICE = 30;
    private static final int TIER_CHEAP_COOLDOWN = 120;  // 2分钟
    private static final int TIER_CHEAP_MAX = 300;

    private static final int TIER_MEDIUM_PRICE = 80;
    private static final int TIER_MEDIUM_COOLDOWN = 300; // 5分钟
    private static final int TIER_MEDIUM_MAX = 300;

    private static final int TIER_EXPENSIVE_PRICE = 150;
    private static final int TIER_EXPENSIVE_COOLDOWN = 600; // 10分钟
    private static final int TIER_EXPENSIVE_MAX = 180;

    private static final String[] ALLOWED_EFFECTS = {
            "speed", "jump_boost", "haste", "luck", "night_vision",
            "fire_resistance", "water_breathing", "slow_falling", "glow",
            "invisibility", "dolphin_grace"
    };

    public GiveEffectTool(MirageFox plugin) {
        super(plugin);
    }

    @Override
    public String getName() { return "give_effect"; }

    @Override
    protected String getDescription() {
        return "给目标玩家施加一个趣味辅助状态效果。效果分级定价："
                + "便宜（2分钟冷却，30 " + plugin.getConfigManager().getCurrencyName() + "）：speed, jump_boost, haste, luck, night_vision；"
                + "中等（5分钟冷却，80 " + plugin.getConfigManager().getCurrencyName() + "）：fire_resistance, water_breathing, slow_falling, glow；"
                + "昂贵（10分钟冷却，150 " + plugin.getConfigManager().getCurrencyName() + "）：invisibility, dolphin_grace。"
                + "duration最长5分钟，amplifier等级0-4。";
    }

    @Override
    protected void buildParameters(JsonObject properties, JsonArray required) {
        addStringProp(properties, "target", "目标玩家用户名", true, required);
        addEnumProp(properties, "effect", "效果名称", true, required, ALLOWED_EFFECTS);
        addIntProp(properties, "duration", "持续时间（秒），最长时间取决于效果类型，默认30秒", false, required);
        addIntProp(properties, "amplifier", "效果等级，0=1级，1=2级（默认0）", false, required);
    }

    // ---- 动态价格/冷却 ----

    @Override
    public int getPrice(JsonObject args) {
        String effect = getString(args, "effect", "speed").toLowerCase();
        return getTier(effect).price;
    }

    @Override
    public int getRateLimitSeconds(JsonObject args) {
        String effect = getString(args, "effect", "speed").toLowerCase();
        return getTier(effect).cooldown;
    }

    @Override
    protected String validate(JsonObject args, String caller) {
        String effect = getString(args, "effect", "").toLowerCase();
        if (!isAllowed(effect)) {
            return "效果 " + effect + " 不可用，本狐没有这个法术喵。";
        }
        return super.validate(args, caller);
    }

    @Override
    protected ToolResult executeImpl(JsonObject args, String caller) {
        String targetName = getString(args, "target", "");
        String effectName = getString(args, "effect", "speed").toLowerCase();

        EffectTier tier = getTier(effectName);
        int maxDuration = tier.maxDuration;
        int duration = Math.max(5, Math.min(maxDuration, getInt(args, "duration", 30)));
        int amplifier = Math.max(0, Math.min(4, getInt(args, "amplifier", 0)));

        Player target = findPlayer(targetName);
        if (target == null) {
            return ToolResult.fail("找不到玩家 " + targetName + "，他可能不在线喵。");
        }

        PotionEffectType effectType = PotionEffectType.getByName(effectName);
        if (effectType == null) {
            return ToolResult.fail("效果 " + effectName + " 不存在喵。");
        }

        boolean applied = target.addPotionEffect(
                new PotionEffect(effectType, duration * 20, amplifier, false, true, true));
        if (!applied) {
            return ToolResult.fail("这个效果施放失败了喵（可能被更高级的效果覆盖了）。");
        }

        target.sendMessage(plugin.getConfigManager().getChatTag() + "给了你" + getEffectName(effectName) + "（" + duration + "秒）～");

        return ToolResult.ok("给 " + target.getName() + " 施加了 " + effectName + " "
                + (amplifier + 1) + "级，" + duration + "秒，花费 " + tier.price + " " + plugin.getConfigManager().getCurrencyName() + "。");
    }

    private boolean isAllowed(String name) {
        for (String s : ALLOWED_EFFECTS) {
            if (s.equals(name)) return true;
        }
        return false;
    }

    private EffectTier getTier(String name) {
        switch (name) {
            case "speed": case "jump_boost": case "haste": case "luck": case "night_vision":
                return new EffectTier(TIER_CHEAP_PRICE, TIER_CHEAP_COOLDOWN, TIER_CHEAP_MAX);
            case "fire_resistance": case "water_breathing": case "slow_falling": case "glow":
                return new EffectTier(TIER_MEDIUM_PRICE, TIER_MEDIUM_COOLDOWN, TIER_MEDIUM_MAX);
            case "invisibility": case "dolphin_grace":
                return new EffectTier(TIER_EXPENSIVE_PRICE, TIER_EXPENSIVE_COOLDOWN, TIER_EXPENSIVE_MAX);
            default:
                return new EffectTier(TIER_CHEAP_PRICE, TIER_CHEAP_COOLDOWN, TIER_CHEAP_MAX);
        }
    }

    private String getEffectName(String name) {
        switch (name) {
            case "speed": return "迅捷";
            case "jump_boost": return "跳跃提升";
            case "haste": return "急迫";
            case "night_vision": return "夜视";
            case "fire_resistance": return "抗火";
            case "water_breathing": return "水下呼吸";
            case "invisibility": return "隐身";
            case "luck": return "幸运";
            case "slow_falling": return "缓降";
            case "glow": return "发光";
            case "dolphin_grace": return "海豚的恩惠";
            default: return name;
        }
    }

    private static class EffectTier {
        final int price;
        final int cooldown;
        final int maxDuration;
        EffectTier(int price, int cooldown, int maxDuration) {
            this.price = price;
            this.cooldown = cooldown;
            this.maxDuration = maxDuration;
        }
    }
}
