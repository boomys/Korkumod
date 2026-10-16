package com.korkumod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
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
        LOGGER.info("KorkuMod yukleniyor...");

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).content(entries -> entries.add(CURSED_SIGN_ITEM));

        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            String playerText = message.getContent().getString().toLowerCase();
            
            if (playerText.contains("steve come here")) {
                sender.sendMessage(Text.literal("§e[Steve]: Geliyorum kanka!"), false);
            } else {
                sender.getServer().getPlayerManager().broadcast(
                        Text.literal("§4[Varlık]: Hee çağrımı o mal köpek"), 
                        false
                );
            }
        });
    }
}
