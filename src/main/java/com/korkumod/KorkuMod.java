package com.korkumod;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
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
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.network.packet.s2c.play.StopSoundS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.Box;
import net.minecraft.world.GameRules;
import java.util.ArrayList;
import java.util.UUID;
import java.util.Iterator;
import java.util.Collections;
import java.util.List;
import java.util.Random;
public class KorkuMod implements ModInitializer {
  public static final String MOD_ID = "korkumod";
  public static final Logger LOGGER = LogManager.getLogger(MOD_ID);
  private static final Random RANDOM = new Random();
  private static final int PERIOD_TICKS = 1200;  // kontrol aralığı: 1200 tick = 1 dakika
  private static final int EFFECT_CHANCE = 3;    // her kontrolde efekt şansı: 1/3 (~3 dakikada bir)
  static final int FIGURE_CHANCE = 6;
  private static final int WINDOW_CHANCE = 5;
  private static final int FOOTSTEP_CHANCE = 5;
  private static final int FAKE_MSG_CHANCE = 4;
  private static final int NIGHTMARE_CHANCE = 3;
  private static final int WAKE_FIGURE_CHANCE = 2;
  private static final int NEW_EVENT_CHANCE = 4;  // yeni olaylar havuzu: her kontrolde 1/4 (~4 dakikada bir)
  private static final int NULL_FIRST_DELAY = 3600;  // sunucu açıldıktan ~3 dk sonra ilk ziyaret
  private static final int NULL_INTERVAL = 12000;    // ziyaretler arası: 12000 tick = 10 dk (test için 1200 yap)
  private static final int NULL_WAIT_TICKS = 3600;   // eşyayı getirmek için süre: 3 dk
  private static final int NULL_CHASE_TICKS = 600;   // kaçış süresi: 30 sn
  private static final int NULL_RAID_CHANCE = 5;     // ziyaretlerin 1/5'i TPA baskını (kılıçla öldürür)
  static final int FIGURE_STARE = 1;
  static final int FIGURE_APPROACH = 2;
  static final int FIGURE_NIGHTMARE = 3;
  static final int FIGURE_WINDOW = 4;
  static final int FIGURE_WAKE = 5;
  static final int FIGURE_TREE = 6;
  static final int FIGURE_BEDMAN = 7;
  static final int FIGURE_NULL = 8;
  private static final int NULL_PHASE_WAIT = 1;
  private static final int NULL_PHASE_COUNTDOWN = 2;
  private static final int NULL_PHASE_CHASE = 3;
  private static final int NULL_PHASE_RAID = 4;
  public static final RegistryKey<Item> CURSED_SIGN_KEY =
      RegistryKey.of(RegistryKeys.ITEM, Identifier.of(MOD_ID, "cursed_sign"));
  public static final Item CURSED_SIGN_ITEM = Registry.register(
      Registries.ITEM,
      CURSED_SIGN_KEY,
      new Item(new Item.Settings().maxCount(1).registryKey(CURSED_SIGN_KEY))
  );
  public static final ItemGroup KORKUMOD_GROUP = Registry.register(
      Registries.ITEM_GROUP,
      Identifier.of(MOD_ID, "korkumod_group"),
      FabricItemGroup.builder()
          .displayName(Text.literal("KorkuMod"))
          .icon(() -> new ItemStack(CURSED_SIGN_ITEM))
          .entries((displayContext, entries) -> entries.add(CURSED_SIGN_ITEM))
          .build()
  );
  public static final RegistryKey<EntityType<?>> KORKU_ADAM_KEY =
      RegistryKey.of(RegistryKeys.ENTITY_TYPE, Identifier.of(MOD_ID, "korku_adam"));
  public static final EntityType<KorkuAdamEntity> KORKU_ADAM = Registry.register(
      Registries.ENTITY_TYPE,
      KORKU_ADAM_KEY,
      EntityType.Builder.create(KorkuAdamEntity::new, SpawnGroup.MISC)
          .dimensions(0.6f, 1.8f)
          .build(KORKU_ADAM_KEY)
  );
  public static final SoundEvent CHASE_MUSIC = Registry.register(
      Registries.SOUND_EVENT,
      Identifier.of(MOD_ID, "chase_music"),
      SoundEvent.of(Identifier.of(MOD_ID, "chase_music"))
  );
  private int tickCounter = 0;
  private int nightmareTimer = -1;
  private ServerPlayerEntity nightmareTarget = null;
  KorkuAdamEntity figure = null;
  private ServerPlayerEntity figureTarget = null;
  private ServerWorld figureWorld = null;
  private int figureMode = 0;
  private int figureTicks = 0;
  private ServerPlayerEntity stepTarget = null;
  private int stepTicks = 0;
  private int stepCooldown = 0;
  private double stepDist = 8.0;
  private double stepLastX = 0;
  private double stepLastZ = 0;
  private ServerPlayerEntity wakeTarget = null;
  private int wakeDelay = 0;
  private final KorkuEvents events = new KorkuEvents(this);
  private int nullCooldown = NULL_FIRST_DELAY;
  private int nullPhase = 0;
  private int nullTimer = 0;
  private Item nullItem = null;
  private int nullItemCount = 0;
  private ServerPlayerEntity lockedPlayer = null;
  private boolean keepInvToggled = false;
  private boolean savedKeepInv = false;
  private int keepInvTicks = 0;
  private MinecraftServer keepInvServer = null;
  private UUID raidVictim = null;
  @Override
  public void onInitialize() {
    LOGGER.info("KorkuMod started with all mechanics.");
    FabricDefaultAttributeRegistry.register(KORKU_ADAM, KorkuAdamEntity.createAttributes());
    PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) ->
        !(lockedPlayer != null && player == lockedPlayer));
    UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
      if (lockedPlayer != null && player == lockedPlayer
          && player.getStackInHand(hand).getItem() instanceof BlockItem) {
        return ActionResult.FAIL;
      }
      return ActionResult.PASS;
    });
    ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
      if (raidVictim != null && newPlayer.getUuid().equals(raidVictim)) {
        restoreKeepInventory(newPlayer.getServer());
      }
    });
    ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
      ServerPlayerEntity player = handler.getPlayer();
      server.execute(() -> {
        player.getInventory().insertStack(new ItemStack(CURSED_SIGN_ITEM));
      });
      server.getPlayerManager().broadcast(Text.literal("§e[SYSTEM]: Unknown_Presence joined the game"), false);
    });
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
    EntitySleepEvents.STOP_SLEEPING.register((entity, sleepingPos) -> {
      if (entity instanceof ServerPlayerEntity sp && RANDOM.nextInt(WAKE_FIGURE_CHANCE) == 0) {
        wakeTarget = sp;
        wakeDelay = 30;
      }
    });
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
    ServerTickEvents.END_SERVER_TICK.register(server -> {
      tickCounter++;
      tickFigure();
      tickFootsteps();
      events.tick();
      tickNullScheduler(server);
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
      if (tickCounter >= PERIOD_TICKS) {
        tickCounter = 0;
        if (RANDOM.nextInt(FAKE_MSG_CHANCE) == 0 && !server.getPlayerManager().getPlayerList().isEmpty()) {
          String[] names = {"Steve", "Alex", "Notch", "Herobrine", "Unknown_Presence"};
          String fakeName = names[RANDOM.nextInt(names.length)];
          String what = RANDOM.nextBoolean() ? "joined the game" : "left the game";
          server.getPlayerManager().broadcast(Text.literal("§e" + fakeName + " " + what), false);
        }
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
          ServerWorld world = player.getServerWorld();
          BlockPos pPos = player.getBlockPos();
          if (figure == null && nightmareTimer <= 0
              && world.isSkyVisible(pPos) && RANDOM.nextInt(FIGURE_CHANCE) == 0) {
            if (RANDOM.nextBoolean()) {
              spawnFigure(player, FIGURE_STARE, 25.0, false);
            } else {
              spawnFigure(player, FIGURE_APPROACH, 20.0, true);
            }
          }
          if (figure == null && nightmareTimer <= 0 && !world.isSkyVisible(pPos)) {
            long dayTime = world.getTimeOfDay() % 24000L;
            boolean night = dayTime >= 13000L && dayTime <= 23000L;
            if (night && RANDOM.nextInt(WINDOW_CHANCE) == 0) {
              trySpawnWindowFigure(player);
            }
          }
          if (stepTarget == null && RANDOM.nextInt(FOOTSTEP_CHANCE) == 0) {
            startFootsteps(player);
          }
          if (nightmareTimer <= 0 && RANDOM.nextInt(NEW_EVENT_CHANCE) == 0) {
            events.runRandomEvent(player);
          }
          if (RANDOM.nextInt(EFFECT_CHANCE) != 0) continue;
          world.spawnParticles(ParticleTypes.DUST_PLUME, pPos.getX(), pPos.getY() + 0.1, pPos.getZ(), 5, 0.2, 0.0, 0.2, 0.0);
          int randomEffect = RANDOM.nextInt(9);
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
              player.sendMessage(Text.literal("§4Something is watching from behind the trees..."), true);
              break;
            case 6:
              player.networkHandler.sendPacket(new GameStateChangeS2CPacket(GameStateChangeS2CPacket.DEMO_MESSAGE_SHOWN, 0));
              break;
            case 7:
              world.playSound(null, pPos, SoundEvents.BLOCK_FURNACE_FIRE_CRACKLE, SoundCategory.BLOCKS, 1.0f, 1.0f);
              break;
            case 8:
              world.playSound(null, pPos, SoundEvents.UI_BUTTON_CLICK.value(), SoundCategory.PLAYERS, 1.0f, 0.2f);
              break;
          }
        }
      }
    });
  }
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
  void spawnFigureAt(ServerPlayerEntity player, int mode, double x, double y, double z) {
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
    if (figureMode == FIGURE_NULL) {
      tickNull();
      return;
    }
    if (figureTicks % 5 == 0 && figureMode != FIGURE_BEDMAN) faceTarget();
    double dx = figureTarget.getX() - figure.getX();
    double dz = figureTarget.getZ() - figure.getZ();
    double dist = Math.sqrt(dx * dx + dz * dz);
    if (figureMode == FIGURE_STARE) {
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
      double nx = figure.getX() + dx / dist * 0.07;
      double nz = figure.getZ() + dz / dist * 0.07;
      double ny = figureWorld.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(nx), (int) Math.floor(nz));
      figure.setPosition(nx, ny, nz);
    } else if (figureMode == FIGURE_NIGHTMARE) {
      if (figureTicks >= 600) removeFigure(false);
    } else if (figureMode == FIGURE_BEDMAN) {
      if (figureTicks >= 1200 || dist < 3.5) removeFigure(true);
    } else if (figureMode == FIGURE_WAKE) {
      if (figureTicks >= 25) removeFigure(true);
    } else if (figureMode == FIGURE_WINDOW || figureMode == FIGURE_TREE) {
      if (figureTicks >= 1200) {
        removeFigure(true);
        return;
      }
      if (figureMode == FIGURE_TREE && dist < 7.0) {
        removeFigure(true);
        return;
      }
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
  double[] horizontalLook(ServerPlayerEntity p) {
    Vec3d look = p.getRotationVec(1.0f);
    double hx = look.x;
    double hz = look.z;
    double len = Math.sqrt(hx * hx + hz * hz);
    if (len < 0.01) {
      return new double[]{0.0, 1.0};
    }
    return new double[]{hx / len, hz / len};
  }
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
  private void tickNullScheduler(MinecraftServer server) {
    if (keepInvToggled && --keepInvTicks <= 0) {
      restoreKeepInventory(keepInvServer);
    }
    List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
    if (players.isEmpty()) return;
    if (nullCooldown > 0) {
      nullCooldown--;
      return;
    }
    if (figure != null || nightmareTimer > 0) {
      nullCooldown = 200;
      return;
    }
    ServerPlayerEntity target = players.get(RANDOM.nextInt(players.size()));
    if (target.isSpectator() || !target.isAlive()) {
      nullCooldown = 200;
      return;
    }
    nullCooldown = NULL_INTERVAL;
    startNullVisit(target);
  }
  private void startNullVisit(ServerPlayerEntity target) {
    boolean raid = RANDOM.nextInt(NULL_RAID_CHANCE) == 0;
    BlockPos spot = raid ? findStandingSpot(target, 14.0, 18.0) : findStandingSpot(target, 6.0, 10.0);
    if (spot == null) {
      nullCooldown = 600;
      return;
    }
    spawnFigureAt(target, FIGURE_NULL, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
    if (figure == null) return;
    figure.setCustomName(Text.literal("null"));
    figure.setCustomNameVisible(true);
    if (raid) {
      nullPhase = NULL_PHASE_RAID;
      nullTimer = NULL_CHASE_TICKS;
      figure.setAiDisabled(false);
      figure.setSilent(false);
      figure.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));
      target.sendMessage(Text.literal("§e<null> teleported to you."), false);
      figureWorld.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 1.5f, 0.6f);
    } else {
      Item[] items = {Items.DIAMOND, Items.EMERALD, Items.ENDER_PEARL, Items.GOLDEN_APPLE,
          Items.GOLD_INGOT, Items.BLAZE_ROD, Items.NETHERITE_INGOT};
      int[] counts = {2, 4, 2, 1, 8, 3, 1};
      String[] names = {"diamonds", "emeralds", "ender pearls", "golden apple",
          "gold ingots", "blaze rods", "netherite ingot"};
      int idx = RANDOM.nextInt(8) == 0 ? 6 : RANDOM.nextInt(6);
      nullItem = items[idx];
      nullItemCount = counts[idx];
      nullPhase = NULL_PHASE_WAIT;
      nullTimer = NULL_WAIT_TICKS;
      target.sendMessage(Text.literal("§7<null> §fBring me " + counts[idx] + " " + names[idx]
          + ". Drop them at my feet. You have 3 minutes."), false);
    }
  }
  private BlockPos findStandingSpot(ServerPlayerEntity p, double minDist, double maxDist) {
    ServerWorld w = p.getServerWorld();
    for (int i = 0; i < 30; i++) {
      double ang = RANDOM.nextDouble() * Math.PI * 2;
      double d = minDist + RANDOM.nextDouble() * (maxDist - minDist);
      int x = (int) Math.floor(p.getX() + Math.cos(ang) * d);
      int z = (int) Math.floor(p.getZ() + Math.sin(ang) * d);
      for (int dy = 3; dy >= -4; dy--) {
        BlockPos feet = new BlockPos(x, p.getBlockPos().getY() + dy, z);
        if (w.getBlockState(feet).isAir() && w.getBlockState(feet.up()).isAir()
            && !w.getBlockState(feet.down()).isAir()) {
          return feet;
        }
      }
    }
    return null;
  }
  private void tickNull() {
    ServerPlayerEntity p = figureTarget;
    ServerWorld w = figureWorld;
    nullTimer--;
    if (nullPhase == NULL_PHASE_WAIT) {
      if (figureTicks % 5 == 0) faceTarget();
      if (figureTicks % 10 == 0 && checkNullDelivery()) return;
      if (nullTimer == 2400 || nullTimer == 1200 || nullTimer == 600) {
        p.sendMessage(Text.literal("§7<null> §f" + (nullTimer / 20) + " seconds left."), false);
      }
      if (nullTimer <= 0) {
        nullPhase = NULL_PHASE_COUNTDOWN;
        nullTimer = 201;
        p.sendMessage(Text.literal("§4<null> You did not bring it."), false);
        w.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.HOSTILE, 2.0f, 0.6f);
      }
    } else if (nullPhase == NULL_PHASE_COUNTDOWN) {
      if (figureTicks % 5 == 0) faceTarget();
      if (nullTimer > 0 && nullTimer % 20 == 0) {
        int n = nullTimer / 20;
        p.sendMessage(Text.literal("§4§l" + n), false);
        w.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.HOSTILE,
            2.0f, 0.8f + (10 - n) * 0.06f);
      }
      if (nullTimer <= 0) startNullChase();
    } else if (nullPhase == NULL_PHASE_CHASE) {
      tickNullChase(p, w);
    } else if (nullPhase == NULL_PHASE_RAID) {
      tickNullRaid(p, w);
    }
  }
  private boolean checkNullDelivery() {
    if (nullItem == null) return false;
    Box box = figure.getBoundingBox().expand(3.0);
    List<ItemEntity> drops = figureWorld.getEntitiesByClass(ItemEntity.class, box,
        it -> it.getStack().isOf(nullItem));
    int total = 0;
    for (ItemEntity e : drops) {
      total += e.getStack().getCount();
    }
    if (total < nullItemCount) return false;
    int need = nullItemCount;
    for (ItemEntity e : drops) {
      if (need <= 0) break;
      ItemStack s = e.getStack();
      int take = Math.min(need, s.getCount());
      s.decrement(take);
      need -= take;
      if (s.isEmpty()) {
        e.discard();
      } else {
        e.setStack(s.copy());
      }
    }
    ServerPlayerEntity p = figureTarget;
    String[] lines = {"Task complete.", "Good. Do not make me ask twice.", "It is done. You are spared. For now."};
    p.sendMessage(Text.literal("§7<null> §f" + lines[RANDOM.nextInt(lines.length)]), false);
    figureWorld.playSound(null, figure.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 1.5f, 0.5f);
    removeFigure(true);
    return true;
  }
  private void startNullChase() {
    ServerPlayerEntity p = figureTarget;
    nullPhase = NULL_PHASE_CHASE;
    nullTimer = NULL_CHASE_TICKS;
    lockedPlayer = p;
    figure.setAiDisabled(false);
    figure.setSilent(false);
    p.networkHandler.sendPacket(new TitleFadeS2CPacket(0, 60, 10));
    p.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("§4§lRUN")));
    p.sendMessage(Text.literal("§4<null> RUN."), false);
    p.playSoundToPlayer(CHASE_MUSIC, SoundCategory.MASTER, 1.0f, 1.0f);
    figureWorld.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.HOSTILE, 2.0f, 0.2f);
  }
  private void tickNullChase(ServerPlayerEntity p, ServerWorld w) {
    double dist = figure.distanceTo(p);
    if (nullTimer > 0 && nullTimer % 20 == 0) {
      p.sendMessage(Text.literal("§4§lRUN §c" + (nullTimer / 20) + "s"), true);
    }
    if (dist < 1.8) {
      p.setHealth(Math.max(2.0f, p.getHealth() - 16.0f));
      w.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_PLAYER_HURT, SoundCategory.PLAYERS, 1.5f, 0.7f);
      p.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 60, 0));
      p.sendMessage(Text.literal("§4<null> Too slow."), false);
      removeFigure(true);
      return;
    }
    if (nullTimer <= 0) {
      p.sendMessage(Text.literal("§7<null> Not this time."), false);
      removeFigure(true);
      return;
    }
    if (figureTicks % 5 == 0) {
      if (dist > 28.0) {
        BlockPos spot = findStandingSpot(p, 14.0, 20.0);
        if (spot != null) {
          figure.refreshPositionAndAngles(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0.0f, 0.0f);
        }
      } else {
        figure.getNavigation().startMovingTo(p, 1.25);
      }
    }
  }
  private void tickNullRaid(ServerPlayerEntity p, ServerWorld w) {
    double dist = figure.distanceTo(p);
    if (dist < 1.8) {
      MinecraftServer server = p.getServer();
      if (server != null && !keepInvToggled) {
        GameRules.BooleanRule rule = server.getGameRules().get(GameRules.KEEP_INVENTORY);
        savedKeepInv = rule.get();
        rule.set(true, server);
        keepInvToggled = true;
        keepInvTicks = 72000;
        keepInvServer = server;
        raidVictim = p.getUuid();
      }
      p.damage(w, w.getDamageSources().mobAttack(figure), 1000.0f);
      removeFigure(false);
      return;
    }
    if (nullTimer <= 0) {
      removeFigure(true);
      return;
    }
    if (figureTicks % 5 == 0) {
      if (dist > 30.0) {
        BlockPos spot = findStandingSpot(p, 14.0, 18.0);
        if (spot != null) {
          figure.refreshPositionAndAngles(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0.0f, 0.0f);
        }
      } else {
        figure.getNavigation().startMovingTo(p, 1.45);
      }
    }
  }
  private void restoreKeepInventory(MinecraftServer server) {
    if (!keepInvToggled) return;
    if (server != null) {
      server.getGameRules().get(GameRules.KEEP_INVENTORY).set(savedKeepInv, server);
    }
    keepInvToggled = false;
    keepInvTicks = 0;
    raidVictim = null;
  }
  private void startFootsteps(ServerPlayerEntity p) {
    stepTarget = p;
    stepTicks = 600;
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
    if (speed < 0.03) return;
    if (stepCooldown > 0) return;
    stepCooldown = speed > 0.25 ? 4 : 8;
    ServerWorld w = p.getServerWorld();
    double[] h = horizontalLook(p);
    double x = p.getX() - h[0] * stepDist;
    double z = p.getZ() - h[1] * stepDist;
    stepDist = Math.max(3.0, stepDist - 0.15);
    BlockState below = w.getBlockState(p.getBlockPos().down());
    SoundEvent step = below.getSoundGroup().getStepSound();
    w.playSound(null, x, p.getY(), z, step, SoundCategory.PLAYERS, 0.9f, 0.8f);
  }
  private void figureScare() {
    ServerWorld world = figureWorld;
    ServerPlayerEntity p = figureTarget;
    world.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_SCREAM, SoundCategory.HOSTILE, 2.0f, 0.5f);
    p.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 40, 1));
    p.sendMessage(Text.literal("§4§l...it was right behind you."), true);
    removeFigure(true);
  }
  private void removeFigure(boolean poof) {
    if (figureMode == FIGURE_NULL && nullPhase == NULL_PHASE_CHASE && figureTarget != null) {
      figureTarget.networkHandler.sendPacket(
          new StopSoundS2CPacket(Identifier.of(MOD_ID, "chase_music"), SoundCategory.MASTER));
    }
    nullPhase = 0;
    lockedPlayer = null;
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
    spawnFigureAt(player, FIGURE_NIGHTMARE, 0.5, 201.0, 4.5);
    nightmareTimer = 200;
    nightmareTarget = player;
    player.sendMessage(Text.literal("§4..."), true);
  }
  private void executeNightmareJumpScare(ServerPlayerEntity player) {
    ServerWorld world = player.getServerWorld();
    world.setWeather(0, 1000, true, true);
    player.sendMessage(Text.literal("§4§lRUNN! RUNN! RUNN!"), false);
    world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.HOSTILE, 2.0f, 0.1f);
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
      figureTicks = 100;
    }
    player.removeStatusEffect(StatusEffects.SLOWNESS);
    player.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 100, 1));
  }
            }
