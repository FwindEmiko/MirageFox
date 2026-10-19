package top.miragedge.miragefox.listener;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import top.miragedge.miragefox.MirageFox;
import top.miragedge.miragefox.tools.SummonMobTool;

/**
 * 宠物清理监听器 —— 被标记为 MirageFox 宠物的生物死亡时，
 * 通知 SummonMobTool 减计数，防止宠物计数永久残留。
 */
public class PetListener implements Listener {

    private final MirageFox plugin;

    public PetListener(MirageFox plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity == null) return;

        // 检查是否有 PDC 标记（通过 SummonMobTool 工具注册时的 key）
        String owner = entity.getPersistentDataContainer()
                .get(SummonMobTool.getPetKey(), org.bukkit.persistence.PersistentDataType.STRING);
        if (owner == null || owner.isEmpty()) return;

        // 查找已注册的 SummonMobTool 实例
        var tool = plugin.getToolRegistry().getTool("summon_pet");
        if (tool instanceof SummonMobTool) {
            ((SummonMobTool) tool).onPetRemoved(entity);
        }
    }
}