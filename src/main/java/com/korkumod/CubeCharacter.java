package com.korkumod;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class CubeCharacter {
  private static class Cube {
    final String tag;
    ServerWorld world;
    double x;
    double y;
    double z;
    int age = 0;
    int talkTicks = 0;
    boolean mouthOpen = false;

    Cube(String tag, ServerWorld world) {
      this.tag = tag;
      this.world = world;
    }
  }

  private final Map<UUID, Cube> cubes = new HashMap<>();

  public boolean has(ServerPlayerEntity p) {
    return cubes.containsKey(p.getUuid());
  }

  public void spawn(ServerPlayerEntity p) {
    remove(p);
    MinecraftServer server = p.getServer();
    String tag = "cube_" + p.getUuidAsString().replace("-", "").substring(0, 12);
    Cube c = new Cube(tag, p.getServerWorld());
    Vec3d t = targetFor(p, 0);
    c.x = t.x;
    c.y = t.y;
    c.z = t.z;
    summonParts(server, c);
    cubes.put(p.getUuid(), c);
  }

  public void remove(ServerPlayerEntity p) {
    Cube c = cubes.remove(p.getUuid());
    if (c != null) {
      kill(p.getServer(), c);
    }
  }

  public void killOrphans(MinecraftServer server) {
    for (ServerWorld w : server.getWorlds()) {
      run(server, w, "kill @e[tag=korkumod_cube]");
    }
  }

  public void say(ServerPlayerEntity p, String text) {
    MinecraftServer server = p.getServer();
    server.getPlayerManager().broadcast(Text.literal("§f<§bCubix§f> §f" + text), false);
    Cube c = cubes.get(p.getUuid());
    if (c != null) {
      c.talkTicks = Math.min(240, 24 + text.length() * 2);
    }
  }

  public void tick(MinecraftServer server) {
    if (cubes.isEmpty()) return;
    Iterator<Map.Entry<UUID, Cube>> it = cubes.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry<UUID, Cube> e = it.next();
      Cube c = e.getValue();
      ServerPlayerEntity p = server.getPlayerManager().getPlayer(e.getKey());
      if (p == null) {
        kill(server, c);
        it.remove();
        continue;
      }
      if (p.isRemoved()) continue;

      if (p.getServerWorld() != c.world) {
        kill(server, c);
        c.world = p.getServerWorld();
        Vec3d t = targetFor(p, c.age);
        c.x = t.x;
        c.y = t.y;
        c.z = t.z;
        summonParts(server, c);
        continue;
      }

      c.age++;
      if (c.age % 2 == 0) {
        Vec3d t = targetFor(p, c.age);
        double yaw = Math.toDegrees(Math.atan2(-(p.getX() - t.x), p.getZ() - t.z));
        run(server, c.world, "tp @e[tag=" + c.tag + "] " + f(t.x) + " " + f(t.y) + " " + f(t.z) + " " + f(yaw) + " 0");
        c.x = t.x;
        c.y = t.y;
        c.z = t.z;
      }

      if (c.talkTicks > 0) {
        c.talkTicks--;
        if (c.talkTicks == 0) {
          setMouth(server, c, false);
        } else if (c.talkTicks % 4 == 0) {
          setMouth(server, c, !c.mouthOpen);
        }
      }
    }
  }

  private Vec3d targetFor(ServerPlayerEntity p, int age) {
    double yaw = Math.toRadians(p.getYaw());
    double rx = -Math.cos(yaw);
    double rz = -Math.sin(yaw);
    double fx = -Math.sin(yaw);
    double fz = Math.cos(yaw);
    double bob = Math.sin(age * 0.12) * 0.12;
    return new Vec3d(p.getX() + rx * 1.3 + fx * 0.6, p.getY() + 1.9 + bob, p.getZ() + rz * 1.3 + fz * 0.6);
  }

  private void summonParts(MinecraftServer server, Cube c) {
    part(server, c, "p_body", "white_concrete", -0.4, -0.4, -0.4, 0.8, 0.8, 0.8);
    part(server, c, "p_eyeL_f", "black_concrete", -0.26, -0.02, 0.395, 0.2, 0.2, 0.02);
    part(server, c, "p_eyeR_f", "black_concrete", 0.06, -0.02, 0.395, 0.2, 0.2, 0.02);
    part(server, c, "p_eyeL", "white_concrete", -0.235, 0.005, 0.41, 0.15, 0.15, 0.02);
    part(server, c, "p_eyeR", "white_concrete", 0.085, 0.005, 0.41, 0.15, 0.15, 0.02);
    part(server, c, "p_mouth", "black_concrete", -0.15, -0.18, 0.395, 0.3, 0.03, 0.02);
    c.mouthOpen = false;
  }

  private void part(MinecraftServer server, Cube c, String name, String block,
                    double tx, double ty, double tz, double sx, double sy, double sz) {
    String nbt = "{block_state:{Name:\"minecraft:" + block + "\"},"
        + "Tags:[\"korkumod_cube\",\"" + c.tag + "\",\"" + name + "\"],"
        + "teleport_duration:3,brightness:{sky:15,block:15},"
        + "transformation:" + tf(tx, ty, tz, sx, sy, sz) + "}";
    run(server, c.world, "summon minecraft:block_display " + f(c.x) + " " + f(c.y) + " " + f(c.z) + " " + nbt);
  }

  private void setMouth(MinecraftServer server, Cube c, boolean open) {
    String tr = open ? tf(-0.15, -0.28, 0.395, 0.3, 0.15, 0.02) : tf(-0.15, -0.18, 0.395, 0.3, 0.03, 0.02);
    run(server, c.world, "data merge entity @e[tag=" + c.tag + ",tag=p_mouth,limit=1] "
        + "{start_interpolation:0,interpolation_duration:3,transformation:" + tr + "}");
    c.mouthOpen = open;
  }

  private void kill(MinecraftServer server, Cube c) {
    run(server, c.world, "kill @e[tag=" + c.tag + "]");
  }

  private void run(MinecraftServer server, ServerWorld world, String cmd) {
    String dim = world.getRegistryKey().getValue().toString();
    server.getCommandManager().executeWithPrefix(
        server.getCommandSource().withSilent(), "execute in " + dim + " run " + cmd);
  }

  private String tf(double tx, double ty, double tz, double sx, double sy, double sz) {
    return "{translation:[" + f(tx) + "f," + f(ty) + "f," + f(tz) + "f],"
        + "scale:[" + f(sx) + "f," + f(sy) + "f," + f(sz) + "f],"
        + "left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f]}";
  }

  private static String f(double v) {
    return String.format(Locale.ROOT, "%.3f", v);
  }
}
