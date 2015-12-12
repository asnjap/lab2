package chatserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import util.Config;

public class Chatserver implements IChatserverCli, Runnable {

	private String componentName;
	private Config config;
	private InputStream userRequestStream;
	private PrintStream userResponseStream;
	private ServerSocket serverSocket;
	private DatagramSocket datagramSocket;
	private ConcurrentHashMap<String, User> users = new ConcurrentHashMap<>();
	private TCPListenerThread tcpthread;
	private UDPListenerThread udpthread;
	

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
	public Chatserver(String componentName, Config config,
			InputStream userRequestStream, PrintStream userResponseStream) {
		this.componentName = componentName;
		this.config = config;
		this.userRequestStream = userRequestStream;
		this.userResponseStream = userResponseStream;
	}

	@Override
	public void run() {
		//userResponseStream.println("Congratulations. You have just run " + componentName);
		
		Config userConfig = new Config("user");
        for (String userKey : userConfig.listKeys()) {
                User user = new User(userKey.replace(".password", ""), userConfig.getString(userKey), false);
                users.put(userKey.replace(".password",  ""), user);
        }
        
        //open sockets for TCP and UDP communication
		config = new Config("chatserver");		
		try {
			serverSocket = new ServerSocket(config.getInt("tcp.port"));
			datagramSocket = new DatagramSocket(config.getInt("udp.port"));
		}catch (SocketException e) {
			throw new RuntimeException("Cannot listen on UDP port.", e);		
		} catch (IOException e) {
			throw new RuntimeException("Cannot listen on TCP port.", e);
		}
		
		//start threads to listen for connections
		udpthread = new UDPListenerThread(datagramSocket, this);
		udpthread.start();
		
		tcpthread = new TCPListenerThread(serverSocket, this);
		tcpthread.start();

        BufferedReader userRequestReader = new BufferedReader(new InputStreamReader(userRequestStream));
        PrintWriter userResponseWriter = new PrintWriter(userResponseStream, true);
        
		while(true){
			try{
				String input = userRequestReader.readLine();
				switch(input){
					case "!exit":
						userResponseWriter.println(this.exit());
						return;
					case "!users":
						try{
							userResponseWriter.println(this.users());
						}catch(IOException e){
							System.err.println("Could not print the list of all users");
						}
						break;
					default: userResponseWriter.println("Unknown command.");
				}
			}catch(IOException e){
				//highly unlikely
			}
			
		}
	}
	public User getUser(String username){
		return users.get(username);
	}
	
	public InputStream getUserRequestStream(){
		return this.userRequestStream;
	}
	
	public PrintStream getUserResponseStream(){
		return this.userResponseStream;
	}
	
	public String getOnlineUsers(){
		List<User> onlineUsers = new ArrayList<>();
		synchronized(users){
			for(User u: users.values()){
				if(u.isOnline()){
					onlineUsers.add(u);
					}
			}
		}
		Collections.sort(onlineUsers, new Comparator<User>() {
	        @Override
	        public int compare(final User object1, final User object2) {
	            return object1.getUsername().compareTo(object2.getUsername());
	        }
	       } );
		
		String result = "";
		
		for(User u: onlineUsers){
			result += u.toString() + "\n";
		}
		if(result.equals(""))
			return "There are no online users.";
		
		return "Online users: \n" + result;
	}


	@Override
	public String users() throws IOException {

		List<User> userList = new ArrayList<>();
		userList.addAll(users.values());
		
		Collections.sort(userList, new Comparator<User>() {
	        @Override
	        public int compare(final User object1, final User object2) {
	            return object1.getUsername().compareTo(object2.getUsername());
	        }
	       } );
		
		String result = "";
		for(User u: userList){
			result += u + "\n";
		}
		return result;
	}

	//@Override
	public String exit(){
		
		for (User user : users.values())
			user.setOnline(false);
		
        if(tcpthread != null) {
            tcpthread.exit();
        }
        if(udpthread != null) {
            udpthread.exit();
        }
        try{
        	userRequestStream.close();
        }catch(IOException e){
        	//nothing can be done about it
        }
		return "Server successfully shut down";
	}
	

	/**
	 * @param args
	 *            the first argument is the name of the {@link Chatserver}
	 *            component
	 */
	public static void main(String[] args) {
		Chatserver chatserver = new Chatserver(args[0],	new Config("chatserver"), System.in, System.out);
		chatserver.run();
	}

}
