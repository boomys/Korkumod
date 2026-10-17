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

    // Lanetli Tabela Eşyası
    public static final Item CURSED_SIGN_ITEM = Registry.register(
            Registries.ITEM,
            Identifier.of(MOD_ID, "cursed_sign"),
            new Item(new Item.Settings().maxCount(1))
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
        LOGGER.info("KorkuMod 1.21.2 Kararli Versiyon Baslatildi.");

        // Oyuna girince eşyayı garanti veren güvenli mekanizma
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            server.execute(() -> {
                player.getInventory().insertStack(new ItemStack(CURSED_SIGN_ITEM));
            });
            server.getPlayerManager().broadcast(Text.literal("§e[SYSTEM]: Unknown_Presence joined the game"), false);
        });

        // Chat Yanıt Sistemi
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            sender.getServer().execute(() -> {
                sender.getServer().getPlayerManager().broadcast(
                    Text.literal("§4<Unknown_Presence> §cHee çağrımı o mal köpek"),
                    false
                );
            });
        });

        // Yatak Kabusu
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient) return ActionResult.PASS;
            BlockPos pos = hitResult.getBlockPos();
            if (world.getBlockState(pos).getBlock().toString().contains("bed")) {
                if (player instanceof ServerPlayerEntity serverPlayer) {
                    startNightmareSequence(serverPlayer);
                    return ActionResult.SUCCESS;
                }
            }
            return ActionResult.PASS;
        });

        // Lanetli Tabela Etkileşimi
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient) return ActionResult.PASS;
            ItemStack stack = player.getStackInHand(hand);
            if (stack.isOf(CURSED_SIGN_ITEM)) {
                ServerWorld serverWorld = (ServerWorld) world;
                serverWorld.setWeather(0, 12000, true, true);
                player.sendMessage(Text.literal("§4I see you, " + player.getName().getString()), false);
                serverWorld.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.AMBIENT, 1.0f, 0.5f);
            }
            return ActionResult.PASS;
        });

        // Tick Döngüsü ve Olaylar
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickCounter++;

            if (nightmareTimer > 0) {
                nightmareTimer--;
                if (nightmareTimer == 0 && nightmareTarget != null) {
                    executeNightmareJumpScare(nightmareTarget);
                }
            }

            if (tickCounter >= 200) {
                tickCounter = 0;

                for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                    ServerWorld world = player.getServerWorld();
                    BlockPos pPos = player.getBlockPos();

                    world.spawnParticles(ParticleTypes.DUST_PLUME, pPos.getX(), pPos.getY() + 0.1, pPos.getZ(), 5, 0.2, 0.0, 0.2, 0.0);

                    int randomEffect = RANDOM.nextInt(8);
                    switch (randomEffect) {
                        case 0:
                            world.playSound(null, pPos, SoundEvents.ENTITY_PLAYER_BREATH, SoundCategory.PLAYERS, 1.5f, 0.2f);
                            break;
                        case 1:
                            world.playSound(null, pPos, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.BLOCKS, 1.0f, 0.8f);
                            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 100, 2));
                            player.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 60, 1));
                            break;
                        case 2:
                            world.playSound(null, pPos, SoundEvents.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, SoundCategory.BLOCKS, 1.0f, 0.5f);
                            break;
                        case 3:
                            player.sendMessage(Text.literal("§c§kHELP ME§r §4I AM BEHIND YOU"), false);
                            break;
                        case 4:
                            BlockPos torchPos = pPos.add(RANDOM.nextInt(5) - 2, 0, RANDOM.nextInt(5) - 2);
                            if (world.getBlockState(torchPos).isOf(Blocks.TORCH)) {
                                world.setBlockState(torchPos, Blocks.AIR.getDefaultState());
                                world.playSound(null, torchPos, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 1.0f, 1.0f);
                            }
                            break;
                        case 5:
                            player.sendMessage(Text.literal("§4Bir şey ağaçların arkasından izliyor..."), true);
                            break;
                        case 6:
                            player.networkHandler.sendPacket(new GameStateChangeS2CPacket(GameStateChangeS2CPacket.DEMO_MESSAGE_SHOWN, 0));
                            break;
                        case 7:
                            world.playSound(null, pPos, SoundEvents.BLOCK_FURNACE_FIRE_CRACKLE, SoundCategory.BLOCKS, 1.0f, 1.0f);
                            break;
                    }
                }
            }
        });
    }

    private void startNightmareSequence(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        BlockPos nightmarePos = new BlockPos(0, 200, 0);

        for (int x = -5; x <= 5; x++) {
            for (int z = -5; z <= 5; z++) {
                world.setBlockState(nightmarePos.add(x, -1, z), Blocks.BEDROCK.getDefaultState());
                world.setBlockState(nightmarePos.add(x, 0, z), Blocks.GRASS_BLOCK.getDefaultState());
            }
        }

        player.requestTeleport(0.5, 201.0, 0.5);
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 200, 255)); 

        nightmareTimer = 200; 
        nightmareTarget = player;
        player.sendMessage(Text.literal("§4..."), true);
    }

    private void executeNightmareJumpScare(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        world.setWeather(0, 1000, true, true);
        player.sendMessage(Text.literal("§4§lRUNN! RUNN! RUNN!"), false);
        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.HOSTILE, 2.0f, 0.1f);
        player.removeStatusEffect(StatusEffects.SLOWNESS);
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 100, 1));
    }
}
