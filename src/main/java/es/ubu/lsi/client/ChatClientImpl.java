package es.ubu.lsi.client;

import java.io.*;
import java.net.*;
import java.util.*;

import es.ubu.lsi.common.ChatMessage;
import es.ubu.lsi.common.ChatMessage.MessageType;

/**
 * Client.
 * 
 */
public class ChatClientImpl implements ChatClient {

	private ObjectInputStream sInput; // to read from the socket
	private ObjectOutputStream sOutput; // to write on the socket
	private Socket socket;

	private String server;
	private String username;
	private int port;

	private boolean carryOn = true;
	private int id;

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
			String msg = "Connection accepted " + socket.getInetAddress() + ":" + socket.getPort();
			display(msg);

			sInput = new ObjectInputStream(socket.getInputStream());
			sOutput = new ObjectOutputStream(socket.getOutputStream());

		} catch (IOException eIO) {
			display("Exception creating new Input/output Streams: " + eIO);
			return false;
		} catch (Exception ec) {
			display("Error connecting to server:" + ec);
			return false;
		}

		try {
			sOutput.writeObject(username);
			sOutput.flush();
			id = sInput.readInt();
		} catch (IOException eIO) {
			display("Exception doing login : " + eIO);
			disconnect();
			return false;
		}

		new Thread(new ChatClientListener()).start();
		return true;
	}

	private void display(String msg) {
		System.out.println(msg);
	}

	@Override
	public synchronized void sendMessage(ChatMessage msg) {
		try {
			if (this.carryOn) {
				sOutput.writeObject(msg);
			}
		} catch (IOException e) {
			display("Exception writing to server: " + e);
		}
	}

	@Override
	public void disconnect() {
		try {
			display("Disconnecting client " + username);
			if (sInput != null) {
				sInput.close();
				sInput = null;
			}
			if (sOutput != null) {
				sOutput.close();
				sOutput = null;
			}
			if (socket != null && !socket.isClosed()) {
				socket.close();
				socket = null;
			}
		} catch (Exception e) {
			display("Disconnect with error, closing resources.");
		} finally {
			carryOn = false;
			display("Bye!");
		}
	}

	public static void main(String[] args) {
		int portNumber = 1500;
		String serverAddress = "localhost";
		String userName = "Anonymous";

		switch (args.length) {
			case 3:
				serverAddress = args[2];
			case 2:
				try {
					portNumber = Integer.parseInt(args[1]);
				} catch (Exception e) {
					System.out.println("Invalid port.");
					return;
				}
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
				String userMsg = scan.nextLine();

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

				// Formateo del mensaje con username
				String formattedMsg = client.username + " patrocina el mensaje: " + userMsg;
				client.sendMessage(new ChatMessage(client.id, MessageType.MESSAGE, formattedMsg));
			}
		}

		client.disconnect();
	}

	class ChatClientListener implements Runnable {
		public void run() {
			while (true) {
				try {
					ChatMessage msg = (ChatMessage) sInput.readObject();
					String text = msg.getMessage();
					String user = "";
					if (text.contains(":"))
						user = text.split(":")[0];

					if (!bannedUsers.contains(user)) {
						System.out.println(text);
						System.out.print("> ");
					}

				} catch (IOException e) {
					display("Server has closed the connection.");
					carryOn = false;
					break;
				} catch (ClassNotFoundException e2) {
					throw new RuntimeException("Wrong message type", e2);
				}
			}
		}
	}
}