package nameserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.rmi.AlreadyBoundException;
import java.rmi.NoSuchObjectException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.MissingResourceException;
import java.util.concurrent.ConcurrentHashMap;


import nameserver.exceptions.AlreadyRegisteredException;
import nameserver.exceptions.InvalidDomainException;
import util.Config;

/**
 * Please note that this class is not needed for Lab 1, but will later be used
 * in Lab 2. Hence, you do not have to implement it for the first submission.
 */
public class Nameserver implements INameserverCli, Runnable, INameserver{

	private String componentName;
	private Config config;
	private InputStream userRequestStream;
	private PrintStream userResponseStream;
	private String rootID;
	private String registryHost;
	private int registryPort;
	private String domain;
	private Registry registry;
	private INameserver remote;
	private ConcurrentHashMap<String, String> addresses = new ConcurrentHashMap<>();
	private ConcurrentHashMap<String, INameserver> zones = new ConcurrentHashMap<>();

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
	public Nameserver(String componentName, Config config,
			InputStream userRequestStream, PrintStream userResponseStream) {
		this.componentName = componentName;
		this.config = config;
		this.userRequestStream = userRequestStream;
		this.userResponseStream = userResponseStream;
		this.rootID = config.getString("root_id");
		this.registryHost = config.getString("registry.host");
		this.registryPort = config.getInt("registry.port");
		
		try{
			this.domain = config.getString("domain");
		}
		catch(MissingResourceException e){
			domain = null;
		}
	}

	@Override
	public void run() {
		
        BufferedReader userRequestReader = new BufferedReader(new InputStreamReader(userRequestStream));
        PrintWriter userResponseWriter = new PrintWriter(userResponseStream, true);
        
        
		if(domain == null){
			//nameserver is the root nameserver
			try {
				
				//create registry on localhost
				registry = LocateRegistry.createRegistry(registryPort);
				
				//reate a remote object of this nameserver object
				 remote = (INameserver) UnicastRemoteObject.exportObject(this, 0);
				
				// bind the obtained remote object on specified binding name in the
				// registry
				registry.bind(this.rootID, remote);
				
			} catch (RemoteException e) {
				throw new RuntimeException("Error while starting nameserver.", e);
			} catch(AlreadyBoundException e){
				throw new RuntimeException("Error while binding remote object to registry.", e);
			}
			userResponseWriter.println(new Timestamp(System.currentTimeMillis()) + " : " + this.componentName + " is up!");
			
		}else{
			INameserver rootServer;
			try {
				registry = LocateRegistry.getRegistry(registryHost, registryPort);
				rootServer = (INameserver) registry.lookup(rootID);
				
			} catch (RemoteException e) {
				userResponseWriter.println("An error while obtaining the registry/root nameserver remote object.");
				return;
			} catch (NotBoundException e) {
				userResponseWriter.println("Error while looking for nameserver remote object.");
				return;
			}
			
			try{
				try {
					//SETUP A CALLBACK OBJECT
					remote = (INameserver) UnicastRemoteObject.exportObject(this, 0);
					userResponseWriter.println(new Timestamp(System.currentTimeMillis()) + " : " + "Registering nameserver for zone " + domain + "..." );
					rootServer.registerNameserver(domain, remote, remote);
					userResponseWriter.println(new Timestamp(System.currentTimeMillis()) + " : " + this.componentName + " is up!");
					
				}catch (AlreadyRegisteredException e) {
					userResponseWriter.println("This domain is already registered.");
					exit();
					return;
				} catch (InvalidDomainException e) {
					userResponseWriter.println("The requested domain is invalid");
					exit();
					return;
				} catch (RemoteException e) {
					userResponseWriter.println("An error occurred during the registration.");
					exit();
					return;
				} 
			}
			catch(IOException e){
				//highly unlikely
			}
		}
		
		
		while(true){
			try{
				String input = userRequestReader.readLine();
				switch(input){
					case "!exit":
							userResponseWriter.println(this.exit());
							return;
					case "!addresses":
							userResponseWriter.println(this.addresses());
						break;
					case "!nameservers":
						userResponseWriter.println(this.nameservers());
						break;
					default: userResponseWriter.println("Unknown command.");
				}
			}catch(IOException e){
				System.err.println("There was an error: " + e);
				//highly unlikely
			}
			
		}		
	}
	

