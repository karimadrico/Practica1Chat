package es.ubu.lsi.server;

import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;

import es.ubu.lsi.common.ChatMessage;
import es.ubu.lsi.common.ChatMessage.MessageType;

/**
 * Chat server. Gestiona conexiones, broadcast de mensajes y logout.
 * Inner class ServerThreadForClient para cada cliente.
 */
public class ChatServerImpl implements ChatServer {

	private static final int DEFAULT_PORT = 1500;
	private static int clientId;
	private List<ServerThreadForClient> clients;
	private static SimpleDateFormat sdf;
	private int port;
	private boolean alive;
	private ServerSocket serverSocket;

	static {
		sdf = new SimpleDateFormat("HH:mm:ss");
	}

	public ChatServerImpl(int port) {
		this.port = port;
		clients = new ArrayList<>();
	}

	@Override
	public void startup() {
		alive = true;
		try {
			serverSocket = new ServerSocket(port);

			while (alive) {
				show("Server waiting for Clients on port " + port + ".");
				Socket socket = serverSocket.accept();
				if (!alive)
					break;

				ServerThreadForClient t = new ServerThreadForClient(socket);
				clients.add(t);
				t.start();
			}
			shutdown();
		} catch (IOException e) {
			String msg = sdf.format(new Date()) + " ServerSocket: " + e + "\n";
			show(msg);
		}
	}

	@Override
	public synchronized void shutdown() {
		try {
			serverSocket.close();
			for (int i = 0; i < clients.size(); ++i) {
				ServerThreadForClient tc = clients.get(i);
				try {
					tc.sInput.close();
					tc.sOutput.close();
					tc.socket.close();
				} catch (IOException ioE) {
					System.err.printf("Error closing streams and socket for client %d\n", i);
				}
			}
		} catch (Exception e) {
			show("Exception closing the server and clients: " + e);
		}
	}

	private void show(String event) {
		String time = sdf.format(new Date()) + " " + event;
		System.out.println(time);
	}

	@Override
	public synchronized void broadcast(ChatMessage message) {
		String time = sdf.format(new Date());
		String messageLf = time + " " + message.getMessage() + "\n";
		message.setMessage(messageLf);

		System.out.print(messageLf);

		for (int i = clients.size(); --i >= 0;) {
			ServerThreadForClient ct = clients.get(i);
			if (!ct.sendMessage(message)) {
				clients.remove(i);
				show("Disconnected Client " + ct.username + " removed from list.");
			}
		}
	}

	@Override
	public synchronized void remove(int id) {
		for (int i = 0; i < clients.size(); ++i) {
			ServerThreadForClient ct = clients.get(i);
			if (ct.id == id) {
				clients.remove(i);
				return;
			}
		}
	}

	public static void main(String[] args) {
		int portNumber = DEFAULT_PORT;
		switch (args.length) {
			case 1:
				try {
					portNumber = Integer.parseInt(args[0]);
				} catch (Exception e) {
					System.err.println("Invalid port number.");
					System.err.println("Usage: > java Server [portNumber]");
					return;
				}
				break;
			case 0:
				break;
			default:
				System.out.println("Usage: > java Server [portNumber]");
				return;
		}
		ChatServer server = new ChatServerImpl(portNumber);
		server.startup();
	}

	/**
	 * Thread para manejar cada cliente.
	 */
	private class ServerThreadForClient extends Thread {

		private Socket socket;
		private ObjectInputStream sInput;
		private ObjectOutputStream sOutput;
		private int id;
		private String username;

		private ServerThreadForClient(Socket socket) {
			id = ++clientId;
			this.socket = socket;
			System.out.println("Server thread trying to create I/O streams for client");

			try {
				sOutput = new ObjectOutputStream(socket.getOutputStream());
				sInput = new ObjectInputStream(socket.getInputStream());

				username = (String) sInput.readObject();
				sOutput.writeInt(id);
				sOutput.flush();
				show(username + " just connected.");
				show("connected with id:" + id);
				ChatMessage chatMessage = new ChatMessage(id, MessageType.MESSAGE, username + " now connected");
				broadcast(chatMessage);
			} catch (IOException e) {
				show("Exception creating new I/O Streams: " + e);
				return;
			} catch (ClassNotFoundException e) {
				close();
				throw new RuntimeException("Wrong message type", e);
			}
		}

		public void run() {
			boolean runningThread = true;
			ChatMessage chatMessage = null;

			while (runningThread) {
				try {
					chatMessage = (ChatMessage) sInput.readObject();
				} catch (IOException e) {
					show(username + " Exception reading Streams: " + e);
					break;
				} catch (ClassNotFoundException e2) {
					close();
					throw new RuntimeException("Wrong message type", e2);
				}

				switch (chatMessage.getType()) {
					case SHUTDOWN:
						show(username + " shutdown chat system.");
						runningThread = false;
						alive = false;
						break;
					case MESSAGE:
						chatMessage.setMessage(username + ": " + chatMessage.getMessage());
						broadcast(chatMessage);
						break;
					case LOGOUT:
						show(username + " disconnected with a LOGOUT message.");
						chatMessage.setMessage(username + " leaving chat room!");
						broadcast(chatMessage);
						runningThread = false;
						break;
				}
			}

			remove(id);
			show("Removing " + username + " with id: " + id);
			close();

			if (!alive) {
				shutdown();
			}
		}

		private boolean sendMessage(ChatMessage msg) {
			if (!socket.isConnected()) {
				close();
				return false;
			}
			try {
				sOutput.writeObject(msg);
				sOutput.flush(); // envío inmediato
			} catch (IOException e) {
				show("Error sending message to " + username);
				show(e.toString());
				return false;
			}
			return true;
		}

		private void close() {
			try {
				if (sOutput != null)
					sOutput.close();
				if (sInput != null)
					sInput.close();
				if (socket != null && socket.isConnected() && !socket.isClosed())
					socket.close();
			} catch (Exception e) {
				show("Closed streams and socket");
			}
		}
	}
}