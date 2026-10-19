package top.miragedge.miragefox.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import top.miragedge.miragefox.MirageFox;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * 击掌术 —— 和玩家击掌互动（纯社交，零经济影响）。
 */
public class HighFiveTool extends AbstractTool {

    public HighFiveTool(MirageFox plugin) {
        super(plugin);
    }

    @Override
    public int getPrice() { return 0; }

    @Override
    public String getName() {
        return "high_five";
    }

    @Override
    protected String getDescription() {
        return "和玩家击掌互动！播放欢快的音效、撒出爱心和星星粒子，并给玩家短暂的速度加成。"
                + "纯社交互动，不给予任何物品。target 是击掌对象。";
    }

    @Override
    protected void buildParameters(JsonObject properties, JsonArray required) {
        addStringProp(properties, "target", "要击掌的玩家用户名", true, required);
    }

    @Override
    protected ToolResult executeImpl(JsonObject args, String caller) {
        String targetName = getString(args, "target", "");
        Player target = findPlayer(targetName);
        if (target == null) {
            return ToolResult.fail("找不到玩家 " + targetName + "，他可能不在线喵。");
        }

        Location loc = target.getLocation().add(0, 1, 0);

        // 欢快音效
        target.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);
        target.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.2f);

        // 爱心 + 星星粒子
        target.getWorld().spawnParticle(Particle.HEART, loc, 10, 0.5, 0.5, 0.5, 0.1);
        target.getWorld().spawnParticle(Particle.END_ROD, loc, 15, 0.5, 0.5, 0.5, 0.1);

        // 短暂速度加成（3秒，让击掌有"兴奋"感）
        target.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 3 * 20, 0, false, true, true));

        target.sendMessage("§" + plugin.getConfigManager().getAccentColor() + "[击掌] §" + plugin.getConfigManager().getTextColor() + "和你击掌啦！啪叽～");
        return ToolResult.ok("和 " + target.getName() + " 击掌了喵！播放了欢快音效和爱心粒子，并给了3秒速度加成。");
    }
}
