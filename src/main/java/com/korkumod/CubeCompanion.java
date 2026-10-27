package com.korkumod;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class CubeCompanion {
  private static class Comp {
    final String tag;
    ServerWorld world;
    double x;
    double y;
    double z;
    int age = 0;
    boolean up = false;
    boolean hasTarget = false;
    double bx = 0;
    double bz = 0;

    Comp(String tag, ServerWorld world) {
      this.tag = tag;
      this.world = world;
    }
  }

  private final Map<UUID, Comp> comps = new HashMap<>();
  private int counter = 0;

  public boolean has(ServerPlayerEntity p) {
    return comps.containsKey(p.getUuid());
  }

  public void spawn(MinecraftServer server, ServerPlayerEntity p) {
    remove(server, p);
    counter++;
    Comp c = new Comp("noxbuddy_" + counter + "_" + (System.currentTimeMillis() % 100000), p.getServerWorld());
    double[] t = target(p, 0);
    c.x = t[0];
    c.y = t[1];
    c.z = t[2];
    summonAll(server, c);
    comps.put(p.getUuid(), c);
  }

  public void remove(MinecraftServer server, ServerPlayerEntity p) {
    Comp c = comps.remove(p.getUuid());
    if (c != null) {
      run(server, c.world, "kill @e[tag=" + c.tag + "]");
    }
  }

  public void removeAll(MinecraftServer server) {
    for (Comp c : new ArrayList<>(comps.values())) {
      run(server, c.world, "kill @e[tag=" + c.tag + "]");
    }
    comps.clear();
  }

  public void setTarget(ServerPlayerEntity p, double x, double z) {
    Comp c = comps.get(p.getUuid());
    if (c != null) {
      c.hasTarget = true;
      c.bx = x;
      c.bz = z;
    }
  }

  public void clearTarget(ServerPlayerEntity p) {
    Comp c = comps.get(p.getUuid());
    if (c != null) {
      c.hasTarget = false;
    }
  }

  public void tick(MinecraftServer server) {
    if (comps.isEmpty()) return;
    Iterator<Map.Entry<UUID, Comp>> it = comps.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry<UUID, Comp> e = it.next();
      Comp c = e.getValue();
      ServerPlayerEntity p = server.getPlayerManager().getPlayer(e.getKey());
      if (p == null) {
        run(server, c.world, "kill @e[tag=" + c.tag + "]");
        it.remove();
        continue;
      }
      if (p.isRemoved()) continue;
      if (p.getServerWorld() != c.world) {
        run(server, c.world, "kill @e[tag=" + c.tag + "]");
        c.world = p.getServerWorld();
        double[] t = target(p, c.age);
        c.x = t[0];
        c.y = t[1];
        c.z = t[2];
        summonAll(server, c);
        continue;
      }
      c.age++;
      if (c.age % 2 == 0) {
        double[] t = target(p, c.age);
        double yaw = Math.toDegrees(Math.atan2(-(p.getX() - t[0]), p.getZ() - t[2]));
        double nyaw = c.hasTarget ? Math.toDegrees(Math.atan2(-(c.bx - t[0]), c.bz - t[2])) : yaw;
        run(server, c.world, "tp @e[tag=" + c.tag + ",tag=!c_needle] " + f(t[0]) + " " + f(t[1]) + " " + f(t[2]) + " " + f(yaw) + " 0");
        run(server, c.world, "tp @e[tag=" + c.tag + ",tag=c_needle] " + f(t[0]) + " " + f(t[1]) + " " + f(t[2]) + " " + f(nyaw) + " 0");
        c.x = t[0];
        c.y = t[1];
        c.z = t[2];
      }
      if (c.age % 6 == 0) {
        c.up = !c.up;
        wings(server, c, c.up ? 0.9 : 0.25, 5);
      }
    }
  }

  private double[] target(ServerPlayerEntity p, int age) {
    double yaw = Math.toRadians(p.getYaw());
    double rx = -Math.cos(yaw);
    double rz = -Math.sin(yaw);
    double fx = -Math.sin(yaw);
    double fz = Math.cos(yaw);
    double bob = Math.sin(age * 0.15) * 0.1;
    return new double[]{p.getX() + rx * 1.0 + fx * 0.2, p.getY() + 2.1 + bob, p.getZ() + rz * 1.0 + fz * 0.2};
  }

  private void summonAll(MinecraftServer server, Comp c) {
    part(server, c, "c_body", "cyan_concrete", tf(-0.18, -0.18, -0.18, 0.36, 0.36, 0.36, 0.0));
    part(server, c, "c_eye", "white_concrete", tf(-0.09, -0.05, 0.175, 0.18, 0.16, 0.02, 0.0));
    part(server, c, "c_pup", "black_concrete", tf(-0.04, -0.02, 0.19, 0.08, 0.10, 0.02, 0.0));
    part(server, c, "c_wingR", "yellow_concrete", wingTf(false, 0.25));
    part(server, c, "c_wingL", "yellow_concrete", wingTf(true, 0.25));
    part(server, c, "c_disc", "gray_concrete", tf(-0.09, -0.40, -0.09, 0.18, 0.03, 0.18, 0.0));
    part(server, c, "c_stick", "black_concrete", tf(-0.01, -0.40, -0.01, 0.02, 0.22, 0.02, 0.0));
    part(server, c, "c_needle", "red_concrete", tf(-0.012, -0.37, 0.0, 0.024, 0.012, 0.14, 0.0));
  }

  private void part(MinecraftServer server, Comp c, String name, String block, String tf) {
    String nbt = "{block_state:{Name:\"minecraft:" + block + "\"},"
        + "Tags:[\"korkumod_cube\",\"" + c.tag + "\",\"" + name + "\"],"
        + "teleport_duration:3,brightness:{sky:15,block:15},transformation:" + tf + "}";
    run(server, c.world, "summon minecraft:block_display " + f(c.x) + " " + f(c.y) + " " + f(c.z) + " " + nbt);
  }

  private void wings(MinecraftServer server, Comp c, double phi, int dur) {
    run(server, c.world, "data merge entity @e[tag=" + c.tag + ",tag=c_wingR,limit=1] "
        + "{start_interpolation:0,interpolation_duration:" + dur + ",transformation:" + wingTf(false, phi) + "}");
    run(server, c.world, "data merge entity @e[tag=" + c.tag + ",tag=c_wingL,limit=1] "
        + "{start_interpolation:0,interpolation_duration:" + dur + ",transformation:" + wingTf(true, phi) + "}");
  }

  private String wingTf(boolean left, double phi) {
    if (!left) {
      return tf(0.18, 0.1, -0.1, 0.45, 0.03, 0.2, phi);
    }
    return tf(-0.18 - 0.45 * Math.cos(phi), 0.1 + 0.45 * Math.sin(phi), -0.1, 0.45, 0.03, 0.2, -phi);
  }

  private String tf(double tx, double ty, double tz, double sx, double sy, double sz, double rz) {
    return "{translation:[" + f(tx) + "f," + f(ty) + "f," + f(tz) + "f],"
        + "scale:[" + f(sx) + "f," + f(sy) + "f," + f(sz) + "f],"
        + "left_rotation:[0f,0f," + f(Math.sin(rz / 2)) + "f," + f(Math.cos(rz / 2)) + "f],"
        + "right_rotation:[0f,0f,0f,1f]}";
  }

  private void run(MinecraftServer server, ServerWorld world, String cmd) {
    String dim = world.getRegistryKey().getValue().toString();
    server.getCommandManager().executeWithPrefix(
        server.getCommandSource().withSilent(), "execute in " + dim + " run " + cmd);
  }

  private static String f(double v) {
    return String.format(Locale.ROOT, "%.4f", v);
  }
  }
