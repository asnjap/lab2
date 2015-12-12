package chatserver;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;


public class UDPListenerThread extends Thread{
	private DatagramSocket datagramSocket;
	private Chatserver chatserver;

	public UDPListenerThread(DatagramSocket datagramSocket, Chatserver chatserver) {
		this.datagramSocket = datagramSocket;
		this.chatserver = chatserver;
	}

	public void run() {

		byte[] buffer;
		DatagramPacket packet;
		try {
			while (true) {
				buffer = new byte[1024];

				packet = new DatagramPacket(buffer, buffer.length);

				// wait for incoming packets from client
				datagramSocket.receive(packet);
				// get the data from the packet
				String request = new String(packet.getData());

				String response = "!error provided command is not !list";
				
				if (request.startsWith("!list")){
						response = chatserver.getOnlineUsers();
				}		
				InetAddress address = packet.getAddress();
				int port = packet.getPort();
				buffer = response.getBytes();

				packet = new DatagramPacket(buffer, buffer.length, address, port);
				// finally send the packet
				datagramSocket.send(packet);
			}

		} catch (IOException e) {
			System.out.println("UDP socket closed. Stop listening for connections.");
			//udp connection was interrupted
		}
	}
	
	public void exit(){
		if(datagramSocket != null)
		datagramSocket.close();
	}
}
