package chatserver;

public class User{
	private String username;
	private String password;
	private boolean online;
	private boolean registered;
	
	public User(){}
	
	public User(String username, String password, boolean online){
		this.username = username;
		this.password = password;
		this.online = online;
	}
	
	public String getUsername(){
		return this.username;
	}
	
	public String getPassword(){
		return this.password;
	}
	
	public boolean isOnline(){
		return this.online;
	}
	
	public boolean isRegistered(){
		return this.registered;
	}
	
	public void setUsername(String username){
		this.username = username;
	}
	
	public void setPassword(String password){
		this.password = password;
	}
	
	public void setOnline(boolean online){
		this.online = online;
	}
	
	public void setRegistered(boolean registered){
		this.registered = registered;
	}
	
	@Override
	public String toString(){
		return "Username: " + this.username + " | " + (online ? "online" : "offline");
	}

	
}
