package top.miragedge.miragefox.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import top.miragedge.miragefox.MirageFox;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.EnumSet;
import java.util.Set;

/**
 * 狐跃术 —— 像吃紫颂果那样把玩家随机传送到身边一小段距离（纯娱乐）。
 *
 * 安全设计：
 * - 落点黑名单：排除岩浆块/仙人掌/营火/火/岩浆等危险方块；
 * - 校验站立点及其上方 2 格为空气；
 * - 找不到安全点时直接失败返回（不再原地随机回退，避免掉虚空/卡墙）。
 */
public class RandomTeleportTool extends AbstractTool {

    private static final Set<Material> DANGEROUS_GROUND = EnumSet.of(
            Material.LAVA, Material.MAGMA_BLOCK, Material.CACTUS,
            Material.CAMPFIRE, Material.SOUL_CAMPFIRE, Material.FIRE,
            Material.SWEET_BERRY_BUSH, Material.POWDER_SNOW,
            Material.WITHER_ROSE, Material.POINTED_DRIPSTONE
    );

    public RandomTeleportTool(MirageFox plugin) {
        super(plugin);
    }

    @Override
    public int getPrice() { return 20; }

    @Override
    public String getName() {
        return "random_teleport";
    }

    @Override
    protected String getDescription() {
        return "像吃紫颂果那样把目标玩家随机传送到身边一小段距离！纯娱乐搞怪效果。"
                + "radius 是传送半径（默认8格，最大16格），传送到安全位置。"
                + "这个法术只是小距离瞬移玩玩，不会传送太远。";
    }

    @Override
    protected void buildParameters(JsonObject properties, JsonArray required) {
        addStringProp(properties, "target", "目标玩家用户名", true, required);
        addIntProp(properties, "radius", "传送半径（格），默认8，最大16", false, required);
    }

    @Override
    protected ToolResult executeImpl(JsonObject args, String caller) {
        String targetName = getString(args, "target", "");
        int radius = Math.max(1, Math.min(16, getInt(args, "radius", 8)));

        Player target = findPlayer(targetName);
        if (target == null) {
            return ToolResult.fail("找不到玩家 " + targetName + "，他可能不在线喵。");
        }

        Location origin = target.getLocation();
        Location targetLoc = findSafeLocation(origin, radius);

        if (targetLoc == null) {
            return ToolResult.fail("没找到足够安全的位置喵，换个地方试试？");
        }

        target.teleport(targetLoc);
        target.sendMessage("§" + plugin.getConfigManager().getAccentColor()
                + "[狐跃术] §" + plugin.getConfigManager().getTextColor()
                + "把你瞬移到旁边啦，像吃了紫颂果一样喵～");

        return ToolResult.ok("把 " + target.getName() + " 随机传送到半径 " + radius + " 格内了喵~");
    }

    /** 尝试找到一个安全的地面位置（小距离，玩家站在最高实心方块上方） */
    private Location findSafeLocation(Location origin, int radius) {
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = Math.random() * 2 * Math.PI;
            double dist = 1 + Math.random() * radius;
            int dx = (int) Math.round(Math.cos(angle) * dist);
            int dz = (int) Math.round(Math.sin(angle) * dist);

            int highestY = origin.getWorld().getHighestBlockYAt(
                    origin.getBlockX() + dx, origin.getBlockZ() + dz);

            Location standLoc = origin.clone().add(dx, 0, dz);
            standLoc.setY(highestY + 1);

            Block stand = standLoc.getBlock();
            Block below = standLoc.clone().subtract(0, 1, 0).getBlock();
            Block above = standLoc.clone().add(0, 1, 0).getBlock();
            Block above2 = standLoc.clone().add(0, 2, 0).getBlock();

            // 站立点与上方两格必须是空气/非危险方块
            if (!isSafeStand(stand)) continue;
            if (!isSafeStand(above) || !isSafeStand(above2)) continue;
            // 下方必须是实心且非危险的承载面
            if (!below.getType().isSolid() || below.getType().isAir()) continue;
            if (DANGEROUS_GROUND.contains(below.getType())) continue;
            if (highestY <= origin.getWorld().getMinHeight() + 2) continue;

            return standLoc.add(0.5, 0, 0.5);
        }
        // 找不到安全点：不传送，交由调用方返回失败
        return null;
    }

    private boolean isSafeStand(Block b) {
        return b.getType().isAir() && !DANGEROUS_GROUND.contains(b.getType());
    }
}
