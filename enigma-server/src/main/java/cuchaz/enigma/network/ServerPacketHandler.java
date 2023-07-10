package cuchaz.enigma.network;

import jakarta.websocket.Session;

public record ServerPacketHandler(Session client, EnigmaServer server) {
}
