package top.miragedge.miragefox.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import top.miragedge.miragefox.MirageFox;
import org.bukkit.entity.Player;

/**
 * 落雷术 —— 召唤闪电劈中玩家（默认无伤害，视觉效果）
 */
public class StrikeLightningTool extends AbstractTool {

    public StrikeLightningTool(MirageFox plugin) {
        super(plugin);
    }

    @Override
    public int getPrice() { return 20; }

    @Override
    public String getName() {
        return "strike_lightning";
    }

    @Override
    protected String getDescription() {
        return "召唤一道闪电劈中目标玩家。视觉效果非常震撼，默认不造成伤害（仅视觉效果）。"
                + "可以用于制造戏剧性效果、恶作剧、或者庆祝。参数 target 是玩家名，damage 可选是否造成伤害。";
    }

    @Override
    protected void buildParameters(JsonObject properties, JsonArray required) {
        addStringProp(properties, "target", "要劈的玩家用户名", true, required);
        addBooleanProp(properties, "damage", "是否尝试造成伤害（实际是否生效取决于服务器配置，默认不造成伤害）", false, required);
    }

    @Override
    protected ToolResult executeImpl(JsonObject args, String caller) {
        String targetName = getString(args, "target", "");
        boolean damage = getBoolean(args, "damage", false);

        Player target = findPlayer(targetName);
        if (target == null) {
            return ToolResult.fail("找不到玩家 " + targetName + "，他可能不在线喵。");
        }

        if (damage && plugin.getConfigManager().isLightningDamage()) {
            target.getWorld().strikeLightning(target.getLocation());
            return ToolResult.ok("一道闪电劈中了 " + target.getName() + " 喵！");
        } else {
            // 无伤害闪电
            target.getWorld().strikeLightningEffect(target.getLocation());
            return ToolResult.ok("一道炫酷的闪电特效落在 " + target.getName() + " 头顶喵！（没有伤害，放心~）");
        }
    }
}
