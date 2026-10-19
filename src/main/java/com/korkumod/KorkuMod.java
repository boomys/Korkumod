package com.korkumod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.Blocks;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.GameStateChangeS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Random;

public class KorkuMod implements ModInitializer {
    public static final String MOD_ID = "korkumod";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);
    private static final Random RANDOM = new Random();

    // ===== SIKLIK AYARLARI (büyük sayı = daha nadir) =====
    private static final int PERIOD_TICKS = 1200;  // kontrol aralığı: 1200 tick = 1 dakika
    private static final int EFFECT_CHANCE = 3;    // her kontrolde efekt şansı: 1/3 (~3 dakikada bir)
    private static final int FIGURE_CHANCE = 6;    // her kontrolde figür şansı: 1/6 (~6 dakikada bir)

    // Siyah figür modları
    private static final int FIGURE_STARE = 1;     // uzakta durup bakar, kaybolur
    private static final int FIGURE_APPROACH = 2;  // arkandan yavaşça yaklaşır
    private static final int FIGURE_NIGHTMARE = 3; // yatak kabusunda görünür

    // 1. Lanetli Tabela (Cursed Sign) Eşyası
    // 1.21.2'de registryKey zorunlu, yoksa "Item id not set" hatası verir
    public static final RegistryKey<Item> CURSED_SIGN_KEY =
            RegistryKey.of(RegistryKeys.ITEM, Identifier.of(MOD_ID, "cursed_sign"));

    public static final Item CURSED_SIGN_ITEM = Registry.register(
            Registries.ITEM,
            CURSED_SIGN_KEY,
            new Item(new Item.Settings().maxCount(1).registryKey(CURSED_SIGN_KEY))
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

    // Siyah skinli adam yaratığı
    public static final RegistryKey<EntityType<?>> KORKU_ADAM_KEY =
            RegistryKey.of(RegistryKeys.ENTITY_TYPE, Identifier.of(MOD_ID, "korku_adam"));

    public static final EntityType<KorkuAdamEntity> KORKU_ADAM = Registry.register(
            Registries.ENTITY_TYPE,
            KORKU_ADAM_KEY,
            EntityType.Builder.create(KorkuAdamEntity::new, SpawnGroup.MISC)
                    .dimensions(0.6f, 1.8f)
                    .build(KORKU_ADAM_KEY)
    );

    private int tickCounter = 0;
    private int nightmareTimer = -1;
    private ServerPlayerEntity nightmareTarget = null;

    // Siyah figür durumu
    private KorkuAdamEntity figure = null;
    private ServerPlayerEntity figureTarget = null;
    private ServerWorld figureWorld = null;
    private int figureMode = 0;
    private int figureTicks = 0;

    @Override
    public void onInitialize() {
        LOGGER.info("KorkuMod Tum Mekaniklerle Tam Kapasite Baslatildi!");

        FabricDefaultAttributeRegistry.register(KORKU_ADAM, KorkuAdamEntity.createAttributes());

        // 🟢 Oyuna Girişte Fake Join Mesajı ve Eşya Teslimi
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            server.execute(() -> {
                player.getInventory().insertStack(new ItemStack(CURSED_SIGN_ITEM));
            });
            server.getPlayerManager().broadcast(Text.literal("§e[SYSTEM]: Unknown_Presence joined the game"), false);
        });

        // 💬 Oyuncu Chat Yanıt Sistemi ("Hee çağrımı o mal köpek")
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            sender.getServer().execute(() -> {
                sender.getServer().getPlayerManager().broadcast(
                    Text.literal("§4<Unknown_Presence> §cHee çağrımı o mal köpek"),
                    false
                );
            });
        });

        // 🛌 Yatak Kabusu ve Katmankaya Sahnesi
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

        // 🩸 Lanetli Tabela Yere Konulduğunda Ritüel Başlangıcı
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

        // 🔄 Dinamik Korku Döndürücüsü (Tick Olayları)
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickCounter++;

            // Siyah figürü her tick güncelle
            tickFigure();

            if (nightmareTimer > 0) {
                nightmareTimer--;
                if (nightmareTimer == 0 && nightmareTarget != null) {
                    executeNightmareJumpScare(nightmareTarget);
                }
            }

            // Periyodik Korku Tetikleyicileri (PERIOD_TICKS'te bir kontrol edilir)
            if (tickCounter >= PERIOD_TICKS) {
                tickCounter = 0;

                for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                    ServerWorld world = player.getServerWorld();
                    BlockPos pPos = player.getBlockPos();

                    // 👤 Siyah figür: açık havadaysan arada bir belirir
                    if (figure == null && nightmareTimer <= 0
                            && world.isSkyVisible(pPos) && RANDOM.nextInt(FIGURE_CHANCE) == 0) {
                        if (RANDOM.nextBoolean()) {
                            spawnFigure(player, FIGURE_STARE, 25.0, false);   // karşıda durur
                        } else {
                            spawnFigure(player, FIGURE_APPROACH, 20.0, true); // arkadan gelir
                        }
                    }

                    // Efektler her kontrolde değil, arada bir olsun
                    if (RANDOM.nextInt(EFFECT_CHANCE) != 0) continue;

                    // Kanlı ayak izi parçacığı
                    world.spawnParticles(ParticleTypes.DUST_PLUME, pPos.getX(), pPos.getY() + 0.1, pPos.getZ(), 5, 0.2, 0.0, 0.2, 0.0);

                    int randomEffect = RANDOM.nextInt(9);
                    switch (randomEffect) {
                        case 0: // Kulak dibinde nefes
                            world.playSound(null, pPos, SoundEvents.ENTITY_PLAYER_BREATH, SoundCategory.PLAYERS, 1.5f, 0.2f);
                            break;
                        case 1: // Cam kırılması ve nefes darlığı (Körlük + Yavaşlık)
                            world.playSound(null, pPos, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.BLOCKS, 1.0f, 0.8f);
                            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 100, 2));
                            player.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 60, 1));
                            break;
                        case 2: // Kapı/Sandık zorlanma sesi
                            world.playSound(null, pPos, SoundEvents.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, SoundCategory.BLOCKS, 1.0f, 0.5f);
                            break;
                        case 3: // Bozuk chat / telsiz fısıltısı
                            player.sendMessage(Text.literal("§c§kHELP ME§r §4I AM BEHIND YOU"), false);
                            break;
                        case 4: // Meşale söndürme
                            BlockPos torchPos = pPos.add(RANDOM.nextInt(5) - 2, 0, RANDOM.nextInt(5) - 2);
                            if (world.getBlockState(torchPos).isOf(Blocks.TORCH)) {
                                world.setBlockState(torchPos, Blocks.AIR.getDefaultState());
                                world.playSound(null, torchPos, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 1.0f, 1.0f);
                            }
                            break;
                        case 5: // Yapraksız ölü ağaç hissi / stalker mesajı
                            player.sendMessage(Text.literal("§4Bir şey ağaçların arkasından izliyor..."), true);
                            break;
                        case 6: // Sahte ölüm ekranı kırılması
                            player.networkHandler.sendPacket(new GameStateChangeS2CPacket(GameStateChangeS2CPacket.DEMO_MESSAGE_SHOWN, 0));
                            break;
                        case 7: // Fırın cızırtısı
                            world.playSound(null, pPos, SoundEvents.BLOCK_FURNACE_FIRE_CRACKLE, SoundCategory.BLOCKS, 1.0f, 1.0f);
                            break;
                        case 8: // Sahte envanter tıklama sesi
                            world.playSound(null, pPos, SoundEvents.UI_BUTTON_CLICK.value(), SoundCategory.PLAYERS, 1.0f, 0.2f);
                            break;
                    }
                }
            }
        });
    }

    // ================= SİYAH FİGÜR =================

    // Oyuncunun baktığı yöne göre ileri/geri belirli mesafede figür oluşturur
    private void spawnFigure(ServerPlayerEntity player, int mode, double distance, boolean behind) {
        ServerWorld world = player.getServerWorld();
        Vec3d look = player.getRotationVec(1.0f);
        double hx = look.x;
        double hz = look.z;
        double len = Math.sqrt(hx * hx + hz * hz);
        if (len < 0.01) {
            hx = 0;
            hz = 1;
            len = 1;
        }
        hx /= len;
        hz /= len;
        double sign = behind ? -1.0 : 1.0;
        double x = player.getX() + hx * distance * sign;
        double z = player.getZ() + hz * distance * sign;
        double y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
        spawnFigureAt(player, mode, x, y, z);
    }

    // Belirli bir noktada siyah skinli adamı oluşturur (AI kapalı)
    private void spawnFigureAt(ServerPlayerEntity player, int mode, double x, double y, double z) {
        removeFigure(false);
        ServerWorld world = player.getServerWorld();
        KorkuAdamEntity e = new KorkuAdamEntity(KORKU_ADAM, world);
        e.refreshPositionAndAngles(x, y, z, 0.0f, 0.0f);
        e.setAiDisabled(true);
        e.setSilent(true);
        e.setInvulnerable(true);
        e.setPersistent();
        world.spawnEntity(e);

        figure = e;
        figureTarget = player;
        figureWorld = world;
        figureMode = mode;
        figureTicks = 0;
        faceTarget();
    }

    // Figürü oyuncuya doğru döndürür
    private void faceTarget() {
        if (figure == null || figureTarget == null) return;
        double dx = figureTarget.getX() - figure.getX();
        double dz = figureTarget.getZ() - figure.getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        figure.setYaw(yaw);
        figure.setHeadYaw(yaw);
        figure.setBodyYaw(yaw);
    }

    private void tickFigure() {
        if (figure == null) return;
        if (figure.isRemoved() || figureTarget == null || figureTarget.isRemoved()
                || !figureTarget.isAlive() || figureTarget.getServerWorld() != figureWorld) {
            removeFigure(false);
            return;
        }

        figureTicks++;
        if (figureTicks % 5 == 0) faceTarget();

        double dx = figureTarget.getX() - figure.getX();
        double dz = figureTarget.getZ() - figure.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);

        if (figureMode == FIGURE_STARE) {
            // 6 saniye bakıp kaybolur
            if (figureTicks >= 120) removeFigure(true);
        } else if (figureMode == FIGURE_APPROACH) {
            if (dist < 4.0) {
                figureScare();
                return;
            }
            if (figureTicks >= 1200) {
                removeFigure(true);
                return;
            }
            // Yavaşça oyuncuya yürür
            double nx = figure.getX() + dx / dist * 0.07;
            double nz = figure.getZ() + dz / dist * 0.07;
            double ny = figureWorld.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(nx), (int) Math.floor(nz));
            figure.setPosition(nx, ny, nz);
        } else if (figureMode == FIGURE_NIGHTMARE) {
            if (figureTicks >= 600) removeFigure(false);
        }
    }

    // Yaklaşan figür oyuncuya ulaşınca
    private void figureScare() {
        ServerWorld world = figureWorld;
        ServerPlayerEntity p = figureTarget;
        world.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_SCREAM, SoundCategory.HOSTILE, 2.0f, 0.5f);
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 40, 1));
        p.sendMessage(Text.literal("§4§l...arkandaydı."), true);
        removeFigure(true);
    }

    private void removeFigure(boolean poof) {
        if (figure != null && !figure.isRemoved()) {
            if (poof && figureWorld != null) {
                figureWorld.spawnParticles(ParticleTypes.LARGE_SMOKE, figure.getX(), figure.getY() + 1.5, figure.getZ(), 20, 0.3, 0.8, 0.3, 0.02);
            }
            figure.discard();
        }
        figure = null;
        figureTarget = null;
        figureWorld = null;
        figureMode = 0;
        figureTicks = 0;
    }

    // ================= YATAK KABUSU =================

    private void startNightmareSequence(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        BlockPos nightmarePos = new BlockPos(0, 200, 0);

        // Katmankaya ve çim platformu oluştur
        for (int x = -5; x <= 5; x++) {
            for (int z = -5; z <= 5; z++) {
                world.setBlockState(nightmarePos.add(x, -1, z), Blocks.BEDROCK.getDefaultState());
                world.setBlockState(nightmarePos.add(x, 0, z), Blocks.GRASS_BLOCK.getDefaultState());
            }
        }

        player.requestTeleport(0.5, 201.0, 0.5);
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 200, 255));

        // 👤 Platformun karşı ucunda siyah figür bekler
        spawnFigureAt(player, FIGURE_NIGHTMARE, 0.5, 201.0, 4.5);

        nightmareTimer = 200; // 10 saniye bakışma
        nightmareTarget = player;
        player.sendMessage(Text.literal("§4..."), true);
    }

    private void executeNightmareJumpScare(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        world.setWeather(0, 1000, true, true);
        player.sendMessage(Text.literal("§4§lRUNN! RUNN! RUNN!"), false);
        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.HOSTILE, 2.0f, 0.1f);

        // 👤 Figür bir anda tam önüne ışınlanır, kısa süre sonra kaybolur
        if (figure != null && !figure.isRemoved() && figureMode == FIGURE_NIGHTMARE) {
            Vec3d look = player.getRotationVec(1.0f);
            double hx = look.x;
            double hz = look.z;
            double len = Math.sqrt(hx * hx + hz * hz);
            if (len < 0.01) {
                hx = 0;
                hz = 1;
                len = 1;
            }
            hx /= len;
            hz /= len;
            figure.setPosition(player.getX() + hx * 2.0, player.getY(), player.getZ() + hz * 2.0);
            faceTarget();
            figureMode = FIGURE_STARE;
            figureTicks = 100; // ~1 saniye sonra kaybolur
        }

        player.removeStatusEffect(StatusEffects.SLOWNESS);
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 100, 1));
    }
}
