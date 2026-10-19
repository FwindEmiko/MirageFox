package top.miragedge.miragefox.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import top.miragedge.miragefox.MirageFox;
import org.bukkit.entity.Player;

/**
 * 私语术 —— 星玖主动给指定玩家发送私信。
 * 其他玩家看不到这条消息。
 */
public class WhisperPlayerTool extends AbstractTool {

    public WhisperPlayerTool(MirageFox plugin) {
        super(plugin);
    }

    @Override
    public int getPrice() { return 0; }

    @Override
    public String getName() {
        return "whisper_player";
    }

    @Override
    protected String getDescription() {
        return "主动给指定玩家发送一条私信。只有目标玩家能看到这条消息，其他玩家看不到。"
                + "当你想跟某个玩家单独说点什么、或者回复玩家不想让其他人看到的内容时用这个工具。"
                + "target 是接收私信的玩家用户名，message 是私信内容（简短自然，像朋友说话）。";
    }

    @Override
    protected void buildParameters(JsonObject properties, JsonArray required) {
        addStringProp(properties, "target", "接收私信的玩家用户名", true, required);
        addStringProp(properties, "message", "私信内容，简短自然", true, required);
    }

    @Override
    protected ToolResult executeImpl(JsonObject args, String caller) {
        String targetName = getString(args, "target", "");
        String message = getString(args, "message", "");

        if (message.isEmpty()) {
            return ToolResult.fail("消息内容不能为空喵。");
        }

        Player target = findPlayer(targetName);
        if (target == null) {
            return ToolResult.fail("找不到玩家 " + targetName + "，他可能不在线喵。");
        }

        String accent = plugin.getConfigManager().getAccentColor();
        String textColor = plugin.getConfigManager().getTextColor();
        String msg = "§" + accent + "[星玖私信] §" + textColor + message;
        target.sendMessage(msg);

        return ToolResult.ok("已私信给 " + targetName + "，内容: " + message);
    }
}
