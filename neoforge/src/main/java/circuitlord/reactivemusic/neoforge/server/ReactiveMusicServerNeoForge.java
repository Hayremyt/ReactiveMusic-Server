package circuitlord.reactivemusic.neoforge.server;

import circuitlord.reactivemusic.server.ServerEvents;
import circuitlord.reactivemusic.server.MobTargetStatus;
import circuitlord.reactivemusic.server.handshake.HandshakeConstants;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.UUID;

@Mod("reactivemusic-server")
public class ReactiveMusicServerNeoForge {

    public static ServerEvents serverEvents;

    public ReactiveMusicServerNeoForge(IEventBus modEventBus) {
        serverEvents = new ServerEvents();
        serverEvents.getHandshakeManager().configure(
                FMLPaths.CONFIGDIR.get().resolve("reactivemusic-server").resolve("handshake.properties"));

        modEventBus.addListener(this::registerPayloads);
        NeoForge.EVENT_BUS.register(this);
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        // optional() — клиенты без этих payload'ов не отключаются (мод опциональный)
        PayloadRegistrar registrar = event.registrar("1").optional();

        registrar.playToServer(HandshakeRequestPayload.TYPE, HandshakeRequestPayload.CODEC,
                ReactiveMusicServerNeoForge::handleHandshakeRequest);
        registrar.playToClient(HandshakeResponsePayload.TYPE, HandshakeResponsePayload.CODEC,
                ReactiveMusicServerNeoForge::handleNoop);
        registrar.playToClient(MobTargetPayload.TYPE, MobTargetPayload.CODEC,
                ReactiveMusicServerNeoForge::handleNoop);
        registrar.playToClient(StructurePayload.TYPE, StructurePayload.CODEC,
                ReactiveMusicServerNeoForge::handleNoop);
    }

    private static void handleNoop(CustomPayload payload, IPayloadContext ctx) {
        // clientbound payload'ы на сервере не обрабатываются
    }

    private static void handleHandshakeRequest(HandshakeRequestPayload msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (serverEvents == null) return;
            if (!(ctx.player() instanceof ServerPlayerEntity player)) return;
            serverEvents.onHandshakeRequest(player);
            PacketDistributor.sendToPlayer(player, new HandshakeResponsePayload(HandshakeConstants.MOD_VERSION));
        });
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (serverEvents == null) return;
        MinecraftServer server = event.getServer();
        if (server == null) return;

        serverEvents.onServerTick(server);

        for (var player : server.getPlayerManager().getPlayerList()) {
            var targetUpdates = serverEvents.drainTargetUpdates(player.getUuid());
            if (targetUpdates != null) {
                for (var update : targetUpdates) {
                    PacketDistributor.sendToPlayer(player, new MobTargetPayload(update.mobUuid, update.status));
                }
            }

            var structures = serverEvents.drainStructureUpdate(player.getUuid());
            if (structures != null) {
                PacketDistributor.sendToPlayer(player, new StructurePayload(structures.toArray(new String[0])));
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

    public static ServerEvents getServerEvents() {
        return serverEvents;
    }

    // ---------------- payload'ы ----------------

    public record HandshakeRequestPayload(String clientVersion) implements CustomPayload {
        public static final CustomPayload.Id<HandshakeRequestPayload> TYPE =
                new CustomPayload.Id<>(Identifier.tryParse(HandshakeConstants.CHANNEL_HANDSHAKE_REQUEST));

        public static final PacketCodec<RegistryByteBuf, HandshakeRequestPayload> CODEC = PacketCodec.ofStatic(
                (RegistryByteBuf buf, HandshakeRequestPayload v) -> buf.writeString(v.clientVersion()),
                buf -> new HandshakeRequestPayload(buf.readString()));

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return TYPE;
        }
    }

    public record HandshakeResponsePayload(String serverVersion) implements CustomPayload {
        public static final CustomPayload.Id<HandshakeResponsePayload> TYPE =
                new CustomPayload.Id<>(Identifier.tryParse(HandshakeConstants.CHANNEL_HANDSHAKE_RESPONSE));

        public static final PacketCodec<RegistryByteBuf, HandshakeResponsePayload> CODEC = PacketCodec.ofStatic(
                (RegistryByteBuf buf, HandshakeResponsePayload v) -> buf.writeString(v.serverVersion()),
                buf -> new HandshakeResponsePayload(buf.readString()));

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return TYPE;
        }
    }

    public record MobTargetPayload(UUID mobUuid, MobTargetStatus status) implements CustomPayload {
        public static final CustomPayload.Id<MobTargetPayload> TYPE =
                new CustomPayload.Id<>(Identifier.tryParse(HandshakeConstants.CHANNEL_TARGETS));

        public static final PacketCodec<RegistryByteBuf, MobTargetPayload> CODEC = PacketCodec.ofStatic(
                (RegistryByteBuf buf, MobTargetPayload v) -> {
                    buf.writeUuid(v.mobUuid());
                    buf.writeByte(v.status().ordinal());
                },
                buf -> new MobTargetPayload(buf.readUuid(), MobTargetStatus.byOrdinal(buf.readByte())));

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return TYPE;
        }
    }

    public record StructurePayload(String[] structures) implements CustomPayload {
        public static final CustomPayload.Id<StructurePayload> TYPE =
                new CustomPayload.Id<>(Identifier.tryParse(HandshakeConstants.CHANNEL_STRUCTURES));

        public static final PacketCodec<RegistryByteBuf, StructurePayload> CODEC = PacketCodec.ofStatic(
                (RegistryByteBuf buf, StructurePayload v) -> {
                    buf.writeInt(v.structures().length);
                    for (String s : v.structures()) {
                        buf.writeString(s);
                    }
                },
                buf -> {
                    int len = buf.readInt();
                    String[] arr = new String[len];
                    for (int i = 0; i < len; i++) {
                        arr[i] = buf.readString();
                    }
                    return new StructurePayload(arr);
                });

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return TYPE;
        }
    }
}
