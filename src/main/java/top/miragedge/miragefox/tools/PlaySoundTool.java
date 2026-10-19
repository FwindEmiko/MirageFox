package top.miragedge.miragefox.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import top.miragedge.miragefox.MirageFox;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * 灵音术 —— 在玩家位置播放音效
 */
public class PlaySoundTool extends AbstractTool {

    private static final String[] AVAILABLE_SOUNDS = {
            "entity_experience_orb_pickup", "entity_player_levelup",
            "entity_firework_rocket_launch", "entity_cat_ambient",
            "entity_fox_ambient", "entity_villager_yes",
            "entity_wolf_howl", "entity_enderman_teleport",
            "entity_lightning_bolt_thunder", "entity_lightning_bolt_impact",
            "block_note_block_bell", "entity_arrow_hit_player",
            "entity_bat_takeoff", "entity_parrot_ambient",
            "music_disc_strad", "ui_toast_in",
            "block_amethyst_block_chime"
    };

    public PlaySoundTool(MirageFox plugin) {
        super(plugin);
    }

    @Override
    public int getPrice() { return 0; }

    @Override
    public String getName() {
        return "play_sound";
    }

    @Override
    protected String getDescription() {
        return "在目标玩家位置播放一个音效。可以用于制造氛围、恶作剧或庆祝。"
                        + "可用音效: " + String.join(", ", AVAILABLE_SOUNDS) + "。";
    }

    @Override
    protected void buildParameters(JsonObject properties, JsonArray required) {
        addStringProp(properties, "target", "目标玩家用户名", true, required);
        addEnumProp(properties, "sound", "音效名称", true, required, AVAILABLE_SOUNDS);
        addNumberProp(properties, "volume", "音量，0.0-2.0（默认1.0）", false, required);
        addNumberProp(properties, "pitch", "音调，0.5-2.0（默认1.0）", false, required);
    }

    @Override
    protected ToolResult executeImpl(JsonObject args, String caller) {
        String targetName = getString(args, "target", "");
        String soundName = getString(args, "sound", "entity_experience_orb_pickup");
        float volume = (float) Math.max(0.0, Math.min(2.0, getDouble(args, "volume", 1.0)));
        float pitch = (float) Math.max(0.5, Math.min(2.0, getDouble(args, "pitch", 1.0)));

        Player target = findPlayer(targetName);
        if (target == null) {
            return ToolResult.fail("找不到玩家 " + targetName + "，他可能不在线喵。");
        }

        Sound sound = null;
        try {
            sound = Sound.valueOf(soundName.toUpperCase());
        } catch (IllegalArgumentException e) {
            try {
                String key = soundName.replace("entity_", "ENTITY_")
                        .replace("block_", "BLOCK_")
                        .replace("music_", "MUSIC_")
                        .replace("ui_", "UI_")
                        .replace("item_", "ITEM_")
                        .toUpperCase();
                sound = Sound.valueOf(key);
            } catch (IllegalArgumentException e2) {
                return ToolResult.fail("音效 " + soundName + " 不存在喵。");
            }
        }

        Location loc = target.getLocation();
        target.playSound(loc, sound, volume, pitch);

        target.sendMessage(plugin.getConfigManager().getChatTag() + "在你耳边响起了一段奇妙的声音喵～");
        return ToolResult.ok("在 " + target.getName() + " 位置播放了音效 " + soundName + "（音量=" + volume + "，音调=" + pitch + "）。");
    }
}
