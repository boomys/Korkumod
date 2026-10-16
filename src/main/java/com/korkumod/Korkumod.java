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

    // Lanetli Tabela İtemi
    public static final Item CURSED_SIGN_ITEM = Registry.register(
            Registries.ITEM,
            Identifier.of(MOD_ID, "cursed_sign"),
            new Item(new Item.Settings().maxCount(1))
    );

    @Override
    public void onInitialize() {
        LOGGER.info("KorkuMod 1.21.5 basariyla baslatildi! Ruhlar uykudan uyanıyor...");

        // Lanetli tabelayı yaratıcı mod / eşya sekmesine ekliyoruz
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).content(entries -> entries.add(CURSED_SIGN_ITEM));
    }
}
