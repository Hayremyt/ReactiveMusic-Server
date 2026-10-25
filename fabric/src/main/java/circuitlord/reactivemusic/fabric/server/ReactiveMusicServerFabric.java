package circuitlord.reactivemusic.fabric.server;

import circuitlord.reactivemusic.server.ServerEvents;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

//? if >=1.20.5 {
/*import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

import java.util.UUID;
*///?} else {
import circuitlord.reactivemusic.server.ServerPackets;
import circuitlord.reactivemusic.server.handshake.HandshakePackets;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
//?}

public class ReactiveMusicServerFabric implements DedicatedServerModInitializer {

    public static ServerEvents serverEvents;

    //? if >=1.20.5 {
    /*private static final CustomPayload.Id<MobTargetPayload> MOB_ID =
            new CustomPayload.Id<>(Identifier.tryParse("reactivemusic:targets"));
    private static final CustomPayload.Id<StructurePayload> STRUCT_ID =
            new CustomPayload.Id<>(Identifier.tryParse("reactivemusic:structures"));
    *///?} else {
    private static final Identifier CHANNEL_ID = Identifier.tryParse("reactivemusic:handshake");
    private static final Identifier CHANNEL_TARGETS = Identifier.tryParse("reactivemusic:targets");
    private static final Identifier CHANNEL_STRUCTURES = Identifier.tryParse("reactivemusic:structures");
    //?}

    @Override
    public void onInitializeServer() {
        serverEvents = new ServerEvents();
        serverEvents.getHandshakeManager().configure(
                FabricLoader.getInstance().getConfigDir()
                        .resolve("reactivemusic-server").resolve("handshake.properties"));

        //? if >=1.20.5 {
        /*PayloadTypeRegistry.playC2S().register(HandshakeRequestPayload.TYPE, HandshakeRequestPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(HandshakeResponsePayload.TYPE, HandshakeResponsePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(MobTargetPayload.TYPE, MobTargetPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(StructurePayload.TYPE, StructurePayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(HandshakeRequestPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayerEntity player = context.player();
                if (player == null || serverEvents == null) return;
                serverEvents.onHandshakeRequest(player);
                ServerPlayNetworking.send(player, new HandshakeResponsePayload("1.3.5"));
            });
        });
        *///?} else {
        ServerPlayNetworking.registerGlobalReceiver(CHANNEL_ID,
                (server, player, handler, buf, responseSender) -> {
                    var request = HandshakePackets.HandshakeRequest.read(buf);
                    server.execute(() -> {
                        serverEvents.onHandshakeRequest(player);
                        PacketByteBuf out = PacketByteBufs.create();
                        new HandshakePackets.HandshakeResponse("1.3.5").write(out);
                        ServerPlayNetworking.send(player, CHANNEL_ID, out);
                    });
                });
        //?}

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (serverEvents == null) return;
            serverEvents.onServerTick(server);

            for (var player : server.getPlayerManager().getPlayerList()) {
                var targetUpdates = serverEvents.drainTargetUpdates(player.getUuid());
                if (targetUpdates != null) {
                    for (var update : targetUpdates) {
                        sendMobTarget(player, update.mobUuid, update.targetUuid);
                    }
                }

                var structures = serverEvents.drainStructureUpdate(player.getUuid());
                if (structures != null) {
                    sendStructures(player, structures.toArray(new String[0]));
                }
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            serverEvents.onPlayerJoin(handler.player);
        });

        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
            serverEvents.onPlayerChangedDimension(player);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            serverEvents.onPlayerLeave(handler.player);
        });
    }

    private static void sendMobTarget(ServerPlayerEntity player, java.util.UUID mobUuid, java.util.UUID targetUuid) {
        //? if >=1.20.5 {
        /*ServerPlayNetworking.send(player, new MobTargetPayload(mobUuid, targetUuid));
        *///?} else {
        PacketByteBuf buf = PacketByteBufs.create();
        new ServerPackets.MobTargetPacket(mobUuid, targetUuid).write(buf);
        ServerPlayNetworking.send(player, CHANNEL_TARGETS, buf);
        //?}
    }

    private static void sendStructures(ServerPlayerEntity player, String[] structures) {
        //? if >=1.20.5 {
        /*ServerPlayNetworking.send(player, new StructurePayload(structures));
        *///?} else {
        PacketByteBuf buf = PacketByteBufs.create();
        new ServerPackets.StructurePacket(structures).write(buf);
        ServerPlayNetworking.send(player, CHANNEL_STRUCTURES, buf);
        //?}
    }

    public static ServerEvents getServerEvents() {
        return serverEvents;
    }

    //? if >=1.20.5 {
    /*public record HandshakeRequestPayload(String clientVersion) implements CustomPayload {
        public static final CustomPayload.Id<HandshakeRequestPayload> TYPE =
                new CustomPayload.Id<>(Identifier.tryParse("reactivemusic:handshake_request"));
        public static final PacketCodec<RegistryByteBuf, HandshakeRequestPayload> CODEC = PacketCodec.ofStatic(
                (RegistryByteBuf buf, HandshakeRequestPayload v) -> buf.writeString(v.clientVersion()),
                buf -> new HandshakeRequestPayload(buf.readString()));
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return TYPE; }
    }

    public record HandshakeResponsePayload(String serverVersion) implements CustomPayload {
        public static final CustomPayload.Id<HandshakeResponsePayload> TYPE =
                new CustomPayload.Id<>(Identifier.tryParse("reactivemusic:handshake_response"));
        public static final PacketCodec<RegistryByteBuf, HandshakeResponsePayload> CODEC = PacketCodec.ofStatic(
                (RegistryByteBuf buf, HandshakeResponsePayload v) -> buf.writeString(v.serverVersion()),
                buf -> new HandshakeResponsePayload(buf.readString()));
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return TYPE; }
    }

    public record MobTargetPayload(UUID mobUuid, UUID targetUuid) implements CustomPayload {
        public static final CustomPayload.Id<MobTargetPayload> TYPE =
                new CustomPayload.Id<>(Identifier.tryParse("reactivemusic:targets"));
        public static final PacketCodec<RegistryByteBuf, MobTargetPayload> CODEC = PacketCodec.ofStatic(
                (RegistryByteBuf buf, MobTargetPayload v) -> {
                    buf.writeUuid(v.mobUuid());
                    if (v.targetUuid() == null) buf.writeBoolean(false);
                    else { buf.writeBoolean(true); buf.writeUuid(v.targetUuid()); }
                },
                buf -> {
                    UUID mob = buf.readUuid();
                    boolean has = buf.readBoolean();
                    UUID target = has ? buf.readUuid() : null;
                    return new MobTargetPayload(mob, target);
                });
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return TYPE; }
    }

    public record StructurePayload(String[] structures) implements CustomPayload {
        public static final CustomPayload.Id<StructurePayload> TYPE =
                new CustomPayload.Id<>(Identifier.tryParse("reactivemusic:structures"));
        public static final PacketCodec<RegistryByteBuf, StructurePayload> CODEC = PacketCodec.ofStatic(
                (RegistryByteBuf buf, StructurePayload v) -> {
                    buf.writeInt(v.structures().length);
                    for (String s : v.structures()) buf.writeString(s);
                },
                buf -> {
                    int len = buf.readInt();
                    String[] arr = new String[len];
                    for (int i = 0; i < len; i++) arr[i] = buf.readString();
                    return new StructurePayload(arr);
                });
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return TYPE; }
    }
    *///?}
}