	@Override
	public String nameservers() throws IOException {		
		List<String> serverList = new ArrayList<>();
		serverList.addAll(zones.keySet());
		
		Collections.sort(serverList);
		
		String result = "";
		int i = 1;
		for(String s: serverList){
			result += i + ". " + s + "\n";
			i++;
		}
		return result;
	}

	@Override
	public String addresses() throws IOException {
		
		List<String> userList = new ArrayList<>();
		userList.addAll(addresses.keySet());
		
		Collections.sort(userList);
		
		String result = "";
		int i = 1;
		for(String s: userList){
			if(!addresses.get(s).equals(""))
			result += i + ". " + s + " " + addresses.get(s) + "\n";
			i++;
		}
		return result;
	}

	@Override
	public String exit() throws IOException {
		
		try {
			// unexport the previously exported remote object
			UnicastRemoteObject.unexportObject(this, true);
		} catch (NoSuchObjectException e) {
			System.err.println("Error while unexporting object: "
					+ e.getMessage());
		}
		
		if(domain == null){
			try {
				// unbind the remote object so that a client can't find it anymore
				registry.unbind(this.rootID);
			} catch (Exception e) {
				System.err.println("Error while unbinding object: "
						+ e.getMessage());
			}
			UnicastRemoteObject.unexportObject(registry, true);
		}
		
        try{
        	userRequestStream.close();
        }catch(IOException e){
        	//nothing can be done about it
        }
		
		return "Sucessfully shutdown the nameserver: " + this.componentName;
	}

	/**
	 * @param args
	 *            the first argument is the name of the {@link Nameserver}
	 *            component
	 */
	public static void main(String[] args) {
		Nameserver nameserver = new Nameserver(args[0], new Config(args[0]),
				System.in, System.out);
		nameserver.run();		
	}

	
	
	@Override
	public void registerUser(String username, String address)
			throws RemoteException, AlreadyRegisteredException, InvalidDomainException {
		
		loggingForChatserver();
		
		String[] parts = username.split("\\.");

		if(parts.length == 1){
			this.addresses.put(parts[0], address);
		}
		
		else{
			String parentDomain = parts[parts.length-1];
			if(!isDomainValid(parentDomain)){
				throw new InvalidDomainException("The domain may contain only alphabetic characters");
			}
			if(!zones.containsKey(parentDomain.toLowerCase()))
				throw new InvalidDomainException("The domain does not exist");
			else{
				String username1 = username.substring(0,username.lastIndexOf("."));
				zones.get(parentDomain.toLowerCase()).registerUser(username1, address);			
				}
			}	
	}
	
	public ConcurrentHashMap<String, String> getAddresses(){
		return this.addresses;
	}

	@Override
	public INameserverForChatserver getNameserver(String zone) throws RemoteException {
		loggingForChatserver();
		return zones.get(zone.toLowerCase());
	}

	@Override
	public String lookup(String username) throws RemoteException {
		loggingForChatserver();
		return addresses.get(username);
	}

	@Override
	public void registerNameserver(String domain, INameserver nameserver,
			INameserverForChatserver nameserverForChatserver)
					throws RemoteException, AlreadyRegisteredException, InvalidDomainException {
		
		
		if(!isDomainValid(domain))
			throw new InvalidDomainException("Domain may contain only alphabetic characters");
		
		if(!domain.contains(".")){
			if(!zones.containsKey(domain.toLowerCase()))
				zones.put(domain.toLowerCase(), nameserver);
			else
				throw new AlreadyRegisteredException("The domain already exists.");
		}
		
		else{
			String[] parts = domain.split("\\.");
			String parentDomain = parts[parts.length-1];
			if(zones.containsKey(parentDomain.toLowerCase())){
				String subdomain = domain.substring(0,domain.lastIndexOf("."));
				zones.get(parentDomain.toLowerCase()).registerNameserver(subdomain, nameserver, nameserverForChatserver);			
			}
			else{
				throw new InvalidDomainException("The parent domain does not exist");
				}
			}
	}
	
	private boolean isDomainValid(String domain){
		    return domain.matches("[a-zA-Z]+(.[a-zA-Z]+)*");
	}
	
	private void loggingForChatserver(){
		
		PrintWriter userResponseWriter = new PrintWriter(userResponseStream, true);
		String dom = domain == null ? "root":domain;
		userResponseWriter.println(new Timestamp(System.currentTimeMillis()) + " : Nameserver for '" + dom + "' requested by chatserver");
		
	}
	

}
