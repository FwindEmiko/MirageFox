package top.miragedge.miragefox.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import top.miragedge.miragefox.MirageFox;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * 灵视术 —— 查看在线玩家列表
 */
public class ListPlayersTool extends AbstractTool {

    public ListPlayersTool(MirageFox plugin) {
        super(plugin);
    }

    @Override
    public int getPrice() { return 0; }

    @Override
    public String getName() {
        return "list_players";
    }

    @Override
    protected String getDescription() {
        return "查看当前在线的所有玩家列表。返回玩家名字列表。";
    }

    @Override
    protected void buildParameters(JsonObject properties, JsonArray required) {
    }

    @Override
    protected ToolResult executeImpl(JsonObject args, String caller) {
        Player[] online = Bukkit.getOnlinePlayers().toArray(new Player[0]);
        if (online.length == 0) {
            return ToolResult.ok("现在没有玩家在线喵，好寂寞……");
        }

        StringBuilder result = new StringBuilder("当前在线 ");
        result.append(online.length).append(" 位玩家: ");
        for (int i = 0; i < online.length; i++) {
            if (i > 0) result.append(", ");
            result.append(online[i].getName());
        }
        return ToolResult.ok(result.toString());
    }
}
