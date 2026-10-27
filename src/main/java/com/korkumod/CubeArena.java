package com.korkumod;

import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class CubeArena {
  private static final Random RANDOM = new Random();
  private static final int ARENA_Y = 120;
  private static final String[][] VAR = {
      {"acid", "lime_concrete", "green_terracotta", "shroomlight", "spider", "Acid Mantis", "4.0", "180", "7"},
      {"gravity", "purple_concrete", "black_concrete", "sea_lantern", "ravager", "Void Crusher", "2.2", "160", "8"},
      {"nest", "brown_terracotta", "terracotta", "glowstone", "silverfish", "Hive Titan", "9.0", "140", "9"}
  };

  private static class Run {
    UUID id;
    String name;
    CubeCharacter.Cube cube;
    ServerWorld origin;
    double rx;
    double ry;
    double rz;
    String[] var;
    String tag;
    int ax;
    int az;
    boolean ready = false;
    ServerBossBar bar;
    int ticks = 0;
  }

  private final CubeCharacter cubes;
  private final CubeCompanion companions;
  private final CubeTimer timer = new CubeTimer();
  private final Map<UUID, Run> runs = new HashMap<>();
  private int counter = 0;

  public CubeArena(CubeCharacter cubes, CubeCompanion companions) {
    this.cubes = cubes;
    this.companions = companions;
  }

  public boolean isIn(ServerPlayerEntity p) {
    return runs.containsKey(p.getUuid());
  }

  public void enter(MinecraftServer server, ServerPlayerEntity p, CubeCharacter.Cube cube) {
    if (runs.containsKey(p.getUuid())) return;
    counter++;
    Run r = new Run();
    r.id = p.getUuid();
    r.name = p.getName().getString();
    r.cube = cube;
    r.origin = p.getServerWorld();
    double yr = Math.toRadians(cube.yaw);
    double d = 0.7 * cube.g + 1.8;
    r.rx = cube.x - Math.sin(yr) * d;
    r.ry = cube.y;
    r.rz = cube.z + Math.cos(yr) * d;
    r.var = VAR[RANDOM.nextInt(VAR.length)];
    r.tag = "nxboss_" + counter + "_" + (System.currentTimeMillis() % 100000);
    r.ax = 6000 + counter * 100;
    r.az = 6000;
    runs.put(r.id, r);
    p.sendMessage(Text.literal("§d<" + CubeCharacter.NAME + "§d> §fEntering dimension: " + r.var[0] + ". Kill the guardian."), false);
    cmd(server, "forceload add " + (r.ax - 24) + " " + (r.az - 24) + " " + (r.ax + 24) + " " + (r.az + 24));
    timer.later(30, () -> build(server, r));
    timer.later(50, () -> start(server, p, r));
  }

  public boolean allowDeath(ServerPlayerEntity p) {
    Run r = runs.get(p.getUuid());
    if (r == null || !r.ready) return true;
    defeat(p.getServer(), p, r);
    return false;
  }

  public void tick(MinecraftServer server) {
    timer.tick();
    checkEnter(server);
    for (Run r : new ArrayList<>(runs.values())) {
      ServerPlayerEntity p = server.getPlayerManager().getPlayer(r.id);
      if (p == null) {
        cleanup(server, r);
        continue;
      }
      if (!r.ready) continue;
      r.ticks++;
      ServerWorld w = server.getOverworld();
      Box box = new Box(r.ax - 22, ARENA_Y - 2, r.az - 22, r.ax + 22, ARENA_Y + 16, r.az + 22);
      List<MobEntity> boss = w.getEntitiesByClass(MobEntity.class, box, e -> e.getCommandTags().contains(r.tag));
      if (boss.isEmpty()) {
        if (r.ticks > 40) victory(server, p, r);
        continue;
      }
      MobEntity b = boss.get(0);
      r.bar.setPercent(Math.max(0.0f, Math.min(1.0f, b.getHealth() / b.getMaxHealth())));
      companions.setTarget(p, b.getX(), b.getZ());
      if (r.var[0].equals("acid") && r.ticks % 80 == 0) {
        cmd(server, "effect give " + r.name + " minecraft:poison 4 0 true");
      } else if (r.var[0].equals("gravity") && r.ticks % 200 == 1) {
        cmd(server, "effect give " + r.name + " minecraft:jump_boost 12 3 true");
        cmd(server, "effect give " + r.name + " minecraft:slow_falling 12 0 true");
      } else if (r.var[0].equals("nest") && r.ticks % 120 == 0) {
        cmd(server, "effect give " + r.name + " minecraft:slowness 3 0 true");
      }
      if (p.getY() < ARENA_Y - 3) {
        cmd(server, "tp " + r.name + " " + (r.ax + 0.5) + " " + (ARENA_Y + 1) + " " + (r.az + 0.5));
      }
    }
  }

  public void removeAll(MinecraftServer server) {
    for (Run r : new ArrayList<>(runs.values())) {
      cleanup(server, r);
    }
  }

  private void checkEnter(MinecraftServer server) {
    for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
      if (runs.containsKey(p.getUuid())) continue;
      for (CubeCharacter.Cube c : cubes.all()) {
        if (!c.wide || c.g < 2.0 || c.world != p.getServerWorld()) continue;
        if (Math.abs(p.getX() - c.x) < 0.45 * c.g && Math.abs(p.getZ() - c.z) < 0.45 * c.g
            && p.getY() >= c.y - 0.2 && p.getY() <= c.y + 0.9 * c.g) {
          enter(server, p, c);
          break;
        }
      }
    }
  }

  private void build(MinecraftServer server, Run r) {
    int x1 = r.ax - 20;
    int x2 = r.ax + 20;
    int z1 = r.az - 20;
    int z2 = r.az + 20;
    int y2 = ARENA_Y + 13;
    cmd(server, "fill " + x1 + " " + ARENA_Y + " " + z1 + " " + x2 + " " + y2 + " " + z2 + " minecraft:" + r.var[2] + " hollow");
    cmd(server, "fill " + (x1 + 1) + " " + ARENA_Y + " " + (z1 + 1) + " " + (x2 - 1) + " " + ARENA_Y + " " + (z2 - 1) + " minecraft:" + r.var[1]);
    for (int x = x1 + 6; x <= x2 - 6; x += 14) {
      for (int z = z1 + 6; z <= z2 - 6; z += 14) {
        cmd(server, "setblock " + x + " " + (y2 - 1) + " " + z + " minecraft:" + r.var[3]);
      }
    }
  }

  private void start(MinecraftServer server, ServerPlayerEntity p, Run r) {
    if (!runs.containsKey(r.id) || p.isRemoved()) {
      cleanup(server, r);
      return;
    }
    cmd(server, "tp " + r.name + " " + (r.ax + 0.5) + " " + (ARENA_Y + 1) + " " + (r.az + 0.5));
    String nbt = "{Tags:[\"" + r.tag + "\"],PersistenceRequired:1b,CustomName:\"" + r.var[5] + "\",CustomNameVisible:1b,Health:" + r.var[7] + "f,"
        + "attributes:[{id:\"minecraft:scale\",base:" + r.var[6] + "d},{id:\"minecraft:max_health\",base:" + r.var[7] + "d},"
        + "{id:\"minecraft:attack_damage\",base:" + r.var[8] + "d}]}";
    cmd(server, "summon minecraft:" + r.var[4] + " " + (r.ax + 0.5) + " " + (ARENA_Y + 1) + " " + (r.az + 10.5) + " " + nbt);
    String minion = r.var[0].equals("acid") ? "cave_spider" : r.var[0].equals("nest") ? "endermite" : null;
    if (minion != null) {
      for (int i = 0; i < 3; i++) {
        cmd(server, "summon minecraft:" + minion + " " + (r.ax - 8 + i * 8) + " " + (ARENA_Y + 1) + " " + (r.az + 4)
            + " {Tags:[\"" + r.tag + "m\"],PersistenceRequired:1b}");
      }
    }
    r.bar = new ServerBossBar(Text.literal(r.var[5]), BossBar.Color.PURPLE, BossBar.Style.PROGRESS);
    r.bar.addPlayer(p);
    companions.spawn(server, p);
    r.ready = true;
  }

  private void victory(MinecraftServer server, ServerPlayerEntity p, Run r) {
    p.sendMessage(Text.literal("§d<" + CubeCharacter.NAME + "§d> §fGuardian eliminated. Return."), false);
    cmd(server, "effect clear " + r.name);
    cmdIn(server, r.origin, "tp " + r.name + " " + r.rx + " " + r.ry + " " + r.rz);
    cubes.shrink(server, r.cube);
    cubes.reward(server, r.cube, p);
    cleanup(server, r);
  }

  private void defeat(MinecraftServer server, ServerPlayerEntity p, Run r) {
    p.setHealth(RANDOM.nextBoolean() ? 2.0f : 4.0f);
    p.sendMessage(Text.literal("§d<" + CubeCharacter.NAME + "§d> §fYou failed. Get out."), false);
    cmd(server, "effect clear " + r.name);
    double ang = RANDOM.nextDouble() * Math.PI * 2;
    double dist = 150 + RANDOM.nextInt(100);
    int cx = (int) (r.rx + Math.cos(ang) * dist);
    int cz = (int) (r.rz + Math.sin(ang) * dist);
    cmdIn(server, r.origin, "spreadplayers " + cx + " " + cz + " 5 25 false " + r.name);
    cubes.shrink(server, r.cube);
    cleanup(server, r);
  }

  private void cleanup(MinecraftServer server, Run r) {
    runs.remove(r.id);
    r.ready = false;
    if (r.bar != null) {
      r.bar.clearPlayers();
    }
    ServerPlayerEntity p = server.getPlayerManager().getPlayer(r.id);
    if (p != null) {
      companions.clearTarget(p);
      companions.remove(server, p);
    }
    cmd(server, "kill @e[tag=" + r.tag + "]");
    cmd(server, "kill @e[tag=" + r.tag + "m]");
    timer.later(40, () -> {
      cmd(server, "fill " + (r.ax - 20) + " " + ARENA_Y + " " + (r.az - 20) + " " + (r.ax + 20) + " " + (ARENA_Y + 13) + " " + (r.az + 20) + " minecraft:air");
      cmd(server, "forceload remove " + (r.ax - 24) + " " + (r.az - 24) + " " + (r.ax + 24) + " " + (r.az + 24));
    });
  }

  private void cmd(MinecraftServer server, String c) {
    cmdIn(server, server.getOverworld(), c);
  }

  private void cmdIn(MinecraftServer server, ServerWorld world, String c) {
    String dim = world.getRegistryKey().getValue().toString();
    server.getCommandManager().executeWithPrefix(
        server.getCommandSource().withSilent(), "execute in " + dim + " run " + c);
  }
        }
