package top.miragedge.miragefox.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import top.miragedge.miragefox.MirageFox;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

/**
 * 星尘术 —— 在玩家周围生成粒子效果（视觉华丽）
 */
public class SpawnParticlesTool extends AbstractTool {

    private static final String[] AVAILABLE_PARTICLES = {
            "heart", "happy_villager", "note", "enchant",
            "flame", "soul_fire_flame", "portal", "cloud",
            "end_rod", "drip_water", "witch", "spell",
            "sonic_boom", "cherry_leaves", "dolphin",
            "electric_spark", "sculk_soul", "glow_squid_ink",
            "dust", "wax_off", "wax_on"
    };

    public SpawnParticlesTool(MirageFox plugin) {
        super(plugin);
    }

    @Override
    public int getPrice() { return 0; }

    @Override
    public String getName() {
        return "spawn_particles";
    }

    @Override
    protected String getDescription() {
        return "在目标玩家周围生成华丽的粒子效果。可用粒子: heart（爱心）, happy_villager（快乐村民）, "
                + "note（音符）, flame（火焰）, soul_fire_flame（灵魂火焰）, portal（传送门）, "
                + "cloud（云）, end_rod（末地烛）, sonic_boom（音爆）, cherry_leaves（樱花）, "
                + "electric_spark（电火花）, sculk_soul（幽匿灵魂）等。count 是粒子数量（默认50）。";
    }

    @Override
    protected void buildParameters(JsonObject properties, JsonArray required) {
        addStringProp(properties, "target", "目标玩家用户名", true, required);
        addEnumProp(properties, "particle", "粒子类型", true, required, AVAILABLE_PARTICLES);
        addIntProp(properties, "count", "粒子数量，默认50", false, required);
    }

    @Override
    protected ToolResult executeImpl(JsonObject args, String caller) {
        String targetName = getString(args, "target", "");
        String particleName = getString(args, "particle", "heart").toUpperCase();
        int count = Math.max(1, Math.min(500, getInt(args, "count", 50)));

        Player target = findPlayer(targetName);
        if (target == null) {
            return ToolResult.fail("找不到玩家 " + targetName + "，他可能不在线喵。");
        }

        Particle particle;
        try {
            particle = Particle.valueOf(particleName);
        } catch (IllegalArgumentException e) {
            return ToolResult.fail("粒子 " + particleName + " 不存在喵。可用: heart, happy_villager, note, flame, cloud 等。");
        }

        Location loc = target.getLocation().add(0, 1, 0);
        try {
            target.getWorld().spawnParticle(
                    particle, loc, count, 0.5, 0.5, 0.5, 0.1
            );
        } catch (Exception e) {
            return ToolResult.fail("粒子效果施放失败了喵（" + e.getMessage() + "）。");
        }

        target.sendMessage(plugin.getConfigManager().getChatTag() + "在你周围撒下了美丽的星尘喵～");
        return ToolResult.ok("在 " + target.getName() + " 周围生成了 " + count + " 个 " + particleName + " 粒子。");
    }
}
