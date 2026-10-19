package top.miragedge.miragefox.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import top.miragedge.miragefox.MirageFox;
import org.bukkit.entity.Player;

/**
 * 灵鉴术 —— 查看玩家信息（生命/饥饿/经验），不返回坐标防追踪。
 */
public class PlayerInfoTool extends AbstractTool {

    public PlayerInfoTool(MirageFox plugin) {
        super(plugin);
    }

    @Override
    public int getPrice() { return 0; }

    @Override
    public String getName() {
        return "player_info";
    }

    @Override
    protected String getDescription() {
        return "查看目标玩家的状态信息，包括生命值、饥饿值、经验等级等。"
                + "用这个工具可以了解玩家状态。注意：不显示玩家坐标，保护隐私。";
    }

    @Override
    protected void buildParameters(JsonObject properties, JsonArray required) {
        addStringProp(properties, "target", "要查询的玩家用户名", true, required);
    }

    @Override
    protected ToolResult executeImpl(JsonObject args, String caller) {
        String targetName = getString(args, "target", "");

        Player target = findPlayer(targetName);
        if (target == null) {
            return ToolResult.fail("找不到玩家 " + targetName + "，他可能不在线喵。");
        }

        int health = (int) Math.round(target.getHealth());
        int maxHealth = (int) Math.round(target.getMaxHealth());
        int food = target.getFoodLevel();
        int exp = target.getLevel();

        return ToolResult.ok("玩家 " + target.getName() + " 的状态喵：\n"
                + " 生命: " + health + "/" + maxHealth + "\n"
                + " 饥饿: " + food + "/20\n"
                + " 经验等级: " + exp);
    }
}
