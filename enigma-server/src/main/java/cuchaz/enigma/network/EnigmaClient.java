package cuchaz.enigma.network;

import cuchaz.enigma.network.packet.Packet;
import cuchaz.enigma.network.packet.PacketRegistry;
import jakarta.websocket.ClientEndpoint;
import jakarta.websocket.CloseReason;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnMessage;
import jakarta.websocket.Session;
import org.glassfish.tyrus.client.ClientManager;
import org.tinylog.Logger;

import javax.swing.SwingUtilities;
import java.io.ByteArrayInputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.SocketException;
import java.net.URI;

public class EnigmaClient {
	private final ClientPacketHandler controller;

	private final String ip;
	private final int port;
	private ClientManager client;
	private Session session;

	public EnigmaClient(ClientPacketHandler controller, String ip, int port) {
		this.controller = controller;
		this.ip = ip;
		this.port = port;
	}

	public void connect() {
		URI uri = URI.create(this.ip + ":" + this.port + "/main/enigma");
		this.client = ClientManager.createClient();
		try (Session s = this.client.connectToServer(Endpoint.class, uri)) {
			this.session = s;
		} catch (DeploymentException | IOException e) {
			Logger.error(e, "Interrupted while waiting for closing latch");
		}
	}

	public synchronized void disconnect() {
		if (this.client != null) {
			this.client.shutdown();
		}
	}

	public void sendPacket(Packet<ServerPacketHandler> packet) {
		try {
			DataOutput output = new DataOutputStream(this.session.getBasicRemote().getSendStream());
			output.writeByte(PacketRegistry.getC2SId(packet));
			packet.write(output);
		} catch (IOException e) {
			Logger.error(e, "Failed to send packet");
			this.controller.disconnectIfConnected(e.toString());
		}
	}

	@ClientEndpoint
	public class Endpoint {
		@OnMessage
		public void onMessage(String message) {
			Thread thread = new Thread(() -> {
				try {
					DataInput input = new DataInputStream(new ByteArrayInputStream(message.getBytes()));
					while (true) {
						int packetId;
						try {
							packetId = input.readUnsignedByte();
						} catch (EOFException | SocketException e) {
							break;
						}

						Packet<ClientPacketHandler> packet = PacketRegistry.createS2CPacket(packetId);
						if (packet == null) {
							throw new IOException("Received invalid packet id " + packetId);
						}

						packet.read(input);
						SwingUtilities.invokeLater(() -> packet.handle(EnigmaClient.this.controller));
					}
				} catch (IOException e) {
					EnigmaClient.this.controller.disconnectIfConnected(e.toString());
				}
			});
			thread.setName("Client I/O thread");
			thread.setDaemon(true);
			thread.start();
		}

		@OnClose
		public void onClose(Session session, CloseReason reason) {
			Logger.info("disconnected from server at " + session.getRequestURI() + "!" + reason);
		}
	}
}
