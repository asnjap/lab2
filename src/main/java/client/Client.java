package client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import util.Config;

public class Client implements IClientCli, Runnable {

	private String componentName;
	private Config config;
	private InputStream userRequestStream;
	private PrintStream userResponseStream;
	private BufferedReader in;
	private PrintWriter out;
	private Socket socket;
	private DatagramSocket datagramSocket;
	private ServerSocket privateSocket;
	private DatagramPacket packet;
	private String input;
	private BufferedReader userIn;
	private InputHandler inHandler;
	private MessageHandler msgHandler;
	private int udpPortNumber;
	private String host;
	private boolean shutdown = false;
	
	private BlockingQueue<String> msgQueue = new LinkedBlockingQueue<>();
	
	/**
	 * @param componentName
	 *            the name of the component - represented in the prompt
	 * @param config
	 *            the configuration to use
	 * @param userRequestStream
	 *            the input stream to read user input from
	 * @param userResponseStream
	 *            the output stream to write the console output to
	 */
	public Client(String componentName, Config config,
			InputStream userRequestStream, PrintStream userResponseStream) {
		this.componentName = componentName;
		this.config = config;
		this.userRequestStream = userRequestStream;
		this.userResponseStream = userResponseStream;
	}

	@Override
	public void run() {
		host = config.getString("chatserver.host");
        int tcpPortNumber = config.getInt("chatserver.tcp.port");
        udpPortNumber = config.getInt("chatserver.udp.port");
		
        try {
            this.socket = new Socket(host, tcpPortNumber);
            this.datagramSocket = new DatagramSocket();
            out = new PrintWriter(socket.getOutputStream(), true);
            in= new BufferedReader(new InputStreamReader(socket.getInputStream()));
            userIn = new BufferedReader(new InputStreamReader(this.userRequestStream));
            inHandler = new InputHandler();
            inHandler.start();
            msgHandler = new MessageHandler();
            msgHandler.start();
            
            try {
				inHandler.join();
				msgHandler.join();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        }catch(UnknownHostException e){
        	System.err.println("Cannot connect to host: " + e.getMessage());
        }
        catch(IOException e){
        	System.err.println(componentName + ": " + e.getMessage());
        }
        finally {
			if(userRequestStream != null)
				try {
					userRequestStream.close();
				} catch (IOException e) {
					//Do nothing (nothing can be done)
				}
			
			if(userResponseStream != null)
				userResponseStream.close();
		}
	}
	

	@Override
	public String login(String username, String password) throws IOException {
			out.println("!login " + username + " " + password);
        	try {
				return msgQueue.take();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        	return null;
	}

	@Override
	public String logout() throws IOException {
		out.println("!logout");
    	try {
			return msgQueue.take();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	return null;
	}

	@Override
	public String send(String message) throws IOException {
		out.println("!send" + " " + message);
    	try {
			return msgQueue.take();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	return null;
	}

	@Override
	public String list() throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String msg(String username, String message) throws IOException {
		out.println("!msg" + " " + username + " " + message);
    	try {
			return msgQueue.take();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	return null;
	}

	@Override
	public String lookup(String username) throws IOException {
		out.println("!lookup" + " " + username);
    	try {
			return msgQueue.take();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	return null;
	}

	@Override
	public String register(String privateAddress) throws IOException {
		out.println("!register" + " " + privateAddress);
    	try {
			return msgQueue.take();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	return null;
	}
	
	@Override
	public String lastMsg() throws IOException {
		out.println("!lastMsg");
    	try {
			return msgQueue.take();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	return null;
	}
	
	public PrintStream getUserResponseStream(){
		return this.userResponseStream;
	}
	
	@Override
	public String exit() throws IOException {
		
		if(msgQueue.isEmpty() || !msgQueue.peek().equals("null")){
			logout();
		}
		shutdown = true;
		
		if(userRequestStream != null)
			userRequestStream.close();	
		
		if (out != null)
			out.close();
		
		if(privateSocket!=null && !privateSocket.isClosed())
			privateSocket.close();
		
		if(datagramSocket != null && !datagramSocket.isClosed())
			datagramSocket.close();
		
		//closing the stream instead of buffer to be able to interrupt the message handler thread
		//socket.getInputStream().close();
		
		
		//if (in != null)
		//	in.close();
		
		if(socket!=null && !socket.isClosed())
			socket.close();
		
		
		return "Shutting down " + componentName + " now.";
	}

	/**
	 * @param args
	 *            the first argument is the name of the {@link Client} component
	 */
	public static void main(String[] args) {
		Client client = new Client(args[0], new Config("client"), System.in,
				System.out);
		client.run();
	}

	// --- Commands needed for Lab 2. Please note that you do not have to
	// implement them for the first submission. ---

	@Override
	public String authenticate(String username) throws IOException {
		// TODO Auto-generated method stub
		return null;
	}
	
	private class PacketListener extends Thread{
		public void run(){
				try {
					datagramSocket.receive(packet);
					userResponseStream.println(new String(packet.getData()));
				} catch (IOException e) {
					System.err.println("Socket closed. Stop waiting for package.");
					
				}
			}
	}
	
	private class InputHandler extends Thread {
		public void run() {
			byte[] buffer;
			while (!shutdown) {
				try {
					input = userIn.readLine();
					
					String response = "Unknown command.";

					if (input.startsWith("!login")) {
						String[] parts = input.split(" ");				
						if(parts.length != 3){
							response = "Wrong amount of arguments for login";
						}else{
							response = login(parts[1], parts[2]);
						}
					}
					
					if(input.startsWith("!logout")){
						response = logout();
					}
					
					if(input.startsWith("!send")){
						String[] parts = input.split(" ");
						if(parts.length < 2){
							response = "Please type in the message";
						}else{
						String msg = "";
						for(int i = 1; i < parts.length; i++){
							msg += parts[i] + " ";
						}
						response = send(msg);		}
					}	
					if(input.startsWith("!register")){
						String[] parts = input.split(" ");
						if(parts.length!=2){
							response = "Wrong number of arguments for !register!";
						}
						else{
						String[] address = parts[1].split(":");
						if(address.length!=2){
							response = "Please give the address of form IP:Port";
						}
						else{
						response = register(parts[1]);
						}
						}
					}
					if(input.startsWith("!lookup")){
						String[] parts = input.split(" ");
						if(parts.length != 2){
							response = "Wrong number of arguments for !lookup";
						}else{
						response = lookup(parts[1]);
						}
					}
					if(input.startsWith("!msg")){
						String[] parts = input.split(" ");
						if(parts.length < 3){
							response ="Wrong number of arguments for !msg";
						}else{
						String message = "";
						for(int i = 2; i < parts.length; i++){
							message += parts[i] + " ";
						}
						
						response = msg(parts[1], message);}
					}
					if(input.startsWith("!lastMsg")){
						response = lastMsg();
					}
					if(input.startsWith("!list")){
						buffer = input.getBytes();
						packet = new DatagramPacket(buffer, buffer.length,
								InetAddress.getByName(host),
								udpPortNumber);
						datagramSocket.send(packet);
						buffer = new byte[1024];
						packet = new DatagramPacket(buffer, buffer.length);
						new PacketListener().start();
						continue;
						
					}
					
					if(input.startsWith("!exit")){
						response = exit();
						userRequestStream.close();
					}
					if(response!=null){
						userResponseStream.println(response);
					}
						
				} catch (IOException e) {
					System.err.println("Shutting down " + componentName + " now: " + e.getMessage());
					break;
				}
			}
		}
	}
	
	private class MessageHandler extends Thread{
		public void run(){
			while(!shutdown){
				try {
					String msg = in.readLine();
					if(msg!=null){
						if(msg.startsWith("!public: ") || msg.startsWith("!private: ")){
							userResponseStream.println(msg);
						}else{
							msgQueue.put(msg);
						}
					}else{
						close();
						
					}
				} catch (IOException e) {
					close();
				} catch (InterruptedException e) {
					close();
				}
			}
		}
		private void close(){
			try {
				msgQueue.put("null");
			} catch (InterruptedException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			shutdown = true;
			System.err.println("Problem with connection. Shutting down client now...");
		}
	}

}
