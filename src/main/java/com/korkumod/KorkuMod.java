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
    private static final int FIGURE_CHANCE = 6;    // her kontrolde figür şansı: 1/6 (~6 dakikada bir)

    private static final int WINDOW_CHANCE = 5;    // gece içerideyken pencerede adam: 1/5
    private static final int FOOTSTEP_CHANCE = 5;  // arkandan ayak sesleri: 1/5
    private static final int FAKE_MSG_CHANCE = 4;  // sahte oyuncu mesajı: 1/4
    private static final int NIGHTMARE_CHANCE = 3; // yatağa basınca kabus: 1/3 (diğer sefer normal uyursun)
    private static final int WAKE_FIGURE_CHANCE = 2; // uyanınca adam belirme: 1/2

    private static final int NEW_EVENT_CHANCE = 4;  // yeni olaylar havuzu: her kontrolde 1/4 (~4 dakikada bir)

    private static final int NULL_FIRST_DELAY = 3600;  // sunucu açıldıktan ~3 dk sonra ilk ziyaret
    private static final int NULL_INTERVAL = 12000;    // ziyaretler arası: 12000 tick = 10 dk (test için 1200 yap)
    private static final int NULL_WAIT_TICKS = 3600;   // eşyayı getirmek için süre: 3 dk
    private static final int NULL_CHASE_TICKS = 600;   // kaçış süresi: 30 sn
    private static final int NULL_RAID_CHANCE = 5;     // ziyaretlerin 1/5'i TPA baskını (kılıçla öldürür)

    private static final int FIGURE_STARE = 1;
    private static final int FIGURE_APPROACH = 2;
    private static final int FIGURE_NIGHTMARE = 3;
    private static final int FIGURE_WINDOW = 4;
    private static final int FIGURE_WAKE = 5;
    private static final int FIGURE_TREE = 6;
    private static final int FIGURE_BEDMAN = 7;
    private static final int FIGURE_NULL = 8;

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
            SoundEvent.of(I
