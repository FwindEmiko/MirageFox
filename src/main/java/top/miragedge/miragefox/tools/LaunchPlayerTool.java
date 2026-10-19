package top.miragedge.miragefox.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import top.miragedge.miragefox.MirageFox;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * 旋风狐 —— 把玩家发射到空中（恶作剧/好玩，带缓降保护）。
 */
public class LaunchPlayerTool extends AbstractTool {

    public LaunchPlayerTool(MirageFox plugin) {
        super(plugin);
    }

    @Override
    public int getPrice() { return 20; }

    @Override
    public String getName() {
        return "launch_player";
    }

    @Override
    protected String getDescription() {
        return "旋风狐——把目标玩家发射到空中！纯粹恶作剧的狐族法术，不会造成伤害。"
                + "power 参数控制力度（1-5，默认3），1=轻轻弹起，5=飞得很高。";
    }

    @Override
    protected void buildParameters(JsonObject properties, JsonArray required) {
        addStringProp(properties, "target", "要发射的玩家用户名", true, required);
        addIntProp(properties, "power", "发射力度，1-5，数字越大飞得越高（默认3）", false, required);
    }

    @Override
    protected ToolResult executeImpl(JsonObject args, String caller) {
        String targetName = getString(args, "target", "");
        int power = getInt(args, "power", 3);
        int maxPower = plugin.getConfigManager().getLaunchMaxPower();
        power = Math.max(1, Math.min(maxPower, power));

        Player target = findPlayer(targetName);
        if (target == null) {
            return ToolResult.fail("找不到玩家 " + targetName + "，他可能不在线喵。");
        }

        double velocityY = 0.5 + power * 0.4;
        double velocityX = (Math.random() - 0.5) * 0.2 * power;
        double velocityZ = (Math.random() - 0.5) * 0.2 * power;
        target.setVelocity(new Vector(velocityX, velocityY, velocityZ));

        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING,
                6 * 20, 0, false, true, true));

        target.sendMessage("§" + plugin.getConfigManager().getAccentColor()
                + "[旋风狐] §" + plugin.getConfigManager().getTextColor()
                + "把你卷到天上啦喵～（有缓降保护，摔不疼）");

        return ToolResult.ok("用旋风狐把 " + target.getName() + " 发射到空中了喵！力度=" + power + "（有缓降效果，不会摔伤）。");
    }
}
