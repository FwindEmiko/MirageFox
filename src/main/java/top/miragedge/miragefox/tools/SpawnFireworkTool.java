package top.miragedge.miragefox.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import top.miragedge.miragefox.MirageFox;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;

/**
 * 烟花术 —— 在玩家位置生成烟花
 */
public class SpawnFireworkTool extends AbstractTool {

    public SpawnFireworkTool(MirageFox plugin) {
        super(plugin);
    }

    @Override
    public int getPrice() { return 10; }

    @Override
    public String getName() {
        return "spawn_firework";
    }

    @Override
    protected String getDescription() {
        return "在目标玩家位置生成一个华丽的烟花。可以用于庆祝、欢迎或制造气氛。"
                + "power 是烟花高度（1-3，默认2）。type 是烟花形状: ball（球形）, star（星形）, "
                + "burst（爆裂）, creeper（苦力怕形）。会生成随机颜色。";
    }

    @Override
    protected void buildParameters(JsonObject properties, JsonArray required) {
        addStringProp(properties, "target", "目标玩家用户名", true, required);
        addIntProp(properties, "power", "烟花飞行高度，1-3（默认2）", false, required);
        addEnumProp(properties, "type", "烟花形状: ball（球形）, star（星形）, burst（爆裂）, creeper（苦力怕形，默认ball）", false, required, new String[]{"ball", "star", "burst", "creeper"});
    }

    @Override
    protected ToolResult executeImpl(JsonObject args, String caller) {
        String targetName = getString(args, "target", "");
        int power = Math.max(1, Math.min(3, getInt(args, "power", 2)));
        String typeName = getString(args, "type", "ball").toLowerCase();

        Player target = findPlayer(targetName);
        if (target == null) {
            return ToolResult.fail("找不到玩家 " + targetName + "，他可能不在线喵。");
        }

        FireworkEffect.Type type;
        switch (typeName) {
            case "star": type = FireworkEffect.Type.STAR; break;
            case "burst": type = FireworkEffect.Type.BURST; break;
            case "creeper": type = FireworkEffect.Type.CREEPER; break;
            default: type = FireworkEffect.Type.BALL;
        }

        Color[] colors = {Color.RED, Color.ORANGE, Color.YELLOW, Color.GREEN, Color.BLUE, Color.PURPLE, Color.FUCHSIA, Color.AQUA};
        Color mainColor = colors[(int)(Math.random() * colors.length)];
        Color fadeColor = colors[(int)(Math.random() * colors.length)];

        try {
            Firework firework = target.getWorld().spawn(target.getLocation().add(0, 1, 0), Firework.class);
            FireworkMeta meta = firework.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder()
                    .with(type)
                    .withColor(mainColor)
                    .withFade(fadeColor)
                    .flicker(true)
                    .trail(true)
                    .build());
            meta.setPower(power);
            firework.setFireworkMeta(meta);

            target.sendMessage(plugin.getConfigManager().getChatTag() + "为你放了一个烟花喵～");
            return ToolResult.ok("在 " + target.getName() + " 位置生成了一个 " + typeName + " 形烟花（高度=" + power + "）。");
        } catch (Exception e) {
            return ToolResult.fail("烟花生成失败了喵（" + e.getMessage() + "）。");
        }
    }
}
