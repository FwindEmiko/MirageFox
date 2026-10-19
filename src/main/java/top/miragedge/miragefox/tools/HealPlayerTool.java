package top.miragedge.miragefox.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import top.miragedge.miragefox.MirageFox;
import org.bukkit.entity.Player;

/**
 * 治愈术 —— 恢复玩家生命值
 */
public class HealPlayerTool extends AbstractTool {

    public HealPlayerTool(MirageFox plugin) {
        super(plugin);
    }

    @Override
    public int getPrice() { return 200; }

    @Override
    public int getRateLimitSeconds() { return 120; }

    @Override
    public int getDailyLimit() { return 10; }

    @Override
    public String getName() {
        return "heal_player";
    }

    @Override
    protected String getDescription() {
        return "恢复目标玩家的生命值。amount 是恢复量（默认满血），可以指定具体数值（1-20颗心）。"
                + "如果玩家已满血，会提示不需要治疗。";
    }

    @Override
    protected void buildParameters(JsonObject properties, JsonArray required) {
        addStringProp(properties, "target", "目标玩家用户名", true, required);
        addIntProp(properties, "amount", "恢复的生命值（心数），1-20，默认0=满血", false, required);
    }

    @Override
    protected ToolResult executeImpl(JsonObject args, String caller) {
        String targetName = getString(args, "target", "");
        int amount = getInt(args, "amount", 0);

        Player target = findPlayer(targetName);
        if (target == null) {
            return ToolResult.fail("找不到玩家 " + targetName + "，他可能不在线喵。");
        }

        if (target.getHealth() >= target.getMaxHealth() && amount == 0) {
            return ToolResult.fail(target.getName() + " 已经满血了喵，不需要治疗～");
        }

        if (amount > 0) {
            double healAmount = Math.min(amount, target.getMaxHealth() - target.getHealth());
            target.setHealth(target.getHealth() + healAmount);
            target.sendMessage(plugin.getConfigManager().getChatTag() + "治愈了你 " + (int)healAmount + " 点生命喵～");
            return ToolResult.ok("恢复了 " + target.getName() + " " + (int)healAmount + " 点生命。");
        } else {
            target.setHealth(target.getMaxHealth());
            target.sendMessage(plugin.getConfigManager().getChatTag() + "帮你满血复活了喵～");
            return ToolResult.ok("完全恢复了 " + target.getName() + " 的生命值。");
        }
    }
}
