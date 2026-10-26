package com.korkumod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public class CubeMod implements ModInitializer {
  public static final RegistryKey<Item> VESPER_KEY =
      RegistryKey.of(RegistryKeys.ITEM, Identifier.of("korkumod", "vesper_cube"));
  public static final Item VESPER_ITEM = Registry.register(
      Registries.ITEM, VESPER_KEY,
      new Item(new Item.Settings().maxCount(1).registryKey(VESPER_KEY)));

  private final CubeAi ai = new CubeAi();
  private final CubeCharacter cubes = new CubeCharacter();

  @Override
  public void onInitialize() {
    ai.loadConfig();

    CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
        dispatcher.register(CommandManager.literal("vesper")
            .executes(ctx -> {
              ServerPlayerEntity p = ctx.getSource().getPlayer();
              if (p == null) return 0;
              p.getInventory().offerOrDrop(new ItemStack(VESPER_ITEM));
              p.sendMessage(Text.literal("§c[Vesper] Vesper Cube envanterine eklendi. Duz bir yere sag tikla."), false);
              if (!ai.hasKey()) {
                p.sendMessage(Text.literal("§e[Vesper] API anahtari yok. Sunucuda config/korkumod-cube.properties dosyasina yaz, sonra /vesper reload de."), false);
              }
              return 1;
            })
            .then(CommandManager.literal("grow").executes(ctx -> {
              ServerPlayerEntity p = ctx.getSource().getPlayer();
              if (p == null) return 0;
              CubeCharacter.Cube c = cubes.nearest(p, 24.0);
              if (c == null) return 0;
              cubes.grow(p.getServer(), c);
              return 1;
            }))
            .then(CommandManager.literal("shrink").executes(ctx -> {
              ServerPlayerEntity p = ctx.getSource().getPlayer();
              if (p == null) return 0;
              CubeCharacter.Cube c = cubes.nearest(p, 24.0);
              if (c == null) return 0;
              cubes.shrink(p.getServer(), c);
              return 1;
            }))
            .then(CommandManager.literal("remove").executes(ctx -> {
              ServerPlayerEntity p = ctx.getSource().getPlayer();
              if (p == null) return 0;
              CubeCharacter.Cube c = cubes.nearest(p, 24.0);
              if (c == null) return 0;
              cubes.remove(p.getServer(), c);
              return 1;
            }))
            .then(CommandManager.literal("reload").executes(ctx -> {
              ai.loadConfig();
              ServerPlayerEntity p = ctx.getSource().getPlayer();
              if (p != null) {
                p.sendMessage(Text.literal(ai.hasKey() ? "§a[Vesper] Ayarlar yuklendi, anahtar bulundu." : "§e[Vesper] Anahtar hala yok."), false);
              }
              return 1;
            }))));

    UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
      if (world.isClient || hand != Hand.MAIN_HAND) return ActionResult.PASS;
      ItemStack stack = player.getStackInHand(hand);
      if (!stack.isOf(VESPER_ITEM)) return ActionResult.PASS;
      if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
      BlockPos target = hit.getBlockPos().offset(hit.getSide());
      CubeCharacter.Cube c = cubes.place(sp, target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
      stack.decrement(1);
      cubes.speak(sp.getServer(), c, "Placement acknowledged. Speak to me in chat.");
      return ActionResult.SUCCESS;
    });

    UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
      if (world.isClient || hand != Hand.MAIN_HAND) return ActionResult.PASS;
      if (!entity.getCommandTags().contains("v_hit")) return ActionResult.PASS;
      if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
      CubeCharacter.Cube c = cubes.byEntity(entity);
      if (c == null) return ActionResult.PASS;
      cubes.remove(sp.getServer(), c);
      sp.getInventory().offerOrDrop(new ItemStack(VESPER_ITEM));
      return ActionResult.SUCCESS;
    });

    ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
      CubeCharacter.Cube c = cubes.nearest(sender, 24.0);
      if (c == null) return;
      String text = message.getSignedContent();
      if (text == null || text.isBlank()) return;
      ai.ask(sender.getServer(), sender, text, reply -> cubes.say(c, sender, reply));
    });

    ServerTickEvents.END_SERVER_TICK.register(server -> cubes.tick(server));
    ServerLifecycleEvents.SERVER_STARTED.register(server -> cubes.killOrphans(server));
    ServerLifecycleEvents.SERVER_STOPPING.register(server -> cubes.removeAll(server));
  }
}
