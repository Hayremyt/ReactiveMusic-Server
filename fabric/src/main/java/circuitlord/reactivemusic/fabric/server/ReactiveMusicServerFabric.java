package circuitlord.reactivemusic.fabric.server;

import circuitlord.reactivemusic.server.ServerEvents;
import circuitlord.reactivemusic.server.handshake.HandshakeConstants;
import circuitlord.reactivemusic.server.handshake.HandshakePackets;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.server.network.ServerPlayNetworking;
import net.fabricmc.fabric.api.server.player.ServerPlayerConnectionEvents;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.PacketByteBufs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

public class ReactiveMusicServerFabric implements DedicatedServerModInitializer {

    public static ServerEvents serverEvents;

    @Override
    public void onInitializeServer() {
        serverEvents = new ServerEvents();

        // Register handshake packet handler
        ServerPlayNetworking.registerReceiver(
                HandshakeConstants.CHANNEL_ID,
                (server, player, handler, buf, responseSender) -> {
                    var request = HandshakePackets.HandshakeRequest.read(buf);
                    server.execute(() -> {
                        serverEvents.onHandshakeRequest(player);
                        var response = new HandshakePackets.HandshakeResponse("1.3.5");
                        PacketByteBuf buf2 = new PacketByteBuf(PacketByteBufs.empty());
                        response.write(buf2);
                        ServerPlayNetworking.send(player, HandshakeConstants.CHANNEL_ID, buf2);
                    });
                }
        );

        // Server tick - update targets and structures
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (serverEvents == null) return;
            serverEvents.onServerTick(server.getWorld(server.getRegistryKey()));
        });

        // Player join
        ServerPlayerConnectionEvents.JOIN.register((handler, sender, server) -> {
            serverEvents.onPlayerJoin(handler.player);
        });

        // Player disconnect
        ServerPlayerConnectionEvents.DISCONNECT.register((handler, server) -> {
            serverEvents.onPlayerLeave(handler.player);
        });
    }

    public static ServerEvents getServerEvents() {
        return serverEvents;
    }
}
