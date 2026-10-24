package circuitlord.reactivemusic.neoforge.server;

import circuitlord.reactivemusic.server.ServerEvents;
import circuitlord.reactivemusic.server.handshake.HandshakeConstants;
import circuitlord.reactivemusic.server.handshake.HandshakePackets;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.network.NetworkRegistry;
import net.neoforged.neoforge.network.simple.SimpleChannel;
import net.neoforged.neoforge.event.TickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;

@Mod("reactivemusic-server")
public class ReactiveMusicServerNeoForge {

    public static ServerEvents serverEvents;
    public static SimpleChannel channel;
    private static int packetId = 0;

    public ReactiveMusicServerNeoForge(IEventBus modEventBus) {
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

        modEventBus.addListener(this::setup);
        modEventBus.register(this);
    }

    private void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {});
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (serverEvents == null) return;

        ServerLevel world = event.getPlayer().getLevel();
        if (world != null) {
            serverEvents.onServerTick(world);
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

        public static void handle(HandshakeRequestMessage msg, net.neoforged.neoforge.network.NetworkEvent.Context ctx) {
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

        public static void handle(HandshakeResponseMessage msg, net.neoforged.neoforge.network.NetworkEvent.Context ctx) {
            ctx.setPacketHandled(true);
        }
    }
}
