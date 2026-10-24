package com.korkumod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class CubeMod implements ModInitializer {
  private final CubeAi ai = new CubeAi();
  private final CubeCharacter cubes = new CubeCharacter();

  @Override
  public void onInitialize() {
    ai.loadConfig();

    CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
        dispatcher.register(CommandManager.literal("cube")
            .executes(ctx -> {
              ServerPlayerEntity p = ctx.getSource().getPlayer();
              if (p == null) return 0;
              cubes.spawn(p);
              p.sendMessage(Text.literal("§f<§bCubix§f> §fHello. I am Cubix. Talk to me in chat."), false);
              if (!ai.hasKey()) {
                p.sendMessage(Text.literal("§e[Cubix] API anahtari yok. Sunucuda config/korkumod-cube.properties dosyasina yaz, sonra /cube reload de."), false);
              }
              return 1;
            })
            .then(CommandManager.literal("off").executes(ctx -> {
              ServerPlayerEntity p = ctx.getSource().getPlayer();
              if (p == null) return 0;
              cubes.remove(p);
              return 1;
            }))
            .then(CommandManager.literal("reload").executes(ctx -> {
              ai.loadConfig();
              ServerPlayerEntity p = ctx.getSource().getPlayer();
              if (p != null) {
                p.sendMessage(Text.literal(ai.hasKey() ? "§a[Cubix] Ayarlar yuklendi, anahtar bulundu." : "§e[Cubix] Anahtar hala yok."), false);
              }
              return 1;
            }))));

    ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
      if (!cubes.has(sender)) return;
      String text = message.getSignedContent();
      if (text == null || text.isBlank()) return;
      ai.ask(sender.getServer(), sender, text, reply -> cubes.say(sender, reply));
    });

    ServerTickEvents.END_SERVER_TICK.register(server -> cubes.tick(server));
    ServerLifecycleEvents.SERVER_STARTED.register(server -> cubes.killOrphans(server));
    ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> cubes.remove(handler.getPlayer()));
  }
          }
