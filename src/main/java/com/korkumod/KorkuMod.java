package com.korkumod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.BlockState;
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
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.block.BedBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.enums.BedPart;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.ChickenEntity;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class KorkuMod implements ModInitializer {
    public static final String MOD_ID = "korkumod";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);
    private static final Random RANDOM = new Random();

    // ===== SIKLIK AYARLARI (büyük sayı = daha nadir) =====
    private static final int PERIOD_TICKS = 1200;  // kontrol aralığı: 1200 tick = 1 dakika
    private static final int EFFECT_CHANCE = 3;    // her kontrolde efekt şansı: 1/3 (~3 dakikada bir)
    private static final int FIGURE_CHANCE = 6;    // her kontrolde figür şansı: 1/6 (~6 dakikada bir)

    private static final int WINDOW_CHANCE = 5;    // gece içerideyken pencerede adam: 1/5
    private static final int FOOTSTEP_CHANCE = 5;  // arkandan ayak sesleri: 1/5
    private static final int FAKE_MSG_CHANCE = 4;  // sahte oyuncu mesajı: 1/4
    private static final int NIGHTMARE_CHANCE = 3; // yatağa basınca kabus: 1/3 (diğer sefer normal uyursun)
    private static final int WAKE_FIGURE_CHANCE = 2; // uyanınca adam belirme: 1/2

    private static final int NEW_EVENT_CHANCE = 4;  // yeni olaylar havuzu: her kontrolde 1/4 (~4 dakikada bir)

    // Siyah figür modları
    private static final int FIGURE_STARE = 1;     // uzakta durup bakar, kaybolur
    private static final int FIGURE_APPROACH = 2;  // arkandan yavaşça yaklaşır
    private static final int FIGURE_NIGHTMARE = 3; // yatak kabusunda görünür
    private static final int FIGURE_WINDOW = 4;    // pencerenin dışından bakar
    private static final int FIGURE_WAKE = 5;      // uyanınca ucunda durur
    private static final int FIGURE_TREE = 6;      // ağacın arkasından izler
    private static final int FIGURE_BEDMAN = 7;    // yatakta yatar

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

    // Ayak sesleri durumu
    private ServerPlayerEntity stepTarget = null;
    private int stepTicks = 0;
    private int stepCooldown = 0;
    private double stepDist = 8.0;
    private double stepLastX = 0;
    private double stepLastZ = 0;

    // Uyanınca beliren adam için bekleyen oyuncu
    private ServerPlayerEntity wakeTarget = null;
    private int wakeDelay = 0;

    // Geçici bloklar (kan izleri) ve zamanlanmış sesler
    private final List<TempBlock> tempBlocks = new ArrayList<>();
    private final List<SoundTask> soundTasks = new ArrayList<>();

    // Kırmızı gözler
    private ServerWorld eyeWorld = null;
    private int eyeTicks = 0;
    private double eyeX = 0;
    private double eyeY = 0;
    private double eyeZ = 0;
    private double eyeDx = 0;
    private double eyeDz = 0;

    // Ani karanlık
    private ServerWorld darkWorld = null;
    private int darkTicks = 0;
    private long darkOriginal = 0;

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
                if (player instanceof ServerPlayerEntity serverPlayer && RANDOM.nextInt(NIGHTMARE_CHANCE) == 0) {
                    startNightmareSequence(serverPlayer);
                    return ActionResult.SUCCESS;
                }
            }
            return ActionResult.PASS;
        });

        // 🌅 Yataktan kalkınca ucunda siyah adam belirir
        EntitySleepEvents.STOP_SLEEPING.register((entity, sleepingPos) -> {
            if (entity instanceof ServerPlayerEntity sp && RANDOM.nextInt(WAKE_FIGURE_CHANCE) == 0) {
                wakeTarget = sp;
                wakeDelay = 30; // uyandıktan ~1.5 sn sonra
            }
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
            tickFootsteps();
            tickTempBlocks();
            tickSoundTasks();
            tickEyes();
            tickDarkness();

            // Uyanınca beliren adam
            if (wakeTarget != null && --wakeDelay <= 0) {
                ServerPlayerEntity w = wakeTarget;
                wakeTarget = null;
                if (figure == null && nightmareTimer <= 0 && w.isAlive() && !w.isRemoved()) {
                    spawnFigureIndoors(w, FIGURE_WAKE, 3.0);
                }
            }

            if (nightmareTimer > 0) {
                nightmareTimer--;
                if (nightmareTimer == 0 && nightmareTarget != null) {
                    executeNightmareJumpScare(nightmareTarget);
                }
            }

            // Periyodik Korku Tetikleyicileri (PERIOD_TICKS'te bir kontrol edilir)
            if (tickCounter >= PERIOD_TICKS) {
                tickCounter = 0;

                // 💬 Sahte oyuncu mesajı (sunucuda olmayan biri girdi/çıktı)
                if (RANDOM.nextInt(FAKE_MSG_CHANCE) == 0 && !server.getPlayerManager().getPlayerList().isEmpty()) {
                    String[] names = {"Steve", "Alex", "Notch", "Herobrine", "Unknown_Presence"};
                    String fakeName = names[RANDOM.nextInt(names.length)];
                    String what = RANDOM.nextBoolean() ? "joined the game" : "left the game";
                    server.getPlayerManager().broadcast(Text.literal("§e" + fakeName + " " + what), false);
                }

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

                    // 🪟 Gece içerideyken pencerenin dışında siyah adam
                    if (figure == null && nightmareTimer <= 0 && !world.isSkyVisible(pPos)) {
                        long dayTime = world.getTimeOfDay() % 24000L;
                        boolean night = dayTime >= 13000L && dayTime <= 23000L;
                        if (night && RANDOM.nextInt(WINDOW_CHANCE) == 0) {
                            trySpawnWindowFigure(player);
                        }
                    }

                    // 👣 Arkandan ayak sesleri
                    if (stepTarget == null && RANDOM.nextInt(FOOTSTEP_CHANCE) == 0) {
                        startFootsteps(player);
                    }

                    // 🎲 Yeni olay havuzundan rastgele biri (yıldırım, kan izi, kapı çalma...)
                    if (nightmareTimer <= 0 && RANDOM.nextInt(NEW_EVENT_CHANCE) == 0) {
                        runRandomEvent(player);
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
        if (figureTicks % 5 == 0 && figureMode != FIGURE_BEDMAN) faceTarget();

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
        } else if (figureMode == FIGURE_BEDMAN) {
            // Yatağa yaklaşınca kaybolur
            if (figureTicks >= 1200 || dist < 3.5) removeFigure(true);
        } else if (figureMode == FIGURE_WAKE) {
            // 1 saniye sonra kaybolur
            if (figureTicks >= 25) removeFigure(true);
        } else if (figureMode == FIGURE_WINDOW || figureMode == FIGURE_TREE) {
            if (figureTicks >= 1200) {
                removeFigure(true);
                return;
            }
            // Ağacın arkasındaki figür sen yaklaşınca da kaybolur
            if (figureMode == FIGURE_TREE && dist < 7.0) {
                removeFigure(true);
                return;
            }
            // Sen ona bakınca kaybolur
            if (figureTicks >= 40) {
                Vec3d eye = figureTarget.getEyePos();
                Vec3d to = new Vec3d(figure.getX(), figure.getY() + 1.6, figure.getZ()).subtract(eye);
                double d = to.length();
                if (d > 0.1 && d < 16.0) {
                    double dot = figureTarget.getRotationVec(1.0f).dotProduct(to.multiply(1.0 / d));
                    if (dot > 0.93) removeFigure(true);
                }
            }
        }
    }

    // Oyuncunun baktığı yönün yatay birim vektörü
    private double[] horizontalLook(ServerPlayerEntity p) {
        Vec3d look = p.getRotationVec(1.0f);
        double hx = look.x;
        double hz = look.z;
        double len = Math.sqrt(hx * hx + hz * hz);
        if (len < 0.01) {
            return new double[]{0.0, 1.0};
        }
        return new double[]{hx / len, hz / len};
    }

    // İçeride (tavan altında) oyuncunun önünde figür oluşturur
    private boolean spawnFigureIndoors(ServerPlayerEntity player, int mode, double distance) {
        ServerWorld world = player.getServerWorld();
        double[] h = horizontalLook(player);
        for (double d : new double[]{distance, distance - 1.0, distance + 1.0}) {
            double x = player.getX() + h[0] * d;
            double z = player.getZ() + h[1] * d;
            BlockPos feet = BlockPos.ofFloored(x, player.getY(), z);
            if (world.getBlockState(feet).isAir() && world.getBlockState(feet.up()).isAir()
                    && !world.getBlockState(feet.down()).isAir()) {
                spawnFigureAt(player, mode, x, feet.getY(), z);
                return true;
            }
        }
        return false;
    }

    // Yakındaki bir camın dışında (dışarıda, zeminde) figür oluşturur
    private boolean trySpawnWindowFigure(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        BlockPos base = player.getBlockPos();
        List<BlockPos> windows = new ArrayList<>();
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                for (int dy = -1; dy <= 3; dy++) {
                    BlockPos g = base.add(dx, dy, dz);
                    BlockState st = world.getBlockState(g);
                    if (st.isOf(Blocks.GLASS) || st.isOf(Blocks.GLASS_PANE)) {
                        windows.add(g);
                    }
                }
            }
        }
        Collections.shuffle(windows, RANDOM);
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (BlockPos g : windows) {
            for (int[] d : dirs) {
                BlockPos outside = g.add(d[0], 0, d[1]);
                BlockPos inside = g.add(-d[0], 0, -d[1]);
                BlockPos feet = outside.down();
                if (!world.getBlockState(inside).isAir()) continue;
                if (!world.getBlockState(outside).isAir()) continue;
                if (!world.getBlockState(feet).isAir()) continue;
                if (world.getBlockState(feet.down()).isAir()) continue;
                if (!world.isSkyVisible(outside)) continue;
                spawnFigureAt(player, FIGURE_WINDOW, outside.getX() + 0.5, feet.getY(), outside.getZ() + 0.5);
                return true;
            }
        }
        return false;
    }

    // ================= YENİ OLAY HAVUZU =================

    private boolean runRandomEvent(ServerPlayerEntity p) {
        List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < 13; i++) ids.add(i);
        Collections.shuffle(ids, RANDOM);
        for (int id : ids) {
            boolean ok = switch (id) {
                case 0 -> eventLightning(p);
                case 1 -> eventBloodTrail(p);
                case 2 -> eventDarkness(p);
                case 3 -> eventDeathMessage(p);
                case 4 -> eventAnimalsVanish(p);
                case 5 -> eventDoorKnock(p);
                case 6 -> eventTreeFigure(p);
                case 7 -> eventJukebox(p);
                case 8 -> eventTitle(p);
                case 9 -> eventNote(p);
                case 10 -> eventRedEyes(p);
                case 11 -> eventStorm(p);
                case 12 -> eventBedMan(p);
                default -> false;
            };
            if (ok) return true;
        }
        return false;
    }

    // 1) Uzakta yıldırım düşer, orada figür görünür
    private boolean eventLightning(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        if (figure != null || !w.isSkyVisible(p.getBlockPos())) return false;
        double[] h = horizontalLook(p);
        double ang = (RANDOM.nextDouble() - 0.5) * 1.2;
        double c = Math.cos(ang);
        double s = Math.sin(ang);
        double dx = h[0] * c - h[1] * s;
        double dz = h[0] * s + h[1] * c;
        double dist = 30 + RANDOM.nextInt(10);
        double x = p.getX() + dx * dist;
        double z = p.getZ() + dz * dist;
        double y = w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));

        LightningEntity bolt = new LightningEntity(EntityType.LIGHTNING_BOLT, w);
        bolt.refreshPositionAndAngles(x, y, z, 0.0f, 0.0f);
        bolt.setCosmetic(true);
        w.spawnEntity(bolt);

        spawnFigureAt(p, FIGURE_STARE, x, y, z);
        return true;
    }

    // 5) Yerde kırmızı izler belirir, 15 saniye sonra kaybolur
    private boolean eventBloodTrail(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        double[] h = horizontalLook(p);
        int placed = 0;
        for (int i = 0; i < 12; i++) {
            double d = 3.0 + i * 1.2;
            double x = p.getX() + h[0] * d + (RANDOM.nextDouble() - 0.5);
            double z = p.getZ() + h[1] * d + (RANDOM.nextDouble() - 0.5);
            BlockPos pos = BlockPos.ofFloored(x, p.getY(), z);
            for (int dy = 2; dy >= -2; dy--) {
                BlockPos cp = pos.add(0, dy, 0);
                if (w.getBlockState(cp).isAir() && w.getBlockState(cp.down()).isSolidBlock(w, cp.down())) {
                    w.setBlockState(cp, Blocks.RED_CARPET.getDefaultState());
                    tempBlocks.add(new TempBlock(w, cp, 300));
                    placed++;
                    break;
                }
            }
        }
        return placed > 0;
    }

    // 7) Gün ortasında gökyüzü bir anda kararır (10 saniye)
    private boolean eventDarkness(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        if (darkTicks > 0 || w.getRegistryKey() != World.OVERWORLD) return false;
        if (!w.isSkyVisible(p.getBlockPos()) || w.isRaining()) return false;
        long t = w.getTimeOfDay() % 24000L;
        if (t < 1000L || t > 11000L) return false;

        darkOriginal = w.getTimeOfDay();
        darkWorld = w;
        darkTicks = 200;
        long day = darkOriginal - (darkOriginal % 24000L);
        w.setTimeOfDay(day + 18000L);
        w.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.AMBIENT, 2.0f, 0.6f);
        return true;
    }

    private void tickDarkness() {
        if (darkTicks <= 0) return;
        darkTicks--;
        if (darkTicks == 0 && darkWorld != null) {
            darkWorld.setTimeOfDay(darkOriginal + 200L);
            darkWorld = null;
        }
    }

    // 9) Olmayan birinin ölüm mesajı
    private boolean eventDeathMessage(ServerPlayerEntity p) {
        String[] names = {"Steve", "Alex", "Notch", "Herobrine"};
        String[] deaths = {
                "was slain by Unknown_Presence",
                "fell from a high place",
                "was killed by Unknown_Presence",
                "hit the ground too hard",
                "was shot by Skeleton",
                "drowned"
        };
        String name = names[RANDOM.nextInt(names.length)];
        String death = deaths[RANDOM.nextInt(deaths.length)];
        p.getServer().getPlayerManager().broadcast(Text.literal(name + " " + death), false);
        return true;
    }

    // 10) Etraftaki hayvanlar 15 saniyeliğine görünmez olur (sesleri gelmeye devam eder)
    private boolean eventAnimalsVanish(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        List<AnimalEntity> animals = w.getEntitiesByClass(
                AnimalEntity.class,
                p.getBoundingBox().expand(24.0),
                a -> !a.hasCustomName() && (a instanceof CowEntity || a instanceof SheepEntity
                        || a instanceof PigEntity || a instanceof ChickenEntity)
        );
        if (animals.isEmpty()) return false;
        int count = 0;
        for (AnimalEntity a : animals) {
            if (count >= 8) break;
            a.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, 300, 0, false, false));
            w.spawnParticles(ParticleTypes.POOF, a.getX(), a.getY() + 0.5, a.getZ(), 8, 0.3, 0.3, 0.3, 0.02);
            count++;
        }
        return true;
    }

    // 12) Yakındaki kapı üç kez çalınır
    private boolean eventDoorKnock(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        BlockPos base = p.getBlockPos();
        List<BlockPos> doors = new ArrayList<>();
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -8; dz <= 8; dz++) {
                for (int dy = -1; dy <= 2; dy++) {
                    BlockPos pos = base.add(dx, dy, dz);
                    if (w.getBlockState(pos).getBlock() instanceof DoorBlock) {
                        doors.add(pos);
                    }
                }
            }
        }
        if (doors.isEmpty()) return false;
        BlockPos door = doors.get(RANDOM.nextInt(doors.size()));
        for (int i = 0; i < 3; i++) {
            scheduleSound(w, door.getX() + 0.5, door.getY() + 0.5, door.getZ() + 0.5,
                    SoundEvents.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, i * 14, 1.5f, 0.9f, false);
        }
        return true;
    }

    // 13) Ağacın arkasından yarı görünerek izleyen figür
    private boolean eventTreeFigure(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        if (figure != null || !w.isSkyVisible(p.getBlockPos())) return false;
        for (int i = 0; i < 40; i++) {
            double ang = RANDOM.nextDouble() * Math.PI * 2;
            double dist = 10 + RANDOM.nextInt(12);
            int x = (int) Math.floor(p.getX() + Math.cos(ang) * dist);
            int z = (int) Math.floor(p.getZ() + Math.sin(ang) * dist);
            for (int dy = 4; dy >= -6; dy--) {
                BlockPos log = new BlockPos(x, p.getBlockPos().getY() + dy, z);
                BlockState st = w.getBlockState(log);
                BlockState below = w.getBlockState(log.down());
                if (st.isIn(BlockTags.LOGS) && !below.isIn(BlockTags.LOGS) && !below.isAir()) {
                    double ddx = log.getX() + 0.5 - p.getX();
                    double ddz = log.getZ() + 0.5 - p.getZ();
                    double len = Math.sqrt(ddx * ddx + ddz * ddz);
                    if (len < 1.0) continue;
                    double ux = ddx / len;
                    double uz = ddz / len;
                    // gövdenin arkasında, hafif yana kaymış
                    double fx = log.getX() + 0.5 + ux * 1.0 + (-uz) * 0.5;
                    double fz = log.getZ() + 0.5 + uz * 1.0 + ux * 0.5;
                    BlockPos feet = BlockPos.ofFloored(fx, log.getY(), fz);
                    if (w.getBlockState(feet).isAir() && w.getBlockState(feet.up()).isAir()
                            && !w.getBlockState(feet.down()).isAir()) {
                        spawnFigureAt(p, FIGURE_TREE, fx, feet.getY(), fz);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // 14) Yakındaki müzik kutusundan bozuk bir melodi gelir
    private boolean eventJukebox(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        BlockPos base = p.getBlockPos();
        BlockPos found = null;
        for (int dx = -12; dx <= 12 && found == null; dx++) {
            for (int dz = -12; dz <= 12 && found == null; dz++) {
                for (int dy = -3; dy <= 4; dy++) {
                    BlockPos pos = base.add(dx, dy, dz);
                    if (w.getBlockState(pos).isOf(Blocks.JUKEBOX)) {
                        found = pos;
                        break;
                    }
                }
            }
        }
        if (found == null) return false;
        float[] pitches = {0.7f, 0.8f, 0.62f, 0.9f, 0.55f, 0.75f, 0.5f, 0.6f};
        int[] delays = {0, 14, 28, 42, 60, 76, 92, 112};
        for (int i = 0; i < pitches.length; i++) {
            scheduleSound(w, found.getX() + 0.5, found.getY() + 0.5, found.getZ() + 0.5,
                    SoundEvents.BLOCK_BELL_USE, delays[i], 1.4f, pitches[i], true);
        }
        return true;
    }

    // 15) Ekranın ortasında bir an yazı çıkar
    private boolean eventTitle(ServerPlayerEntity p) {
        String[] texts = {"§4§lBAK", "§4§lARKANDA", "§c§lSENİ GÖRÜYORUM"};
        String text = texts[RANDOM.nextInt(texts.length)];
        p.networkHandler.sendPacket(new TitleFadeS2CPacket(0, 15, 5));
        p.networkHandler.sendPacket(new TitleS2CPacket(Text.literal(text)));
        return true;
    }

    // 16) Envantere gizemli bir not düşer
    private boolean eventNote(ServerPlayerEntity p) {
        String[] titles = {"§4Arkanı dönme", "§4Seni izliyorum", "§4Kaçamazsın"};
        String[] lores = {"§7Bunu sen yazmadın.", "§7Yanındayım.", "§7Hâlâ buradayım."};
        int i = RANDOM.nextInt(titles.length);
        ItemStack note = new ItemStack(Items.PAPER);
        note.set(DataComponentTypes.CUSTOM_NAME, Text.literal(titles[i]));
        note.set(DataComponentTypes.LORE, new LoreComponent(List.of(Text.literal(lores[RANDOM.nextInt(lores.length)]))));
        p.getInventory().insertStack(note);
        return true;
    }

    // 17) Karanlıkta uzakta iki kırmızı parlayan göz
    private boolean eventRedEyes(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        if (eyeTicks > 0 || w.isSkyVisible(p.getBlockPos())) return false;
        double[] h = horizontalLook(p);
        for (int i = 0; i < 20; i++) {
            double ang = (RANDOM.nextDouble() - 0.5) * 1.4;
            double c = Math.cos(ang);
            double s = Math.sin(ang);
            double dx = h[0] * c - h[1] * s;
            double dz = h[0] * s + h[1] * c;
            double d = 12 + RANDOM.nextInt(10);
            BlockPos pos = BlockPos.ofFloored(p.getX() + dx * d, p.getY(), p.getZ() + dz * d);
            if (w.getBlockState(pos).isAir() && w.getBlockState(pos.up()).isAir()
                    && !w.getBlockState(pos.down()).isAir() && w.getLightLevel(pos) < 8) {
                eyeWorld = w;
                eyeTicks = 80;
                eyeX = pos.getX() + 0.5;
                eyeY = pos.getY() + 1.6;
                eyeZ = pos.getZ() + 0.5;
                eyeDx = -dz * 0.18;
                eyeDz = dx * 0.18;
                return true;
            }
        }
        return false;
    }

    private void tickEyes() {
        if (eyeTicks <= 0 || eyeWorld == null) return;
        eyeTicks--;
        if (eyeTicks % 2 == 0) {
            eyeWorld.spawnParticles(ParticleTypes.SMALL_FLAME, eyeX + eyeDx, eyeY, eyeZ + eyeDz, 1, 0.0, 0.0, 0.0, 0.0);
            eyeWorld.spawnParticles(ParticleTypes.SMALL_FLAME, eyeX - eyeDx, eyeY, eyeZ - eyeDz, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    // 18) Ani fırtına: yağmur ve gök gürültüsü birden başlar, 20 saniye sonra durur
    private boolean eventStorm(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        if (w.isRaining() || w.getRegistryKey() != World.OVERWORLD) return false;
        w.setWeather(0, 400, true, true);
        w.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.WEATHER, 6.0f, 0.8f);
        return true;
    }

    // 20) Evdeki yatakta siyah adam yatıyor, yaklaşınca kaybolur
    private boolean eventBedMan(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        if (figure != null || w.isSkyVisible(p.getBlockPos())) return false;
        BlockPos base = p.getBlockPos();
        List<BlockPos> beds = new ArrayList<>();
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -8; dz <= 8; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    BlockPos b = base.add(dx, dy, dz);
                    BlockState st = w.getBlockState(b);
                    if (st.getBlock() instanceof BedBlock && st.get(BedBlock.PART) == BedPart.HEAD) {
                        double ex = b.getX() + 0.5 - p.getX();
                        double ez = b.getZ() + 0.5 - p.getZ();
                        if (ex * ex + ez * ez > 16.0) { // oyuncu yatağın hemen yanında değilse
                            beds.add(b);
                        }
                    }
                }
            }
        }
        if (beds.isEmpty()) return false;
        BlockPos bed = beds.get(RANDOM.nextInt(beds.size()));
        spawnFigureAt(p, FIGURE_BEDMAN, bed.getX() + 0.5, bed.getY() + 0.5625, bed.getZ() + 0.5);
        if (figure != null) {
            figure.setNoGravity(true);
            figure.setSleepingPosition(bed);
        }
        return true;
    }

    // ---- yardımcılar: zamanlanmış ses ve geçici bloklar ----

    private void scheduleSound(ServerWorld w, double x, double y, double z, SoundEvent sound,
                               int delay, float volume, float pitch, boolean notes) {
        soundTasks.add(new SoundTask(w, x, y, z, sound, delay, volume, pitch, notes));
    }

    private void tickSoundTasks() {
        Iterator<SoundTask> it = soundTasks.iterator();
        while (it.hasNext()) {
            SoundTask t = it.next();
            if (--t.delay <= 0) {
                t.world.playSound(null, t.x, t.y, t.z, t.sound, SoundCategory.BLOCKS, t.volume, t.pitch);
                if (t.notes) {
                    t.world.spawnParticles(ParticleTypes.NOTE, t.x, t.y + 0.8, t.z, 1, 0.3, 0.3, 0.3, 1.0);
                }
                it.remove();
            }
        }
    }

    private void tickTempBlocks() {
        Iterator<TempBlock> it = tempBlocks.iterator();
        while (it.hasNext()) {
            TempBlock t = it.next();
            if (--t.ticks <= 0) {
                if (t.world.getBlockState(t.pos).isOf(Blocks.RED_CARPET)) {
                    t.world.setBlockState(t.pos, Blocks.AIR.getDefaultState());
                }
                it.remove();
            }
        }
    }

    private static class TempBlock {
        final ServerWorld world;
        final BlockPos pos;
        int ticks;

        TempBlock(ServerWorld world, BlockPos pos, int ticks) {
            this.world = world;
            this.pos = pos;
            this.ticks = ticks;
        }
    }

    private static class SoundTask {
        final ServerWorld world;
        final double x;
        final double y;
        final double z;
        final SoundEvent sound;
        int delay;
        final float volume;
        final float pitch;
        final boolean notes;

        SoundTask(ServerWorld world, double x, double y, double z, SoundEvent sound,
                  int delay, float volume, float pitch, boolean notes) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.sound = sound;
            this.delay = delay;
            this.volume = volume;
            this.pitch = pitch;
            this.notes = notes;
        }
    }

    // ================= AYAK SESLERİ =================

    private void startFootsteps(ServerPlayerEntity p) {
        stepTarget = p;
        stepTicks = 600; // ~30 saniye
        stepCooldown = 0;
        stepDist = 8.0;
        stepLastX = p.getX();
        stepLastZ = p.getZ();
    }

    private void tickFootsteps() {
        if (stepTarget == null) return;
        ServerPlayerEntity p = stepTarget;
        if (p.isRemoved() || !p.isAlive() || stepTicks <= 0) {
            stepTarget = null;
            return;
        }
        stepTicks--;

        double mx = p.getX() - stepLastX;
        double mz = p.getZ() - stepLastZ;
        stepLastX = p.getX();
        stepLastZ = p.getZ();
        double speed = Math.sqrt(mx * mx + mz * mz);

        if (stepCooldown > 0) stepCooldown--;
        if (speed < 0.03) return;   // duruyorsan ayak sesleri de durur
        if (stepCooldown > 0) return;
        stepCooldown = speed > 0.25 ? 4 : 8; // koşarsan daha hızlı

        ServerWorld w = p.getServerWorld();
        double[] h = horizontalLook(p);
        double x = p.getX() - h[0] * stepDist;
        double z = p.getZ() - h[1] * stepDist;
        stepDist = Math.max(3.0, stepDist - 0.15); // yavaş yavaş yaklaşır

        BlockState below = w.getBlockState(p.getBlockPos().down());
        SoundEvent step = below.getSoundGroup().getStepSound();
        w.playSound(null, x, p.getY(), z, step, SoundCategory.PLAYERS, 0.9f, 0.8f);
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
