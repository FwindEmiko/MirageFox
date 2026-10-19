package top.miragedge.miragefox.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import top.miragedge.miragefox.MirageFox;
import org.bukkit.entity.Player;

/**
 * 饱腹术 —— 恢复玩家饥饿值
 */
public class FeedPlayerTool extends AbstractTool {

    public FeedPlayerTool(MirageFox plugin) {
        super(plugin);
    }

    @Override
    public int getPrice() { return 150; }

    @Override
    public int getRateLimitSeconds() { return 120; }

    @Override
    public int getDailyLimit() { return 10; }

    @Override
    public String getName() {
        return "feed_player";
    }

    @Override
    protected String getDescription() {
        return "恢复目标玩家的饥饿值和饱和度。会完全恢复饥饿值，并给予额外饱和度。"
                + "如果玩家已经饱了，会提示不需要。";
    }

    @Override
    protected void buildParameters(JsonObject properties, JsonArray required) {
        addStringProp(properties, "target", "目标玩家用户名", true, required);
    }

    @Override
    protected ToolResult executeImpl(JsonObject args, String caller) {
        String targetName = getString(args, "target", "");

        Player target = findPlayer(targetName);
        if (target == null) {
            return ToolResult.fail("找不到玩家 " + targetName + "，他可能不在线喵。");
        }

        if (target.getFoodLevel() >= 20 && target.getSaturation() >= 10) {
            return ToolResult.fail(target.getName() + " 已经很饱了喵，再吃就撑了～");
        }

        target.setFoodLevel(20);
        target.setSaturation(10);
        target.sendMessage(plugin.getConfigManager().getChatTag() + "让你吃饱了喵～");

        return ToolResult.ok("恢复了 " + target.getName() + " 的饥饿值。");
    }
}
