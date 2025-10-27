package org.bukkit.inventory;

/**
 * Minimal item stack stub.
 */
public class ItemStack {
    private final Object type;

    public ItemStack() {
        this("stone");
    }

    public ItemStack(final Object type) {
        this.type = type;
    }

    public Object getType() {
        return type;
    }
}
