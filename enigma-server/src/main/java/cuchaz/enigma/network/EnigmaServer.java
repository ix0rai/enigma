package cuchaz.enigma.network;

import cuchaz.enigma.network.packet.EntryChangeS2CPacket;
import cuchaz.enigma.network.packet.KickS2CPacket;
import cuchaz.enigma.network.packet.MessageS2CPacket;
import cuchaz.enigma.network.packet.Packet;
import cuchaz.enigma.network.packet.PacketRegistry;
import cuchaz.enigma.network.packet.UserListS2CPacket;
import cuchaz.enigma.translation.mapping.EntryChange;
import cuchaz.enigma.translation.mapping.EntryMapping;
import cuchaz.enigma.translation.mapping.EntryRemapper;
import cuchaz.enigma.translation.representation.entry.Entry;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import org.glassfish.tyrus.server.Server;
import org.tinylog.Logger;

import java.io.ByteArrayInputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

public abstract class EnigmaServer {
	// https://discordapp.com/channels/507304429255393322/566418023372816394/700292322918793347
	public static final int DEFAULT_PORT = 34712;
	public static final int PROTOCOL_VERSION = 1;
	public static final int CHECKSUM_SIZE = 20;
	public static final int MAX_PASSWORD_LENGTH = 255; // length is written as a byte in the login packet

	private final int port;
	private Server server;
	private final List<Session> clients = new CopyOnWriteArrayList<>();
	private final Map<Session, String> usernames = new HashMap<>();
	private final Set<Session> unapprovedClients = new HashSet<>();

	private final byte[] jarChecksum;
	private final char[] password;

	public static final int DUMMY_SYNC_ID = 0;
	private final EntryRemapper mappings;
	private final Map<Entry<?>, Integer> syncIds = new HashMap<>();
	private final Map<Integer, Entry<?>> inverseSyncIds = new HashMap<>();
	private final Map<Integer, Set<Session>> clientsNeedingConfirmation = new HashMap<>();
	private int nextSyncId = DUMMY_SYNC_ID + 1;

	private static int nextIoId = 0;

	protected EnigmaServer(byte[] jarChecksum, char[] password, EntryRemapper mappings, int port) {
		this.jarChecksum = jarChecksum;
		this.password = password;
		this.mappings = mappings;
		this.port = port;
	}

	public void start() {
		this.server = new Server("localhost", this.port, "/main", null, Endpoint.class);
		// todo
		//this.log("Server started on " + address + ":" + this.port);
		CountDownLatch startLatch = new CountDownLatch(1);
		Thread thread = new Thread(() -> {
			try {
				this.server.start();
				startLatch.countDown();
			} catch (DeploymentException e) {
				throw new RuntimeException("failed to deploy server!", e);
			}
		});
		thread.setName("main server thread");
		thread.setDaemon(true);
		thread.start();
		try {
			startLatch.await();
		} catch (InterruptedException e) {
			throw new RuntimeException("interrupted while waiting for server to start!", e);
		}
	}

	public void stop() {
		this.runOnThread(() -> {
			if (this.server != null) {
				for (Session client : this.clients) {
					this.kick(client, "disconnect.server_closed");
				}

				try {
					this.server.stop();
				} catch (Exception e) {
					Logger.error(e, "Failed to close server socket!");
				}
			}
		});
	}

	public void kick(Session client, String reason) {
		if (!this.clients.remove(client)) {
			return;
		}

		this.sendPacket(client, new KickS2CPacket(reason));

		this.clientsNeedingConfirmation.values().removeIf(list -> {
			list.remove(client);
			return list.isEmpty();
		});

		String username = this.usernames.remove(client);
		try {
			client.close();
		} catch (IOException e) {
			Logger.error("Failed to close server client socket!", e);
		}

		if (username != null) {
			Logger.info("Kicked " + username + " because " + reason);
			this.sendMessage(ServerMessage.disconnect(username));
		}

		this.sendUsernamePacket();
	}

	public boolean isUsernameTaken(String username) {
		return this.usernames.containsValue(username);
	}

	public void setUsername(Session client, String username) {
		this.usernames.put(client, username);
		this.sendUsernamePacket();
	}

	private void sendUsernamePacket() {
		List<String> usernameList = new ArrayList<>(this.usernames.values());
		Collections.sort(usernameList);
		this.sendToAll(new UserListS2CPacket(usernameList));
	}

	public String getUsername(Session client) {
		return this.usernames.get(client);
	}

