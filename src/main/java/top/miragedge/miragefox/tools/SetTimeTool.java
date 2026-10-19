package top.miragedge.miragefox.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import top.miragedge.miragefox.MirageFox;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * 昼夜术 —— 改变玩家所在世界的时间。
 */
public class SetTimeTool extends AbstractTool {

    private static final String[] TIMES = {"day", "noon", "night", "midnight"};

    public SetTimeTool(MirageFox plugin) {
        super(plugin);
    }

    @Override
    public int getPrice() { return 300; }

    @Override
    public int getRateLimitSeconds() { return 600; }

    @Override
    public int getDailyLimit() { return 3; }

    @Override
    public String getName() {
        return "set_time";
    }

    @Override
    protected String getDescription() {
        return "改变目标玩家所在世界的时间。可选: day（白天）, noon（正午）, "
                + "night（夜晚）, midnight（午夜）。target 是玩家名，只影响该玩家所在的世界。";
    }

    @Override
    protected void buildParameters(JsonObject properties, JsonArray required) {
        addStringProp(properties, "target", "目标玩家用户名", true, required);
        addEnumProp(properties, "time", "时间", true, required, TIMES);
    }

    @Override
    protected ToolResult executeImpl(JsonObject args, String caller) {
        String targetName = getString(args, "target", "");
        String time = getString(args, "time", "day").toLowerCase();

        Player target = findPlayer(targetName);
        if (target == null) {
            return ToolResult.fail("找不到玩家 " + targetName + "，他可能不在线喵。");
        }

        long timeValue;
        String timeName;
        switch (time) {
            case "day":
                timeValue = 1000; timeName = "白天"; break;
            case "noon":
                timeValue = 6000; timeName = "正午"; break;
            case "night":
                timeValue = 13000; timeName = "夜晚"; break;
            case "midnight":
                timeValue = 18000; timeName = "午夜"; break;
            default:
                return ToolResult.fail("时间类型无效喵。可选: day, noon, night, midnight。");
        }

        World world = target.getWorld();
        world.setTime(timeValue);

        target.sendMessage("§" + plugin.getConfigManager().getAccentColor()
                + "[昼夜术] §" + plugin.getConfigManager().getTextColor()
                + "星玖把时间变成了" + timeName + "喵～");

        return ToolResult.ok("已把 " + target.getName() + " 所在的世界(" + world.getName() + ")时间改为" + timeName + "。");
    }
}
