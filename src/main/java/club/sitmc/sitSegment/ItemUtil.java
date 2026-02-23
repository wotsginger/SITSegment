package club.sitmc.sitSegment;

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
    private final LegacyComponentSerializer serializer = LegacyComponentSerializer.legacyAmpersand();

    public ItemUtil(SITSegment plugin) {
        this.returnKey = new NamespacedKey(plugin, "return_checkpoint");
        this.restartKey = new NamespacedKey(plugin, "restart_run");
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

    public boolean hasReturnItem(Player player) {
        PlayerInventory inventory = player.getInventory();
        for (ItemStack item : inventory.getContents()) {
            if (isReturnItem(item)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasRestartItem(Player player) {
        PlayerInventory inventory = player.getInventory();
        for (ItemStack item : inventory.getContents()) {
            if (isRestartItem(item)) {
                return true;
            }
        }
        return false;
    }

    public void giveReturnItem(Player player) {
        if (hasReturnItem(player)) {
            return;
        }
        player.getInventory().addItem(createReturnItem());
    }

    public void giveRestartItem(Player player) {
        PlayerInventory inventory = player.getInventory();
        int hotbarSlot = 8;
        int restartSlot = findRestartItemSlot(inventory);
        ItemStack restartItem = restartSlot >= 0 ? inventory.getItem(restartSlot) : createRestartItem();

        if (restartSlot == hotbarSlot) {
            return;
        }

        ItemStack slotItem = inventory.getItem(hotbarSlot);
        if (restartSlot >= 0) {
            inventory.setItem(hotbarSlot, restartItem);
            inventory.setItem(restartSlot, slotItem);
            return;
        }

        if (isEmpty(slotItem)) {
            inventory.setItem(hotbarSlot, restartItem);
            return;
        }

        int emptySlot = inventory.firstEmpty();
        if (emptySlot == -1) {
            return;
        }
        inventory.setItem(emptySlot, slotItem);
        inventory.setItem(hotbarSlot, restartItem);
    }

    private int findRestartItemSlot(PlayerInventory inventory) {
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isRestartItem(contents[i])) {
                return i;
            }
        }
        return -1;
    }

    private boolean isEmpty(ItemStack item) {
        return item == null || item.getType() == Material.AIR;
    }
}