	public void sendPacket(Session client, Packet<ClientPacketHandler> packet) {
		if (client.isOpen()) {
			int packetId = PacketRegistry.getS2CId(packet);
			try {
				DataOutput output = new DataOutputStream(client.getBasicRemote().getSendStream());
				output.writeByte(packetId);
				packet.write(output);
			} catch (IOException e) {
				if (!(packet instanceof KickS2CPacket)) {
					this.kick(client, e.toString());
					Logger.error("Failed to send packet to client!", e);
				}
			}
		}
	}

	public void sendToAll(Packet<ClientPacketHandler> packet) {
		for (Session client : this.clients) {
			this.sendPacket(client, packet);
		}
	}

	public void sendToAllExcept(Session excluded, Packet<ClientPacketHandler> packet) {
		for (Session client : this.clients) {
			if (client != excluded) {
				this.sendPacket(client, packet);
			}
		}
	}

	public boolean canModifyEntry(Session client, Entry<?> entry) {
		if (this.unapprovedClients.contains(client)) {
			return false;
		}

		Integer syncId = this.syncIds.get(entry);
		if (syncId == null) {
			return true;
		}

		Set<Session> clients = this.clientsNeedingConfirmation.get(syncId);
		return clients == null || !clients.contains(client);
	}

	public int lockEntry(Session exception, Entry<?> entry) {
		int syncId = this.nextSyncId;
		this.nextSyncId++;
		// sync id is sent as an unsigned short, can't have more than 65536
		if (this.nextSyncId == 65536) {
			this.nextSyncId = DUMMY_SYNC_ID + 1;
		}

		Integer oldSyncId = this.syncIds.get(entry);
		if (oldSyncId != null) {
			this.clientsNeedingConfirmation.remove(oldSyncId);
		}

		this.syncIds.put(entry, syncId);
		this.inverseSyncIds.put(syncId, entry);
		Set<Session> clients = new HashSet<>(this.clients);
		clients.remove(exception);
		this.clientsNeedingConfirmation.put(syncId, clients);
		return syncId;
	}

	public void confirmChange(Session client, int syncId) {
		if (this.usernames.containsKey(client)) {
			this.unapprovedClients.remove(client);
		}

		Set<Session> clients = this.clientsNeedingConfirmation.get(syncId);
		if (clients != null) {
			clients.remove(client);
			if (clients.isEmpty()) {
				this.clientsNeedingConfirmation.remove(syncId);
				this.syncIds.remove(this.inverseSyncIds.remove(syncId));
			}
		}
	}

	public void sendCorrectMapping(Session client, Entry<?> entry) {
		EntryMapping oldMapping = this.mappings.getDeobfMapping(entry);
		String oldName = oldMapping.targetName();
		if (oldName == null) {
			this.sendPacket(client, new EntryChangeS2CPacket(DUMMY_SYNC_ID, EntryChange.modify(entry).clearDeobfName()));
		} else {
			this.sendPacket(client, new EntryChangeS2CPacket(0, EntryChange.modify(entry).withDeobfName(oldName)));
		}
	}

	protected abstract void runOnThread(Runnable task);

	public void log(String message) {
		Logger.info("[server] {}", message);
	}

	public byte[] getJarChecksum() {
		return this.jarChecksum;
	}

	public char[] getPassword() {
		return this.password;
	}

	public EntryRemapper getMappings() {
		return this.mappings;
	}

	public void sendMessage(ServerMessage message) {
		Logger.info("[chat] {}", message.translate());
		this.sendToAll(new MessageS2CPacket(message));
	}

	@ServerEndpoint(value = "/enigma")
	public class Endpoint {
		@OnOpen
		public void onOpen() {
			System.out.println("Connected!");
		}

		@OnMessage
		public String onMessage(String message, Session session) {
			EnigmaServer.this.clients.add(session);

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

						Packet<ServerPacketHandler> packet = PacketRegistry.createC2SPacket(packetId);
						if (packet == null) {
							throw new IOException("Received invalid packet id " + packetId);
						}

						packet.read(input);
						EnigmaServer.this.runOnThread(() -> packet.handle(new ServerPacketHandler(session, EnigmaServer.this)));
					}
				} catch (IOException e) {
					EnigmaServer.this.kick(session, e.toString());
					Logger.error("Failed to read packet from client!", e);
					return;
				}

				EnigmaServer.this.kick(session, "disconnect.disconnected");
			});
			thread.setName("Server I/O thread #" + (nextIoId++));
			thread.setDaemon(true);
			thread.start();

			// todo
			return "";
		}

		@OnClose
		public void onClose() {
			System.out.println("Closed!");
		}
	}
}
