package circuitlord.reactivemusic.forge.server;

import circuitlord.reactivemusic.server.ServerEvents;
import circuitlord.reactivemusic.server.handshake.HandshakeConstants;
import circuitlord.reactivemusic.server.handshake.HandshakePackets;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;

import java.util.UUID;

@Mod("reactivemusic-server")
public class ReactiveMusicServerForge {

    public static ServerEvents serverEvents;
    public static SimpleChannel channel;
    public static SimpleChannel targetsChannel;
    public static SimpleChannel structuresChannel;
    private static int packetId = 0;

    public ReactiveMusicServerForge() {
        serverEvents = new ServerEvents();

        channel = NetworkRegistry.ChannelBuilder
                .named(HandshakeConstants.CHANNEL_ID)
                .networkProtocolVersion(() -> "1.3.5")
                .clientAcceptedVersions(s -> true)
                .serverAcceptedVersions(s -> true)
                .simpleChannel();

        channel.messageBuilder(HandshakeRequestMessage.class, packetId++)
                .encoder(HandshakeRequestMessage::encode)
                .decoder(HandshakeRequestMessage::decode)
                .consumer(HandshakeRequestMessage::handle)
                .add();

        channel.messageBuilder(HandshakeResponseMessage.class, packetId++)
                .encoder(HandshakeResponseMessage::encode)
                .decoder(HandshakeResponseMessage::decode)
                .consumer(HandshakeResponseMessage::handle)
                .add();

        // Канал целей мобов
        targetsChannel = NetworkRegistry.ChannelBuilder
                .named(HandshakeConstants.CHANNEL_TARGETS)
                .networkProtocolVersion(() -> "1.3.5")
                .clientAcceptedVersions(s -> true)
                .serverAcceptedVersions(s -> true)
                .simpleChannel();

        targetsChannel.messageBuilder(MobTargetMessage.class, packetId++)
                .encoder(MobTargetMessage::encode)
                .decoder(MobTargetMessage::decode)
                .consumer(MobTargetMessage::handle)
                .add();

        // Канал структур
        structuresChannel = NetworkRegistry.ChannelBuilder
                .named(HandshakeConstants.CHANNEL_STRUCTURES)
                .networkProtocolVersion(() -> "1.3.5")
                .clientAcceptedVersions(s -> true)
                .serverAcceptedVersions(s -> true)
                .simpleChannel();

        structuresChannel.messageBuilder(StructureMessage.class, packetId++)
                .encoder(StructureMessage::encode)
                .decoder(StructureMessage::decode)
                .consumer(StructureMessage::handle)
                .add();

        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        FMLJavaModLoadingContext.get().getModEventBus().register(this);
    }

    private void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {});
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (serverEvents == null) return;

        ServerLevel world = event.getPlayer().getLevel();
        if (world == null || !world.isClientSide) {
            serverEvents.onServerTick(world);

            // --- Отправка изменений целей мобов ---
            var targetChanges = serverEvents.getLastTargetChanges();
            if (!targetChanges.isEmpty()) {
                for (var player : world.players()) {
                    if (!serverEvents.canSendMobTargets(player)) continue;
                    for (var entry : targetChanges.entrySet()) {
                        targetsChannel.send(player, new MobTargetMessage(entry.getKey(), entry.getValue()));
                    }
                }
            }

            // --- Отправка структур (только если были пересканированы в этом тике) ---
            if (serverEvents.areStructuresJustUpdated()) {
                var structures = serverEvents.getLastStructureNames().toArray(new String[0]);
                for (var player : world.players()) {
                    if (!serverEvents.canSendStructureData(player)) continue;
                    structuresChannel.send(player, new StructureMessage(structures));
                }
            }
        }
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        ServerPlayer player = (ServerPlayer) event.getEntity();
        serverEvents.onPlayerJoin(player);
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ServerPlayer player = (ServerPlayer) event.getEntity();
        serverEvents.onPlayerLeave(player);
    }

    public static ServerEvents getServerEvents() {
        return serverEvents;
    }

    public static SimpleChannel getChannel() {
        return channel;
    }

    public static class HandshakeRequestMessage {
        public String clientVersion;

        public HandshakeRequestMessage() {}

        public HandshakeRequestMessage(String clientVersion) {
            this.clientVersion = clientVersion;
        }

        public static void encode(HandshakeRequestMessage msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.clientVersion, 255);
        }

        public static HandshakeRequestMessage decode(FriendlyByteBuf buf) {
            return new HandshakeRequestMessage(buf.readUtf(255));
        }

        public static void handle(HandshakeRequestMessage msg, net.minecraftforge.network.NetworkEvent.Context ctx) {
            ctx.enqueueWork(() -> {
                ServerPlayer player = ctx.getSender();
                if (player != null) {
                    serverEvents.onHandshakeRequest(player);
                    var response = new HandshakeResponseMessage("1.3.5");
                    channel.send(player, response);
                }
            });
            ctx.setPacketHandled(true);
        }
    }

    public static class HandshakeResponseMessage {
        public String serverVersion;

        public HandshakeResponseMessage() {}

        public HandshakeResponseMessage(String serverVersion) {
            this.serverVersion = serverVersion;
        }

        public static void encode(HandshakeResponseMessage msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.serverVersion, 255);
        }

        public static HandshakeResponseMessage decode(FriendlyByteBuf buf) {
            return new HandshakeResponseMessage(buf.readUtf(255));
        }

        public static void handle(HandshakeResponseMessage msg, net.minecraftforge.network.NetworkEvent.Context ctx) {
            ctx.setPacketHandled(true);
        }
    }

    // ---------- MobTargetMessage ----------

    public static class MobTargetMessage {
        public UUID mobUuid;
        public UUID targetUuid; // null если цели нет

        public MobTargetMessage() {}

        public MobTargetMessage(UUID mobUuid, UUID targetUuid) {
            this.mobUuid = mobUuid;
            this.targetUuid = targetUuid;
        }

        public static void encode(MobTargetMessage msg, FriendlyByteBuf buf) {
            buf.writeUuid(msg.mobUuid);
            if (msg.targetUuid == null) {
                buf.writeBoolean(false);
            } else {
                buf.writeBoolean(true);
                buf.writeUuid(msg.targetUuid);
            }
        }

        public static MobTargetMessage decode(FriendlyByteBuf buf) {
            UUID mobUuid = buf.readUuid();
            boolean hasTarget = buf.readBoolean();
            UUID targetUuid = hasTarget ? buf.readUuid() : null;
            return new MobTargetMessage(mobUuid, targetUuid);
        }

        public static void handle(MobTargetMessage msg, net.minecraftforge.network.NetworkEvent.Context ctx) {
            ctx.setPacketHandled(true);
        }
    }

    // ---------- StructureMessage ----------

    public static class StructureMessage {
        public String[] structures;

        public StructureMessage() {}

        public StructureMessage(String[] structures) {
            this.structures = structures;
        }

        public static void encode(StructureMessage msg, FriendlyByteBuf buf) {
            buf.writeInt(msg.structures.length);
            for (var s : msg.structures) {
                buf.writeUtf(s, 255);
            }
        }

        public static StructureMessage decode(FriendlyByteBuf buf) {
            int len = buf.readInt();
            var arr = new String[len];
            for (int i = 0; i < len; i++) {
                arr[i] = buf.readUtf(255);
            }
            return new StructureMessage(arr);
        }

        public static void handle(StructureMessage msg, net.minecraftforge.network.NetworkEvent.Context ctx) {
            ctx.setPacketHandled(true);
        }
    }
}
