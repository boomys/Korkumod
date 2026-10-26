package com.korkumod;

import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public class CubeCharacter {
  public static final String NAME = "Vesper";
  private static final double HB = 0.3;
  private static final double HL = 0.5;

  private static final String[] LID_NAME = {"v_lid", "v_lidin", "v_eyeL", "v_eyeR", "v_pupL", "v_pupR"};
  private static final String[] LID_BLOCK = {"red_concrete", "black_concrete", "white_concrete", "white_concrete", "black_concrete", "black_concrete"};
  private static final double[][] LID_GEO = {
      {-0.4, 0.0, 0.0, 0.8, HL, 0.8},
      {-0.34, -0.012, 0.06, 0.68, 0.014, 0.68},
      {-0.3, 0.14, 0.795, 0.2, 0.28, 0.02},
      {0.1, 0.14, 0.795, 0.2, 0.28, 0.02},
      {-0.24, 0.18, 0.81, 0.11, 0.22, 0.02},
      {0.16, 0.18, 0.81, 0.11, 0.22, 0.02}
  };

  public static class Cube {
    final String tag;
    final ServerWorld world;
    final double x;
    final double y;
    final double z;
    double yaw;
    double g = 1.0;
    double lid = 0.0;
    int talkTicks = 0;
    boolean mouthOpen = false;
    boolean wide = false;

    Cube(String tag, ServerWorld world, double x, double y, double z, double yaw) {
      this.tag = tag;
      this.world = world;
      this.x = x;
      this.y = y;
      this.z = z;
      this.yaw = yaw;
    }
  }

  private static class Later {
    int ticks;
    final Runnable action;

    Later(int ticks, Runnable action) {
      this.ticks = ticks;
      this.action = action;
    }
  }

  private final List<Cube> cubes = new ArrayList<>();
  private final List<Later> laters = new ArrayList<>();
  private int counter = 0;

  public Cube place(ServerPlayerEntity p, double x, double y, double z) {
    MinecraftServer server = p.getServer();
    double yaw = Math.toDegrees(Math.atan2(-(p.getX() - x), p.getZ() - z));
    counter++;
    Cube c = new Cube("vesper_" + counter + "_" + (System.currentTimeMillis() % 100000), p.getServerWorld(), x, y, z, yaw);
    summonAll(server, c);
    cubes.add(c);
    run(server, c.world, "particle minecraft:poof " + f(x) + " " + f(y + 0.4) + " " + f(z) + " 0.3 0.3 0.3 0.05 25 normal");
    return c;
  }

  public Cube nearest(ServerPlayerEntity p, double maxDist) {
    Cube best = null;
    double bestD = maxDist * maxDist;
    for (Cube c : cubes) {
      if (c.world != p.getServerWorld()) continue;
      double d = p.squaredDistanceTo(c.x, c.y, c.z);
      if (d < bestD) {
        bestD = d;
        best = c;
      }
    }
    return best;
  }

  public Cube byEntity(Entity e) {
    for (Cube c : cubes) {
      if (e.getCommandTags().contains(c.tag)) return c;
    }
    return null;
  }

  public void remove(MinecraftServer server, Cube c) {
    cubes.remove(c);
    run(server, c.world, "kill @e[tag=" + c.tag + "]");
  }

  public void removeAll(MinecraftServer server) {
    for (Cube c : new ArrayList<>(cubes)) {
      remove(server, c);
    }
  }

  public void killOrphans(MinecraftServer server) {
    for (ServerWorld w : server.getWorlds()) {
      run(server, w, "kill @e[tag=korkumod_cube]");
    }
  }

  public void speak(MinecraftServer server, Cube c, String text) {
    server.getPlayerManager().broadcast(Text.literal("§f<§c" + NAME + "§f> §f" + text), false);
    c.talkTicks = Math.min(240, 24 + text.length() * 2);
  }

  public void say(Cube c, ServerPlayerEntity asker, String text) {
    MinecraftServer server = asker.getServer();
    c.yaw = Math.toDegrees(Math.atan2(-(asker.getX() - c.x), asker.getZ() - c.z));
    run(server, c.world, "tp @e[tag=" + c.tag + "] " + f(c.x) + " " + f(c.y) + " " + f(c.z) + " " + f(c.yaw) + " 0");
    speak(server, c, text);
  }

  public void grow(MinecraftServer server, Cube c) {
    c.g = 2.6;
    c.mouthOpen = false;
    updateAll(server, c, c.lid, 80);
    later(95, () -> speak(server, c, "I like this form."));
    later(160, () -> speak(server, c, "I know I am asking a lot of you."));
    later(230, () -> {
      speak(server, c, "But you must enter my mouth.");
      c.wide = true;
      updateLid(server, c, -1.75, 25);
    });
  }

  public void shrink(MinecraftServer server, Cube c) {
    c.g = 1.0;
    c.wide = false;
    c.mouthOpen = false;
    updateAll(server, c, 0.0, 50);
  }

  public void tick(MinecraftServer server) {
    Iterator<Later> li = laters.iterator();
    List<Runnable> due = new ArrayList<>();
    while (li.hasNext()) {
      Later l = li.next();
      if (--l.ticks <= 0) {
        due.add(l.action);
        li.remove();
      }
    }
    for (Runnable r : due) {
      r.run();
    }

    for (Cube c : cubes) {
      if (c.talkTicks > 0) {
        c.talkTicks--;
        if (c.wide) continue;
        if (c.talkTicks == 0) {
          updateLid(server, c, 0.0, 3);
          c.mouthOpen = false;
        } else if (c.talkTicks % 4 == 0) {
          c.mouthOpen = !c.mouthOpen;
          updateLid(server, c, c.mouthOpen ? -0.5 : 0.0, 3);
        }
      }
    }
  }

  private void later(int ticks, Runnable r) {
    laters.add(new Later(ticks, r));
  }

  private void summonAll(MinecraftServer server, Cube c) {
    summonPart(server, c, "v_base", "red_concrete", baseTf("v_base", c.g));
    summonPart(server, c, "v_basein", "black_concrete", baseTf("v_basein", c.g));
    for (int i = 0; i < LID_NAME.length; i++) {
      summonPart(server, c, LID_NAME[i], LID_BLOCK[i], lidTf(LID_GEO[i], c.g, c.lid));
    }
    run(server, c.world, "summon minecraft:interaction " + f(c.x) + " " + f(c.y) + " " + f(c.z)
        + " {width:" + f(0.8 * c.g) + "f,height:" + f((HB + HL) * c.g) + "f,response:1b,"
        + "Tags:[\"korkumod_cube\",\"" + c.tag + "\",\"v_hit\"]}");
  }

  private void summonPart(MinecraftServer server, Cube c, String name, String block, String tf) {
    String nbt = "{block_state:{Name:\"minecraft:" + block + "\"},Rotation:[" + f(c.yaw) + "f,0f],"
        + "Tags:[\"korkumod_cube\",\"" + c.tag + "\",\"" + name + "\"],"
        + "teleport_duration:3,brightness:{sky:15,block:15},transformation:" + tf + "}";
    run(server, c.world, "summon minecraft:block_display " + f(c.x) + " " + f(c.y) + " " + f(c.z) + " " + nbt);
  }

  private void updateLid(MinecraftServer server, Cube c, double theta, int dur) {
    for (int i = 0; i < LID_NAME.length; i++) {
      merge(server, c, LID_NAME[i], lidTf(LID_GEO[i], c.g, theta), dur);
    }
    c.lid = theta;
  }

  private void updateAll(MinecraftServer server, Cube c, double theta, int dur) {
    merge(server, c, "v_base", baseTf("v_base", c.g), dur);
    merge(server, c, "v_basein", baseTf("v_basein", c.g), dur);
    updateLid(server, c, theta, dur);
    run(server, c.world, "data merge entity @e[tag=" + c.tag + ",tag=v_hit,limit=1] {width:"
        + f(0.8 * c.g) + "f,height:" + f((HB + HL) * c.g) + "f}");
  }

  private void merge(MinecraftServer server, Cube c, String part, String tf, int dur) {
    run(server, c.world, "data merge entity @e[tag=" + c.tag + ",tag=" + part + ",limit=1] "
        + "{start_interpolation:0,interpolation_duration:" + dur + ",transformation:" + tf + "}");
  }

  private String baseTf(String part, double g) {
    if (part.equals("v_base")) {
      return tf(g * -0.4, 0.0, g * -0.4, g * 0.8, g * HB, g * 0.8, 0.0);
    }
    return tf(g * -0.34, g * (HB - 0.005), g * -0.34, g * 0.68, g * 0.02, g * 0.68, 0.0);
  }

  private String lidTf(double[] geo, double g, double theta) {
    double cs = Math.cos(theta);
    double sn = Math.sin(theta);
    double ox = geo[0];
    double oy = geo[1];
    double oz = geo[2];
    double ry = oy * cs - oz * sn;
    double rz = oy * sn + oz * cs;
    return tf(g * ox, g * (HB + ry), g * (-0.4 + rz), g * geo[3], g * geo[4], g * geo[5], theta);
  }

  private String tf(double tx, double ty, double tz, double sx, double sy, double sz, double theta) {
    return "{translation:[" + f(tx) + "f," + f(ty) + "f," + f(tz) + "f],"
        + "scale:[" + f(sx) + "f," + f(sy) + "f," + f(sz) + "f],"
        + "left_rotation:[" + f(Math.sin(theta / 2)) + "f,0f,0f," + f(Math.cos(theta / 2)) + "f],"
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
