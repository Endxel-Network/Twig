package net.minecraft.server.bossevents;

import com.google.common.collect.Sets;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.ChatComponentUtils;
import net.minecraft.network.chat.ChatHoverable;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.IChatBaseComponent;
import net.minecraft.resources.MinecraftKey;
import net.minecraft.server.level.BossBattleServer;
import net.minecraft.server.level.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.world.BossBattle;

// CraftBukkit start
import org.bukkit.boss.KeyedBossBar;
import org.bukkit.craftbukkit.boss.CraftKeyedBossbar;
// CraftBukkit end

public class BossBattleCustom extends BossBattleServer {

    private static final int DEFAULT_MAX = 100;
    private final MinecraftKey id;
    private final Set<UUID> players = Sets.newHashSet();
    private int value;
    private int max = 100;
    // CraftBukkit start
    private KeyedBossBar bossBar;

    public KeyedBossBar getBukkitEntity() {
        if (bossBar == null) {
            bossBar = new CraftKeyedBossbar(this);
        }
        return bossBar;
    }
    // CraftBukkit end

    public BossBattleCustom(MinecraftKey minecraftkey, IChatBaseComponent ichatbasecomponent) {
        super(ichatbasecomponent, BossBattle.BarColor.WHITE, BossBattle.BarStyle.PROGRESS);
        this.id = minecraftkey;
        this.setProgress(0.0F);
    }

    public MinecraftKey getTextId() {
        return this.id;
    }

    @Override
    public void addPlayer(EntityPlayer entityplayer) {
        super.addPlayer(entityplayer);
        this.players.add(entityplayer.getUUID());
    }

    public void addOfflinePlayer(UUID uuid) {
        this.players.add(uuid);
    }

    @Override
    public void removePlayer(EntityPlayer entityplayer) {
        super.removePlayer(entityplayer);
        this.players.remove(entityplayer.getUUID());
    }

    @Override
    public void removeAllPlayers() {
        super.removeAllPlayers();
        this.players.clear();
    }

    public int getValue() {
        return this.value;
    }

    public int getMax() {
        return this.max;
    }

    public void setValue(int i) {
        this.value = i;
        this.setProgress(MathHelper.clamp((float) i / (float) this.max, 0.0F, 1.0F));
    }

    public void setMax(int i) {
        this.max = i;
        this.setProgress(MathHelper.clamp((float) this.value / (float) i, 0.0F, 1.0F));
    }

    public final IChatBaseComponent getDisplayName() {
        return ChatComponentUtils.wrapInSquareBrackets(this.getName()).withStyle((chatmodifier) -> {
            return chatmodifier.withColor(this.getColor().getFormatting()).withHoverEvent(new ChatHoverable.e(IChatBaseComponent.literal(this.getTextId().toString()))).withInsertion(this.getTextId().toString());
        });
    }

    public boolean setPlayers(Collection<EntityPlayer> collection) {
        Set<UUID> set = Sets.newHashSet();
        Set<EntityPlayer> set1 = Sets.newHashSet();

        for (UUID uuid : this.players) {
            boolean flag = false;

            for (EntityPlayer entityplayer : collection) {
                if (entityplayer.getUUID().equals(uuid)) {
                    flag = true;
                    break;
                }
            }

            if (!flag) {
                set.add(uuid);
            }
        }

        for (EntityPlayer entityplayer1 : collection) {
            boolean flag1 = false;

            for (UUID uuid1 : this.players) {
                if (entityplayer1.getUUID().equals(uuid1)) {
                    flag1 = true;
                    break;
                }
            }

            if (!flag1) {
                set1.add(entityplayer1);
            }
        }

        for (UUID uuid2 : set) {
            for (EntityPlayer entityplayer2 : this.getPlayers()) {
                if (entityplayer2.getUUID().equals(uuid2)) {
                    this.removePlayer(entityplayer2);
                    break;
                }
            }

            this.players.remove(uuid2);
        }

        for (EntityPlayer entityplayer3 : set1) {
            this.addPlayer(entityplayer3);
        }

        return !set.isEmpty() || !set1.isEmpty();
    }

    public static BossBattleCustom load(MinecraftKey minecraftkey, BossBattleCustom.a bossbattlecustom_a) {
        BossBattleCustom bossbattlecustom = new BossBattleCustom(minecraftkey, bossbattlecustom_a.name);

        bossbattlecustom.setVisible(bossbattlecustom_a.visible);
        bossbattlecustom.setValue(bossbattlecustom_a.value);
        bossbattlecustom.setMax(bossbattlecustom_a.max);
        bossbattlecustom.setColor(bossbattlecustom_a.color);
        bossbattlecustom.setOverlay(bossbattlecustom_a.overlay);
        bossbattlecustom.setDarkenScreen(bossbattlecustom_a.darkenScreen);
        bossbattlecustom.setPlayBossMusic(bossbattlecustom_a.playBossMusic);
        bossbattlecustom.setCreateWorldFog(bossbattlecustom_a.createWorldFog);
        Set<UUID> set = bossbattlecustom_a.players; // CraftBukkit - decompile error

        Objects.requireNonNull(bossbattlecustom);
        set.forEach(bossbattlecustom::addOfflinePlayer);
        return bossbattlecustom;
    }

    public BossBattleCustom.a pack() {
        return new BossBattleCustom.a(this.getName(), this.isVisible(), this.getValue(), this.getMax(), this.getColor(), this.getOverlay(), this.shouldDarkenScreen(), this.shouldPlayBossMusic(), this.shouldCreateWorldFog(), Set.copyOf(this.players));
    }

    public void onPlayerConnect(EntityPlayer entityplayer) {
        if (this.players.contains(entityplayer.getUUID())) {
            this.addPlayer(entityplayer);
        }

    }

    public void onPlayerDisconnect(EntityPlayer entityplayer) {
        super.removePlayer(entityplayer);
    }

    public static record a(IChatBaseComponent name, boolean visible, int value, int max, BossBattle.BarColor color, BossBattle.BarStyle overlay, boolean darkenScreen, boolean playBossMusic, boolean createWorldFog, Set<UUID> players) {

        public static final Codec<BossBattleCustom.a> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(ComponentSerialization.CODEC.fieldOf("Name").forGetter(BossBattleCustom.a::name), Codec.BOOL.optionalFieldOf("Visible", false).forGetter(BossBattleCustom.a::visible), Codec.INT.optionalFieldOf("Value", 0).forGetter(BossBattleCustom.a::value), Codec.INT.optionalFieldOf("Max", 100).forGetter(BossBattleCustom.a::max), BossBattle.BarColor.CODEC.optionalFieldOf("Color", BossBattle.BarColor.WHITE).forGetter(BossBattleCustom.a::color), BossBattle.BarStyle.CODEC.optionalFieldOf("Overlay", BossBattle.BarStyle.PROGRESS).forGetter(BossBattleCustom.a::overlay), Codec.BOOL.optionalFieldOf("DarkenScreen", false).forGetter(BossBattleCustom.a::darkenScreen), Codec.BOOL.optionalFieldOf("PlayBossMusic", false).forGetter(BossBattleCustom.a::playBossMusic), Codec.BOOL.optionalFieldOf("CreateWorldFog", false).forGetter(BossBattleCustom.a::createWorldFog), UUIDUtil.CODEC_SET.optionalFieldOf("Players", Set.of()).forGetter(BossBattleCustom.a::players)).apply(instance, BossBattleCustom.a::new);
        });
    }
}
