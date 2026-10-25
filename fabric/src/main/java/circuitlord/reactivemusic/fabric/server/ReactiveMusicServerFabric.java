package circuitlord.reactivemusic.fabric.server;

import circuitlord.reactivemusic.server.ServerEvents;
import circuitlord.reactivemusic.server.ServerPackets;
import circuitlord.reactivemusic.server.handshake.HandshakeConstants;
import circuitlord.reactivemusic.server.handshake.HandshakePackets;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.PacketByteBufs;

public class ReactiveMusicServerFabric implements DedicatedServerModInitializer {

    public static ServerEvents serverEvents;

    @Override
    public void onInitializeServer() {
        serverEvents = new ServerEvents();
        serverEvents.getHandshakeManager().configure(
                FabricLoader.getInstance().getConfigDir()
                        .resolve("reactivemusic-server").resolve("handshake.properties"));

        // Рукопожатие: клиент -> сервер
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

        // Тик: пересчёт per-player состояния и отправка дельт
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (serverEvents == null) return;
            serverEvents.onServerTick(server);

            for (var player : server.getPlayerManager().getPlayerList()) {
                // --- цели мобов (дельта) ---
                var targetUpdates = serverEvents.drainTargetUpdates(player.getUuid());
                if (targetUpdates != null) {
                    for (var update : targetUpdates) {
                        var packet = new ServerPackets.MobTargetPacket(update.mobUuid, update.targetUuid);
                        PacketByteBuf buf = new PacketByteBuf(PacketByteBufs.empty());
                        packet.write(buf);
                        ServerPlayNetworking.send(player, HandshakeConstants.CHANNEL_TARGETS, buf);
                    }
                }

                // --- структура (если изменилась) ---
                var structures = serverEvents.drainStructureUpdate(player.getUuid());
                if (structures != null) {
                    var packet = new ServerPackets.StructurePacket(structures.toArray(new String[0]));
                    PacketByteBuf buf = new PacketByteBuf(PacketByteBufs.empty());
                    packet.write(buf);
                    ServerPlayNetworking.send(player, HandshakeConstants.CHANNEL_STRUCTURES, buf);
                }
            }
        });

        ServerPlayerConnectionEvents.JOIN.register((handler, sender, server) -> {
            serverEvents.onPlayerJoin(handler.player);
        });

        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
            serverEvents.onPlayerChangedDimension(player);
        });

        ServerPlayerConnectionEvents.DISCONNECT.register((handler, server) -> {
            serverEvents.onPlayerLeave(handler.player);
        });
    }

    public static ServerEvents getServerEvents() {
        return serverEvents;
    }
}
