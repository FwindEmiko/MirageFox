package top.miragedge.miragefox.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import top.miragedge.miragefox.MirageFox;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * 天象术 —— 改变玩家所在世界的天气。
 */
public class SetWeatherTool extends AbstractTool {

    private static final String[] WEATHERS = {"clear", "rain", "thunder"};

    public SetWeatherTool(MirageFox plugin) {
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
        return "set_weather";
    }

    @Override
    protected String getDescription() {
        return "改变目标玩家所在世界的天气。可选: clear（晴天）, rain（雨天）, thunder（雷暴）。"
                + "target 是玩家名，只影响该玩家所在的世界。";
    }

    @Override
    protected void buildParameters(JsonObject properties, JsonArray required) {
        addStringProp(properties, "target", "目标玩家用户名", true, required);
        addEnumProp(properties, "weather", "天气类型", true, required, WEATHERS);
    }

    @Override
    protected ToolResult executeImpl(JsonObject args, String caller) {
        String targetName = getString(args, "target", "");
        String weather = getString(args, "weather", "clear").toLowerCase();

        Player target = findPlayer(targetName);
        if (target == null) {
            return ToolResult.fail("找不到玩家 " + targetName + "，他可能不在线喵。");
        }

        World world = target.getWorld();
        String weatherName;
        switch (weather) {
            case "clear":
                world.setStorm(false);
                world.setThundering(false);
                weatherName = "晴天"; break;
            case "rain":
                world.setStorm(true);
                world.setThundering(false);
                weatherName = "雨天"; break;
            case "thunder":
                world.setStorm(true);
                world.setThundering(true);
                weatherName = "雷暴"; break;
            default:
                return ToolResult.fail("天气类型无效喵。可选: clear, rain, thunder。");
        }

        target.sendMessage("§" + plugin.getConfigManager().getAccentColor()
                + "[天象术] §" + plugin.getConfigManager().getTextColor()
                + "星玖把天气变成了" + weatherName + "喵～");

        return ToolResult.ok("已把 " + target.getName() + " 所在的世界(" + world.getName() + ")天气改为" + weatherName + "。");
    }
}
