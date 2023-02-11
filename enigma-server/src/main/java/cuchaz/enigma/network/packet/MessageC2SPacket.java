package cuchaz.enigma.network.packet;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import cuchaz.enigma.network.ServerPacketHandler;
import cuchaz.enigma.network.ServerMessage;

public class MessageC2SPacket implements Packet<ServerPacketHandler> {
	private String message;

	MessageC2SPacket() {

	}

	public MessageC2SPacket(String message) {
		this.message = message;
	}

	@Override
	public void read(DataInput input) throws IOException {
		message = PacketHelper.readString(input);
	}

	@Override
	public void write(DataOutput output) throws IOException {
		PacketHelper.writeString(output, message);
	}

	@Override
	public void handle(ServerPacketHandler handler) {
		String trimmedMessage = this.message.trim();
		if (!trimmedMessage.isEmpty()) {
			handler.getServer().sendMessage(ServerMessage.chat(handler.getServer().getUsername(handler.getClient()), trimmedMessage));
		}
	}
}
