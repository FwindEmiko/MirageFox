package top.miragedge.miragefox.economy;

import net.milkbowl.vault.economy.Economy;
import top.miragedge.miragefox.MirageFox;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * 经济管理器 —— 对接 Vault 实现货币扣款/退款。
 * 插件启动时自动检测 Vault 是否可用。
 *
 * 线程安全：Vault 接口不承诺线程安全，所有调用方必须保证在主线程调用
 * （插件内已通过 ToolExecutor.runOnMain 统一保证）。
 */
public class EconomyManager {

    private final MirageFox plugin;
    private Economy economy;
    private boolean enabled = false;

    public EconomyManager(MirageFox plugin) {
        this.plugin = plugin;
        setup();
    }

    private void setup() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().info("Vault 未安装，经济功能已禁用");
            return;
        }
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            plugin.getLogger().warning("Vault 已安装但未找到经济实现");
            return;
        }
        this.economy = rsp.getProvider();
        this.enabled = true;
        plugin.getLogger().info("经济系统已对接: " + economy.getName());
    }

    public boolean isEnabled() { return enabled; }

    /** 检查玩家是否有足够余额（经济不可用时放行） */
    public boolean hasBalance(Player player, double amount) {
        if (!enabled || player == null) return true;
        return economy.has(player, amount);
    }

    /** 扣款，返回是否成功（经济不可用时视为成功） */
    public boolean withdraw(Player player, double amount) {
        if (!enabled || player == null) return true;
        if (amount <= 0) return true;
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    /** 退款/存款，返回是否成功 */
    public boolean deposit(Player player, double amount) {
        if (!enabled || player == null) return false;
        if (amount <= 0) return false;
        return economy.depositPlayer(player, amount).transactionSuccess();
    }

    /** 获取余额 */
    public double getBalance(Player player) {
        if (!enabled || player == null) return 0;
        return economy.getBalance(player);
    }

    /** 获取货币名 */
    public String getCurrencyName() {
        if (!enabled || economy == null) return plugin.getConfigManager().getCurrencyName();
        return economy.currencyNamePlural();
    }
}
