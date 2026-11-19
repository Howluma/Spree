package dev.howluma.spreecore;

import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import java.util.List;

/** Assigns client-side model selectors without replacing vanilla item_model IDs.
 * Without the resource pack a diamond sword stays a diamond sword, etc. */
public final class ModelData {
    private final SpreeCorePlugin plugin;
    ModelData(SpreeCorePlugin plugin) { this.plugin = plugin; }

    public boolean legendary(ItemMeta meta, LegendaryType type) { return apply(meta, type.id, 721001 + type.ordinal()); }
    public boolean token(ItemMeta meta) { return apply(meta, "token", 721050); }
    public int id(String name, int fallback) {
        return plugin.getConfig().getInt("resource-pack.models." + name, fallback);
    }
    private boolean apply(ItemMeta meta, String name, int fallback) {
        CustomModelDataComponent component = meta.getCustomModelDataComponent();
        List<Float> floats = component.getFloats();
        float target = id(name, fallback);
        if (!plugin.getConfig().getBoolean("resource-pack.enabled", true)) {
            // Only remove a value if it belongs to our managed ID range. Never strip other model data.
            if (floats.size() == 1 && floats.get(0) >= 721000 && floats.get(0) <= 721999) {
                component.setFloats(List.of());
                meta.setCustomModelDataComponent(component);
                return true;
            }
            return false;
        }
        if (floats.size() == 1 && floats.get(0) == target) return false;
        component.setFloats(List.of(target));
        meta.setCustomModelDataComponent(component);
        return true;
    }
}
