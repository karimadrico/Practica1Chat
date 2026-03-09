package es.ubu.lsi.client;

import java.io.*;
import java.net.*;
import java.util.*;

import es.ubu.lsi.common.ChatMessage;
import es.ubu.lsi.common.ChatMessage.MessageType;

/**
 * Cliente de chat final
 * Maneja conexión, envío de mensajes, logout y ban/unban de usuarios.
 * Inner class ChatClientListener escucha mensajes del servidor.
 * 
 * @author Karima
 */
public class ChatClientImpl implements ChatClient {

	private ObjectInputStream sInput;
	private ObjectOutputStream sOutput;
	private Socket socket;

	private String server;
	private String username;
	private int port;
	private int id;
	private boolean carryOn = true;
	private Set<String> bannedUsers = new HashSet<>();

	public ChatClientImpl(String server, int port, String username) {
		this.server = server;
		this.port = port;
		this.username = username;
	}

	@Override
	public boolean start() {
		try {
			socket = new Socket(server, port);
			System.out.println("Connected to server " + socket.getInetAddress() + ":" + socket.getPort());

			sInput = new ObjectInputStream(socket.getInputStream());
			sOutput = new ObjectOutputStream(socket.getOutputStream());

			sOutput.writeObject(username);
			sOutput.flush();
			id = sInput.readInt();

		} catch (IOException e) {
			System.out.println("Error creating I/O streams or connecting: " + e);
			return false;
		}

		new Thread(new ChatClientListener()).start();
		return true;
	}

	@Override
	public synchronized void sendMessage(ChatMessage msg) {
		try {
			if (carryOn) {
				sOutput.writeObject(msg);
				sOutput.flush(); // Garantiza envío inmediato
			}
		} catch (IOException e) {
			System.out.println("Error sending message: " + e);
		}
	}

	@Override
	public void disconnect() {
		try {
			if (sInput != null)
				sInput.close();
			if (sOutput != null)
				sOutput.close();
			if (socket != null && !socket.isClosed())
				socket.close();
		} catch (IOException e) {
			System.out.println("Error closing resources: " + e);
		} finally {
			carryOn = false;
			System.out.println("Disconnected. Bye!");
		}
	}

	public static void main(String[] args) {
		int portNumber = 1500;
		String serverAddress = "localhost";
		String userName = "Karima";

		switch (args.length) {
			case 3:
				serverAddress = args[2];
				break;
			case 2:
				try {
					portNumber = Integer.parseInt(args[1]);
				} catch (Exception e) {
					System.out.println("Invalid port.");
					return;
				}
				break;
			case 1:
				userName = args[0];
				break;
			case 0:
				break;
			default:
				System.err.println("Usage: java ChatClientImpl [username] [portNumber] [serverAddress]");
				return;
		}

		ChatClientImpl client = new ChatClientImpl(serverAddress, portNumber, userName);
		if (!client.start())
			return;

		try (Scanner scan = new Scanner(System.in)) {
			while (client.carryOn) {
				System.out.print("> ");
				String userMsg = scan.nextLine().trim();

				if (userMsg.equalsIgnoreCase("logout")) {
					client.sendMessage(new ChatMessage(client.id, MessageType.LOGOUT, ""));
					break;
				}

				if (userMsg.startsWith("ban ")) {
					String banned = userMsg.substring(4).trim();
					client.bannedUsers.add(banned);
					System.out.println(banned + " banned.");
					continue;
				}

				if (userMsg.startsWith("unban ")) {
					String unbanned = userMsg.substring(6).trim();
					client.bannedUsers.remove(unbanned);
					System.out.println(unbanned + " unbanned.");
					continue;
				}

				// Formateo del mensaje
				String formattedMsg = client.username + " patrocina el mensaje: " + userMsg;
				client.sendMessage(new ChatMessage(client.id, MessageType.MESSAGE, formattedMsg));
			}
		}

		client.disconnect();
	}

	/**
	 * Listener para mensajes del servidor.
	 */
	class ChatClientListener implements Runnable {
		@Override
		public void run() {
			while (true) {
				try {
					ChatMessage msg = (ChatMessage) sInput.readObject();
					String text = msg.getMessage();
					String user = text.contains(":") ? text.split(":")[0] : "";

					if (!bannedUsers.contains(user)) {
						System.out.println(text);
						System.out.print("> ");
					}

				} catch (IOException e) {
					System.out.println("Server has closed the connection.");
					carryOn = false;
					break;
				} catch (ClassNotFoundException e2) {
					throw new RuntimeException("Received unknown message type", e2);
				}
			}
		}
	}
}