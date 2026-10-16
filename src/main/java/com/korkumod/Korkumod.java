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

    // Lanetli Tabela İtemi (Fabric 1.21.5 Identifier kullanımı)
    public static final Item CURSED_SIGN_ITEM = Registry.register(
            Registries.ITEM,
            Identifier.of(MOD_ID, "cursed_sign"),
            new Item(new Item.Settings().maxCount(1))
    );

    @Override
    public void onInitialize() {
        LOGGER.info("KorkuMod 1.21.5 yukleniyor... Kabus basliyor.");

        // Tabelayı Fonksiyonel Eşyalar sekmesine ekle
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).content(entries -> entries.add(CURSED_SIGN_ITEM));

        // Chat Dinleyicisi (Varlığın cevabı ve Steve komutları)
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            String playerText = message.getContent().getString().toLowerCase();
            
            // Eğer oyuncu Steve'i çağırırsa
            if (playerText.contains("steve come here")) {
                sender.sendMessage(Text.literal("§e[Steve]: Geliyorum kanka, beni bekle!"), false);
                // Burada Steve'i oyuncunun yanına ışınlama mantığı tetiklenecek
            } else {
                // Diğer her yazıda stalker varlığın efsanevi cevabı:
                sender.getServer().getPlayerManager().broadcast(
                        Text.literal("§4[Varlık]: Hee çağrımı o mal köpek"), 
                        false
                );
            }
        });
    }
}
