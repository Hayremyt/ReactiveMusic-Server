# ReactiveMusic-Server

Server-side part of ReactiveMusic: Community Edition.

## Purpose

The client mod works without this server part, but on dedicated servers some features are limited:

| Feature | Without server | With server |
|---------|---------------|-------------|
| `target=me/other` | Heuristic (damage + glance + warden anger) | Exact mob target from server |
| `STRUCT=` | Always `false` on dedicated | Real structures around player |

The server part tracks mob targets and structures on the server, and sends them to clients over the network. The client uses exact data when available, otherwise falls back to heuristics.

## Protocol

Client and server perform a handshake on player join:

1. Client sends `HandshakeRequest` (client version)
2. Server replies with `HandshakeResponse` (server version) if it has the server mod
3. If no response within 5 seconds, client works in heuristic-only mode

After handshake, the server sends `MobTargetPacket` and `StructurePacket` only to clients that confirmed they have the client mod.

## Loaders

- **Fabric**: `fabric/` — `ServerPlayNetworking` channel `reactivemusic:server`
- **Forge**: `forge/` — `SimpleChannel`
- **NeoForge**: `neoforge/` — `SimpleChannel`

## Build

`gradlew.bat chiseledBuild` — builds all versions × loaders (use `--no-parallel` if Loom mappings race occurs).

## Installation

Install on the **server** side only. The client mod works standalone without it.
