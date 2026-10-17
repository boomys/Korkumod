package com.korkumod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.Blocks;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.GameStateChangeS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Random;

public class KorkuMod implements ModInitializer {
    public static final String MOD_ID = "korkumod";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);
    private static final Random RANDOM = new Random();

    // 1. Lanetli Tabela (Cursed Sign) Eşyası
    public static final Item CURSED_SIGN_ITEM = Registry.register(
            Registries.ITEM,
            Identifier.of(MOD_ID, "cursed_sign"),
            new Item(new Item.Settings())
    );

    // Özel Kreatif Sekmesi
    public static final ItemGroup KORKUMOD_GROUP = Registry.register(
            Registries.ITEM_GROUP,
            Identifier.of(MOD_ID, "korkumod_group"),
            FabricItemGroup.builder()
                    .displayName(Text.literal("KorkuMod Sekmesi"))
                    .icon(() -> new ItemStack(CURSED_SIGN_ITEM))
                    .entries((displayContext, entries) -> entries.add(CURSED_SIGN_ITEM))
                    .build()
    );

    private int tickCounter = 0;
    private int nightmareTimer = -1;
    private ServerPlayerEntity nightmareTarget = null;

    @Override
    public void onInitialize() {
        LOGGER.info("KorkuMod 1.21.2 Tum Mekaniklerle Baslatildi!");

        // 🟢 1. İLK GİRİŞTE TABELA VERME & SAHTE KATILIM MESAJI (Fake Join)
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            
            // Envantere Lanetli Tabela ekle
            player.getInventory().insertStack(new ItemStack(CURSED_SIGN_ITEM));
            
            // Sahte Katılım Mesajı
            server.getPlayerManager().broadcast(Text.literal("§e[SYSTEM]: Unknown_Presence joined the game"), false);
        });

        // 💬 2. OYUNCU CHAT YANITI ("Hee çağrımı o mal köpek")
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            sender.getServer().execute(() -> {
                sender.getServer().getPlayerManager().broadcast(
                    Text.literal("§4<Unknown_Presence> §cHee çağrımı o mal köpek"),
                    false
                );
            });
        });

        // 🛌 3. YATAK KABUSU VE KATMANKAYA SAHNESİ
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            BlockPos pos = hitResult.getBlockPos();
            if (!world.isClient && world.getBlockState(pos).getBlock().toString().contains("bed")) {
                if (player instanceof ServerPlayerEntity serverPlayer) {
                    startNightmareSequence(serverPlayer);
                    return ActionResult.SUCCESS;
                }
            }
            return ActionResult.PASS;
        });

        // 🩸 4. LANETLİ TABELA YERE KONDUĞUNDA KANLI SİS VE RİTÜEL
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (!world.isClient && stack.isOf(CURSED_SIGN_ITEM)) {
                ServerWorld serverWorld = (ServerWorld) world;

                // Fırtına ve Kızıl Gökyüzü Yağmuru
                serverWorld.setWeather(0, 12000, true, true);

                // Dinamik İngilizce Mesaj
                player.sendMessage(Text.literal("§4I see you, " + player.getName().getString()), false);

                // Karga ve Çılbır Sesleri
                serverWorld.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.AMBIENT, 1.0f, 0.5f);
            }
            return ActionResult.PASS;
        });

        // 🔄 5. DİNAMİK RASTGELE KORKU ETKİNLİKLERİ (Server Tick Loop)
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickCounter++;

            // Yatak Kabusu Zamanlayıcısı
            if (nightmareTimer > 0) {
                nightmareTimer--;
                if (nightmareTimer == 0 && nightmareTarget != null) {
                    executeNightmareJumpScare(nightmareTarget);
                }
            }

            // Her 10 saniyede bir rastgele olay
            if (tickCounter >= 200) {
                tickCounter = 0;

                for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                    ServerWorld world = player.getServerWorld();
                    BlockPos pPos = player.getBlockPos();

                    // Kanlı Ayak İzleri Parçacığı (Redstone Dust Parçacıkları)
                    world.spawnParticles(ParticleTypes.DUST_PLUME, pPos.getX(), pPos.getY() + 0.1, pPos.getZ(), 5, 0.2, 0.0, 0.2, 0.0);

                    int randomEffect = RANDOM.nextInt(9);
                    switch (randomEffect) {
                        case 0: // Taklit Adım Sesleri / Kulağının dibinde nefes
                            world.playSound(null, pPos, SoundEvents.ENTITY_PLAYER_BREATH, SoundCategory.PLAYERS, 1.5f, 0.2f);
                            break;
                        case 1: // Cam Kırılması ve Nefes Darlığı
                            world.playSound(null, pPos, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.BLOCKS, 1.0f, 0.8f);
                            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 100, 2));
                            player.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 60, 1));
                            break;
                        case 2: // Kırılan Kapı/Sandık Sesleri
                            world.playSound(null, pPos, SoundEvents.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, SoundCategory.BLOCKS, 1.0f, 0.5f);
                            break;
                        case 3: // Bozuk Sohbet / Telsiz Cızırtısı
                            player.sendMessage(Text.literal("§c§kHELP ME§r §4I AM BEHIND YOU"), false);
                            world.playSound(null, pPos, SoundEvents.BLOCK_REDSTONE_TORCH_BURNOUT, SoundCategory.BLOCKS, 1.0f, 0.1f);
                            break;
                        case 4: // Sönen Meşaleler
                            BlockPos torchPos = pPos.add(RANDOM.nextInt(5) - 2, 0, RANDOM.nextInt(5) - 2);
                            if (world.getBlockState(torchPos).isOf(Blocks.TORCH)) {
                                world.setBlockState(torchPos, Blocks.AIR.getDefaultState());
                                world.playSound(null, torchPos, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 1.0f, 1.0f);
                            }
                            break;
                        case 5: // Yapraksız / Ağaç Arkasındaki Stalker Ölü Ağacı
                            BlockPos treePos = pPos.add(6, 0, 6);
                            world.setBlockState(treePos, Blocks.OAK_LOG.getDefaultState());
                            world.setBlockState(treePos.up(), Blocks.OAK_LOG.getDefaultState());
                            player.sendMessage(Text.literal("§4Bir şey ağaçların arkasından izliyor..."), true);
                            break;
                        case 6: // Sahte Ölüm Ekranı Kırılma Efekti
                            player.networkHandler.sendPacket(new GameStateChangeS2CPacket(GameStateChangeS2CPacket.DEMO_MESSAGE_SHOWN, 0));
                            break;
                        case 7: // Sahte Envanter Açılma Sesi
                            world.playSound(null, pPos, SoundEvents.UI_BUTTON_CLICK.value(), SoundCategory.PLAYERS, 1.0f, 0.2f);
                            break;
                        case 8: // Kendiliğinden Yanan/Sönen Fırın Sesi
                            world.playSound(null, pPos, SoundEvents.BLOCK_FURNACE_FIRE_CRACKLE, SoundCategory.BLOCKS, 1.0f, 1.0f);
                            break;
                    }
                }
            }
        });
    }

    // 🛌 Yatak Kabusu Dizisi (Katmankaya Sahnesi)
    private void startNightmareSequence(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        BlockPos nightmarePos = new BlockPos(0, 200, 0);

        // 1. Düz çim alan ve katmankaya oluştur
        for (int x = -5; x <= 5; x++) {
            for (int z = -5; z <= 5; z++) {
                world.setBlockState(nightmarePos.add(x, -1, z), Blocks.BEDROCK.getDefaultState());
                world.setBlockState(nightmarePos.add(x, 0, z), Blocks.GRASS_BLOCK.getDefaultState());
            }
        }

        // Oyuncuyu ışınla ve dondur
        player.teleport(world, 0.5, 201.0, 0.5, 0, 0);
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 200, 255)); 

        // 10 Saniyelik Bakışma Süresi
        nightmareTimer = 200; 
        nightmareTarget = player;
        
        player.sendMessage(Text.literal("§4..."), true);
    }

    // Kabus Sonu: Yağmur, RUNN! Yazıları ve Çığlık
    private void executeNightmareJumpScare(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        
        // Şiddetli Yağmur ve Şimşek
        world.setWeather(0, 1000, true, true);
        
        // Chat'te Kırmızı RUNN!
        player.sendMessage(Text.literal("§4§lRUNN! RUNN! RUNN!"), false);
        
        // Korkunç Çığlık Sesi
        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.HOSTILE, 2.0f, 0.1f);
        
        // Körlük Verip Dondurmayı Kaldır
        player.removeStatusEffect(StatusEffects.SLOWNESS);
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 100, 1));
    }
}
