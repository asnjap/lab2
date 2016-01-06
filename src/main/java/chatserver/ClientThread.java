package chatserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.List;

import nameserver.INameserverForChatserver;
import nameserver.exceptions.AlreadyRegisteredException;
import nameserver.exceptions.InvalidDomainException;

public class ClientThread extends Thread {

	private Socket clientSocket;
	private Chatserver chatserver;
	private BufferedReader in;
	private PrintWriter out;
	private User currentUser;
	private List<ClientThread> clientThreads;
	private ServerSocket serverSocket;
	private String lastMessage = "No message received.";

	public ClientThread(Socket clientSocket, Chatserver chatserver, List<ClientThread> clientThreads) {
		this.chatserver = chatserver;
		this.clientSocket = clientSocket;
		this.clientThreads = clientThreads;
	}

	public void run() {

		while (true) {
			try {
				in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
				out = new PrintWriter(clientSocket.getOutputStream(), true);

				String input = "Unknown error";
				String response = "Some error occurred.";

				if ((input = in.readLine()) != null) {
					
					if (input.startsWith("!login")) {
						String[] parts = input.split(" ");

						if (currentUser != null) {
							response = "You are already logged in!";
						} else {
							currentUser = chatserver.getUser(parts[1]);

							if (currentUser != null && currentUser.getPassword().equals(parts[2])) {
								currentUser.setOnline(true);
								response = "Successfully logged in.";
							} else {
								currentUser = null;
								response = "Wrong username or password";
							}
						}
					}
					if (input.startsWith("!logout")) {
						if (currentUser == null)
							response = "You are not logged in!";
						else {
							boolean loggedIn = false;
							for(ClientThread t: clientThreads){
								if(t != this && t.getCurrentUser() == currentUser){
									loggedIn = true;
								}
							}
							currentUser.setOnline(loggedIn);
							if(!loggedIn)
								//currentUser.setAddress(null);
							
							if (serverSocket != null && !serverSocket.isClosed())
								serverSocket.close();
							response = "Successfully logged out!";
							currentUser = null;
							lastMessage = null;
						}
					}
					if (input.startsWith("!send")) {
						if (currentUser == null)
							response = "You must log in first!";
						else {
							String[] parts = input.split(" ");
							String msg = currentUser.getUsername() + ": ";
							for (int i = 1; i < parts.length; i++) {
								msg += parts[i] + " ";
							}
							for (ClientThread t : clientThreads) {
								if (t.getCurrentUser() != null) {
									if (t.getClientSocket() != clientSocket) {
										PrintWriter out = new PrintWriter(t.getClientSocket().getOutputStream(), true);
										t.setLastMessage(msg);
										out.println("!public: " + msg);
										out.flush();
									}
								}
							}
							response = "Message sent successfully!";
						}
					}
					if (input.startsWith("!lastMsg")) {
						if (currentUser == null)
							response = "You must log in first!";
						else {
							response = lastMessage;
						}
					}
					if (input.startsWith("!register")) {
						if (currentUser == null)
							response = "You must log in first!";
						else {
							String[] parts = input.split(" ");
							//currentUser.setAddress(parts[1]);
							try {
									chatserver.getRootNameserver().registerUser(currentUser.getUsername(), parts[1]);
									response = "Sucessfully registered address for " + currentUser.getUsername();
							}
							catch (AlreadyRegisteredException e) {
								response = "This address is already registered.";
							} catch (InvalidDomainException e) {
								// TODO Auto-generated catch block
								response = "This domain is not valid.";
							} catch(RemoteException|NotBoundException e){
								response = "Address cannot be registered: Cannot communicate with the nameserver";
							}		

						}
					}
					if (input.startsWith("!lookup")) {
						if (currentUser == null)
							response = "You must log in first!";
						else {
							String[] parts = input.split(" ");
							try{
								response = lookup(parts[1]);
							} catch(RemoteException|NotBoundException e){
								response = "Could not lookup the address: Cannot communicate with the nameserver";
							}
						}
					}
					if (input.startsWith("!msg")) {
						if (currentUser == null)
							response = "You must log in first!";
						else {
							String[] parts = input.split(" ");
							String message = "";
							for (int i = 2; i < parts.length; i++) {
								message += parts[i] + " ";
							}
								response = this.getCurrentUser().getUsername()+" :";
						}
					}
					out.println(response);
				}
			} catch (IOException e) {
				this.chatserver.getUserResponseStream().println("Closing client connection: " + e.getMessage());
				if(serverSocket != null && !serverSocket.isClosed()){
					try {
						serverSocket.close();
					} catch (IOException e1) {
						//cant handle it
					}
				}
				break;
			}
		}

	}
	
	public String lookup(String username)throws RemoteException, NotBoundException{
		
		String[] parts = username.split("\\.");
		
		INameserverForChatserver server;
		
		server = chatserver.getRootNameserver();
		
		for(int i = (parts.length)-1; i >= 1; i--){
			if(server != null){
				server = server.getNameserver(parts[i]);
			}else{
				return "No such domain.";
			}
		}
		if(server == null)
			return "No such domain.";
		
		String result = server.lookup(parts[0]);
		
		if(result == null)
			result = "User does not have a registered address.";
		
		return result;
	}

	public User getCurrentUser() {
		return currentUser;
	}

	public Socket getClientSocket() {
		return clientSocket;
	}

	public void setLastMessage(String lastMessage) {
		this.lastMessage = lastMessage;
	}

}
