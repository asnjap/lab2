package chatserver;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TCPListenerThread extends Thread {

	ServerSocket serverSocket;
	Chatserver chatserver;
	ExecutorService exec;
	List<ClientThread> clientThreads = new ArrayList<>();
	List<Socket> clientSockets = new ArrayList<>();

	public TCPListenerThread(ServerSocket serverSocket, Chatserver chatserver) {
		this.chatserver = chatserver;
		this.serverSocket = serverSocket;
	}

	@Override
	public void run() {
		try {
			exec = Executors.newFixedThreadPool(20);
			while (true) {
				Socket clientSocket = serverSocket.accept();
				ClientThread c = new ClientThread(clientSocket, chatserver, clientThreads);
				clientThreads.add(c);
				clientSockets.add(clientSocket);
				exec.execute(c);
			}

		} catch (IOException e) {
			this.chatserver.getUserResponseStream().println("TCP socket closed. Stop listening for connections.");
		}
	}

	public void exit() {
		try {
			serverSocket.close();
		} catch (IOException e) {
			// nothing can be done about it
		}
		for (Socket s : clientSockets) {
			if (s != null && !s.isClosed())
				try {
					s.close();
				} catch (IOException e) {
					// nothing can be done about it
				}
		}
		exec.shutdown();
	}
}
