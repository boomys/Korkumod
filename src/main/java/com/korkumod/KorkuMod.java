package com.korkumod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class KorkuMod implements ModInitializer {
    public static final String MOD_ID = "korkumod";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public static final Item CURSED_SIGN_ITEM = Registry.register(
            Registries.ITEM,
            Identifier.of(MOD_ID, "cursed_sign"),
            new Item(new Item.Settings())
    );

    @Override
    public void onInitialize() {
        LOGGER.info("KorkuMod 1.21.5 basariyla baslatildi.");
        
        // Fabric 1.21.5 için doğru item grup ekleme yöntemi (.accept kullanıyoruz)
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(content -> {
            content.add(CURSED_SIGN_ITEM);
        });
    }
}
