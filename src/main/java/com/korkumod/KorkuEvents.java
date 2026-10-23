package com.korkumod;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.enums.BedPart;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.ChickenEntity;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
public class KorkuEvents {
  private static final Random RANDOM = new Random();
  private final KorkuMod mod;
  private final List<TempBlock> tempBlocks = new ArrayList<>();
  private final List<SoundTask> soundTasks = new ArrayList<>();
  private ServerWorld eyeWorld = null;
  private int eyeTicks = 0;
  private double eyeX = 0;
  private double eyeY = 0;
  private double eyeZ = 0;
  private double eyeDx = 0;
  private double eyeDz = 0;
  private ServerWorld darkWorld = null;
  private int darkTicks = 0;
  private long darkOriginal = 0;
  public KorkuEvents(KorkuMod mod) {
    this.mod = mod;
  }
  public void tick() {
    tickTempBlocks();
    tickSoundTasks();
    tickEyes();
    tickDarkness();
  }
  public boolean runRandomEvent(ServerPlayerEntity p) {
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
  private boolean eventLightning(ServerPlayerEntity p) {
    ServerWorld w = p.getServerWorld();
    if (mod.figure != null || !w.isSkyVisible(p.getBlockPos())) return false;
    double[] h = mod.horizontalLook(p);
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
    mod.spawnFigureAt(p, KorkuMod.FIGURE_STARE, x, y, z);
    return true;
  }
  private boolean eventBloodTrail(ServerPlayerEntity p) {
    ServerWorld w = p.getServerWorld();
    double[] h = mod.horizontalLook(p);
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
  private boolean eventTreeFigure(ServerPlayerEntity p) {
    ServerWorld w = p.getServerWorld();
    if (mod.figure != null || !w.isSkyVisible(p.getBlockPos())) return false;
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
          double fx = log.getX() + 0.5 + ux * 1.0 + (-uz) * 0.5;
          double fz = log.getZ() + 0.5 + uz * 1.0 + ux * 0.5;
          BlockPos feet = BlockPos.ofFloored(fx, log.getY(), fz);
          if (w.getBlockState(feet).isAir() && w.getBlockState(feet.up()).isAir()
              && !w.getBlockState(feet.down()).isAir()) {
            mod.spawnFigureAt(p, KorkuMod.FIGURE_TREE, fx, feet.getY(), fz);
            return true;
          }
        }
      }
    }
    return false;
  }
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
  private boolean eventTitle(ServerPlayerEntity p) {
    String[] texts = {"§4§lLOOK", "§4§lBEHIND YOU", "§c§lI SEE YOU"};
    String text = texts[RANDOM.nextInt(texts.length)];
    p.networkHandler.sendPacket(new TitleFadeS2CPacket(0, 15, 5));
    p.networkHandler.sendPacket(new TitleS2CPacket(Text.literal(text)));
    return true;
  }
  private boolean eventNote(ServerPlayerEntity p) {
    String[] titles = {"§4Don't turn around", "§4I am watching you", "§4You can't escape"};
    String[] lores = {"§7You did not write this.", "§7I am right beside you.", "§7I am still here."};
    int i = RANDOM.nextInt(titles.length);
    ItemStack note = new ItemStack(Items.PAPER);
    note.set(DataComponentTypes.CUSTOM_NAME, Text.literal(titles[i]));
    note.set(DataComponentTypes.LORE, new LoreComponent(List.of(Text.literal(lores[RANDOM.nextInt(lores.length)]))));
    p.getInventory().insertStack(note);
    return true;
  }
  private boolean eventRedEyes(ServerPlayerEntity p) {
    ServerWorld w = p.getServerWorld();
    if (eyeTicks > 0 || w.isSkyVisible(p.getBlockPos())) return false;
    double[] h = mod.horizontalLook(p);
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
  private boolean eventStorm(ServerPlayerEntity p) {
    ServerWorld w = p.getServerWorld();
    if (w.isRaining() || w.getRegistryKey() != World.OVERWORLD) return false;
    w.setWeather(0, 400, true, true);
    w.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.WEATHER, 6.0f, 0.8f);
    return true;
  }
  private boolean eventBedMan(ServerPlayerEntity p) {
    ServerWorld w = p.getServerWorld();
    if (mod.figure != null || w.isSkyVisible(p.getBlockPos())) return false;
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
            if (ex * ex + ez * ez > 16.0) {
              beds.add(b);
            }
          }
        }
      }
    }
    if (beds.isEmpty()) return false;
    BlockPos bed = beds.get(RANDOM.nextInt(beds.size()));
    mod.spawnFigureAt(p, KorkuMod.FIGURE_BEDMAN, bed.getX() + 0.5, bed.getY() + 0.5625, bed.getZ() + 0.5);
    if (mod.figure != null) {
      mod.figure.setNoGravity(true);
      mod.figure.setSleepingPosition(bed);
    }
    return true;
  }
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
                                    }
