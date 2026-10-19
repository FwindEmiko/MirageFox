package top.miragedge.miragefox.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import top.miragedge.miragefox.MirageFox;
import org.bukkit.entity.Player;

/**
 * 天启术 —— 向玩家发送标题文字
 */
public class SendTitleTool extends AbstractTool {

    public SendTitleTool(MirageFox plugin) {
        super(plugin);
    }

    @Override
    public int getPrice() { return 10; }

    @Override
    public String getName() {
        return "send_title";
    }

    @Override
    protected String getDescription() {
        return "向目标玩家发送屏幕中央的大标题文字，可以用于宣告、庆祝或戏剧性效果。"
                + "title 是主标题（大字），subtitle 是副标题（小字，可选）。";
    }

    @Override
    protected void buildParameters(JsonObject properties, JsonArray required) {
        addStringProp(properties, "target", "目标玩家用户名", true, required);
        addStringProp(properties, "title", "标题文字（主标题，支持 Minecraft § 颜色代码）", true, required);
        addStringProp(properties, "subtitle", "副标题文字（可选，支持 Minecraft § 颜色代码）", false, required);
    }

    @Override
    protected ToolResult executeImpl(JsonObject args, String caller) {
        String targetName = getString(args, "target", "");
        String title = truncateSafe(getString(args, "title", "喵~"), 50);
        String subtitle = truncateSafe(getString(args, "subtitle", ""), 80);

        Player target = findPlayer(targetName);
        if (target == null) {
            return ToolResult.fail("找不到玩家 " + targetName + "，他可能不在线喵。");
        }

        int fadeIn = 10;
        int stay = 70;
        int fadeOut = 20;

        if (subtitle.isEmpty()) {
            target.sendTitle(title, null, fadeIn, stay, fadeOut);
        } else {
            target.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
        }

        return ToolResult.ok("向 " + target.getName() + " 发送了标题: " + title + (subtitle.isEmpty() ? "" : " / " + subtitle));
    }

    /** 安全截断：不切断 § 颜色代码，截断后去掉悬挂的 § */
    private String truncateSafe(String text, int maxLen) {
        if (text == null || text.length() <= maxLen) return (text == null) ? "" : text;
        String cut = text.substring(0, maxLen);
        if (cut.endsWith("§")) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut;
    }
}
