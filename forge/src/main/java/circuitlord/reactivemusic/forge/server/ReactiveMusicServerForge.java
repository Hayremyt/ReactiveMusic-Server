package circuitlord.reactivemusic.forge.server;

import circuitlord.reactivemusic.server.ServerEvents;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.UUID;
import java.util.function.Supplier;

@Mod("reactivemusic-server")
public class ReactiveMusicServerForge {

    public static ServerEvents serverEvents;
    public static SimpleChannel handshakeChannel;
    public static SimpleChannel targetsChannel;
    public static SimpleChannel structuresChannel;
    private static int packetId = 0;

    public ReactiveMusicServerForge() {
        serverEvents = new ServerEvents();
        serverEvents.getHandshakeManager().configure(
                FMLPaths.CONFIGDIR.get().resolve("reactivemusic-server").resolve("handshake.properties"));

        handshakeChannel = NetworkRegistry.ChannelBuilder
                .named(new Identifier("reactivemusic-server", "handshake"))
                .networkProtocolVersion(() -> "1")
                .clientAcceptedVersions(s -> true)
                .serverAcceptedVersions(s -> true)
                .simpleChannel();

        handshakeChannel.messageBuilder(HandshakeRequestMessage.class, packetId++)
                .encoder(HandshakeRequestMessage::encode)
                .decoder(HandshakeRequestMessage::decode)
                .consumerMainThread(HandshakeRequestMessage::handle)
                .add();

        handshakeChannel.messageBuilder(HandshakeResponseMessage.class, packetId++)
                .encoder(HandshakeResponseMessage::encode)
                .decoder(HandshakeResponseMessage::decode)
                .consumerMainThread(HandshakeResponseMessage::handle)
                .add();

        targetsChannel = NetworkRegistry.ChannelBuilder
                .named(new Identifier("reactivemusic-server", "targets"))
                .networkProtocolVersion(() -> "1")
                .clientAcceptedVersions(s -> true)
                .serverAcceptedVersions(s -> true)
                .simpleChannel();

        targetsChannel.messageBuilder(MobTargetMessage.class, packetId++)
                .encoder(MobTargetMessage::encode)
                .decoder(MobTargetMessage::decode)
                .consumerMainThread(MobTargetMessage::handle)
                .add();

        structuresChannel = NetworkRegistry.ChannelBuilder
                .named(new Identifier("reactivemusic-server", "structures"))
                .networkProtocolVersion(() -> "1")
                .clientAcceptedVersions(s -> true)
                .serverAcceptedVersions(s -> true)
                .simpleChannel();

        structuresChannel.messageBuilder(StructureMessage.class, packetId++)
                .encoder(StructureMessage::encode)
                .decoder(StructureMessage::decode)
                .consumerMainThread(StructureMessage::handle)
                .add();

        MinecraftForge.EVENT_BUS.register(this);
        FMLJavaModLoadingContext.get().getModEventBus().register(this);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (serverEvents == null) return;

        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        serverEvents.onServerTick(server);

        for (var player : server.getPlayerManager().getPlayerList()) {
            var targetUpdates = serverEvents.drainTargetUpdates(player.getUuid());
            if (targetUpdates != null) {
                for (var update : targetUpdates) {
                    sendTarget(player, new MobTargetMessage(update.mobUuid, update.targetUuid));
                }
            }

            var structures = serverEvents.drainStructureUpdate(player.getUuid());
            if (structures != null) {
                sendStructures(player, new StructureMessage(structures.toArray(new String[0])));
            }
        }
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (serverEvents != null && event.getEntity() instanceof ServerPlayerEntity player) {
            serverEvents.onPlayerJoin(player);
        }
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (serverEvents != null && event.getEntity() instanceof ServerPlayerEntity player) {
            serverEvents.onPlayerLeave(player);
        }
    }

    @SubscribeEvent
    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (serverEvents != null && event.getEntity() instanceof ServerPlayerEntity player) {
            serverEvents.onPlayerChangedDimension(player);
        }
    }

    static void sendTarget(ServerPlayerEntity player, MobTargetMessage msg) {
        targetsChannel.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    static void sendStructures(ServerPlayerEntity player, StructureMessage msg) {
        structuresChannel.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    public static ServerEvents getServerEvents() {
        return serverEvents;
    }

    // ---------------- сообщения ----------------

    public static class HandshakeRequestMessage {
        public String clientVersion;

        public HandshakeRequestMessage() {}

        public HandshakeRequestMessage(String clientVersion) {
            this.clientVersion = clientVersion;
        }

        public static void encode(HandshakeRequestMessage msg, PacketByteBuf buf) {
            buf.writeString(msg.clientVersion);
        }

        public static HandshakeRequestMessage decode(PacketByteBuf buf) {
            return new HandshakeRequestMessage(buf.readString());
        }

        public static void handle(HandshakeRequestMessage msg, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context ctx = supplier.get();
            ctx.enqueueWork(() -> {
                ServerPlayerEntity player = ctx.getSender();
                if (player == null || serverEvents == null) return;
                serverEvents.onHandshakeRequest(player);
                handshakeChannel.send(PacketDistributor.PLAYER.with(() -> player),
                        new HandshakeResponseMessage("1.3.5"));
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

        public static void encode(HandshakeResponseMessage msg, PacketByteBuf buf) {
            buf.writeString(msg.serverVersion);
        }

        public static HandshakeResponseMessage decode(PacketByteBuf buf) {
            return new HandshakeResponseMessage(buf.readString());
        }

        public static void handle(HandshakeResponseMessage msg, Supplier<NetworkEvent.Context> supplier) {
            supplier.get().setPacketHandled(true);
        }
    }

    public static class MobTargetMessage {
        public UUID mobUuid;
        public UUID targetUuid; // null если цели нет

        public MobTargetMessage() {}

        public MobTargetMessage(UUID mobUuid, UUID targetUuid) {
            this.mobUuid = mobUuid;
            this.targetUuid = targetUuid;
        }

        public static void encode(MobTargetMessage msg, PacketByteBuf buf) {
            buf.writeUuid(msg.mobUuid);
            if (msg.targetUuid == null) {
                buf.writeBoolean(false);
            } else {
                buf.writeBoolean(true);
                buf.writeUuid(msg.targetUuid);
            }
        }

        public static MobTargetMessage decode(PacketByteBuf buf) {
            UUID mobUuid = buf.readUuid();
            boolean hasTarget = buf.readBoolean();
            UUID targetUuid = hasTarget ? buf.readUuid() : null;
            return new MobTargetMessage(mobUuid, targetUuid);
        }

        public static void handle(MobTargetMessage msg, Supplier<NetworkEvent.Context> supplier) {
            supplier.get().setPacketHandled(true);
        }
    }

    public static class StructureMessage {
        public String[] structures;

        public StructureMessage() {}

        public StructureMessage(String[] structures) {
            this.structures = structures;
        }

        public static void encode(StructureMessage msg, PacketByteBuf buf) {
            buf.writeInt(msg.structures.length);
            for (String s : msg.structures) {
                buf.writeString(s);
            }
        }

        public static StructureMessage decode(PacketByteBuf buf) {
            int len = buf.readInt();
            String[] arr = new String[len];
            for (int i = 0; i < len; i++) {
                arr[i] = buf.readString();
            }
            return new StructureMessage(arr);
        }

        public static void handle(StructureMessage msg, Supplier<NetworkEvent.Context> supplier) {
            supplier.get().setPacketHandled(true);
        }
    }
}
