package club.sitmc.sitSegment;

import java.util.function.Predicate;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class ItemUtil {
    private final NamespacedKey returnKey;
    private final NamespacedKey restartKey;
    private final NamespacedKey exitKey;
    private final LegacyComponentSerializer serializer = LegacyComponentSerializer.legacyAmpersand();

    public ItemUtil(SITSegment plugin) {
        this.returnKey = new NamespacedKey(plugin, "return_checkpoint");
        this.restartKey = new NamespacedKey(plugin, "restart_run");
        this.exitKey = new NamespacedKey(plugin, "exit_run");
    }

    public NamespacedKey getReturnKey() {
        return returnKey;
    }

    public ItemStack createReturnItem() {
        ItemStack item = new ItemStack(Material.MAGMA_CREAM);
        ItemMeta meta = item.getItemMeta();
                meta.displayName(serializer.deserialize("&6返回上一个记录点"));
        meta.getPersistentDataContainer().set(returnKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createRestartItem() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
                meta.displayName(serializer.deserialize("&e重新开始跑酷"));
        meta.getPersistentDataContainer().set(restartKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createExitItem() {
        ItemStack item = new ItemStack(Material.LAPIS_LAZULI);
        ItemMeta meta = item.getItemMeta();
                meta.displayName(serializer.deserialize("&c退出当前跑酷"));
        meta.getPersistentDataContainer().set(exitKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isReturnItem(ItemStack item) {
        if (item == null || item.getType() != Material.MAGMA_CREAM) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        return meta.getPersistentDataContainer().has(returnKey, PersistentDataType.BYTE);
    }

    public boolean isRestartItem(ItemStack item) {
        if (item == null || item.getType() != Material.NETHER_STAR) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        return meta.getPersistentDataContainer().has(restartKey, PersistentDataType.BYTE);
    }

    public boolean isExitItem(ItemStack item) {
        if (item == null || item.getType() != Material.LAPIS_LAZULI) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        return meta.getPersistentDataContainer().has(exitKey, PersistentDataType.BYTE);
    }

    public boolean hasReturnItem(Player player) {
        return hasItem(player, this::isReturnItem);
    }

    public boolean hasRestartItem(Player player) {
        return hasItem(player, this::isRestartItem);
    }

    public boolean hasExitItem(Player player) {
        return hasItem(player, this::isExitItem);
    }

    public void giveReturnItem(Player player) {
        if (hasReturnItem(player)) {
            return;
        }
        player.getInventory().addItem(createReturnItem());
    }

    public void giveExitItem(Player player) {
        removeLegacyExitBarrier(player);
        ensureItemInHotbar(player.getInventory(), 7, this::isExitItem, createExitItem());
    }

    public void giveRestartItem(Player player) {
        ensureItemInHotbar(player.getInventory(), 8, this::isRestartItem, createRestartItem());
    }

    private boolean hasItem(Player player, Predicate<ItemStack> matcher) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (matcher.test(item)) {
                return true;
            }
        }
        return false;
    }

    private void ensureItemInHotbar(PlayerInventory inventory, int hotbarSlot,
                                    Predicate<ItemStack> matcher, ItemStack targetItem) {
        int itemSlot = findItemSlot(inventory, matcher);
        ItemStack selected = itemSlot >= 0 ? inventory.getItem(itemSlot) : targetItem;

        if (itemSlot == hotbarSlot) {
            return;
        }

        ItemStack slotItem = inventory.getItem(hotbarSlot);
        if (itemSlot >= 0) {
            inventory.setItem(hotbarSlot, selected);
            inventory.setItem(itemSlot, slotItem);
            return;
        }

        if (isEmpty(slotItem)) {
            inventory.setItem(hotbarSlot, selected);
            return;
        }

        int emptySlot = inventory.firstEmpty();
        if (emptySlot == -1) {
            return;
        }

        inventory.setItem(emptySlot, slotItem);
        inventory.setItem(hotbarSlot, selected);
    }

    private void removeLegacyExitBarrier(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() != Material.BARRIER) {
                continue;
            }
            ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                continue;
            }
            if (!meta.getPersistentDataContainer().has(exitKey, PersistentDataType.BYTE)) {
                continue;
            }
            inventory.setItem(i, null);
        }
    }

    private int findItemSlot(PlayerInventory inventory, Predicate<ItemStack> matcher) {
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (matcher.test(contents[i])) {
                return i;
            }
        }
        return -1;
    }

    private boolean isEmpty(ItemStack item) {
        return item == null || item.getType() == Material.AIR;
    }
}
