

import java.net.*;
import java.util.HashMap;
import java.util.TimerTask;
import java.util.Timer;
import java.io.*;

public class GossipUdpServer {
	final long OFFSET = 3000;
    DatagramSocket socket = null;
	InetSocketAddress selfInetSock = null;
    Timer keepAliveTimer = null;
    Transmitter kpObj = null;
    Receiver rxObj = null;
    HashMap<String, TableEntry> membTable = null;

 
    public GossipUdpServer() {
    	long currentTime = System.currentTimeMillis();
    	for (int i = 1024; i < 1500; i++) {
		    try {
				socket = new DatagramSocket(i);
			} catch (SocketException e) {
			    System.err.printf("Could not listen on port: %d, Trying %d\n", i, i+1);
				continue;
			}
		    break;
    	}
    	selfInetSock = new InetSocketAddress(socket.getLocalAddress(), socket.getLocalPort());
    	
    	String id = selfInetSock.getAddress().toString() + ":" + 
    	            	selfInetSock.getPort() + ":" + currentTime;
    	TableEntry first = new TableEntry(id, 0);
    	membTable.put(id, first);
    	
    	//TODO get from CLI the details of the first contact and fill table.
    	rxObj = new Receiver();
    	rxObj.start();
    	kpObj = new Transmitter();
    	kpObj.start();
	}

    public class TableEntry {
    	String id = null;
    	long hrtBeat, jiffies;
    	boolean hasFailed;
    	int deadCount = 10;
    	TableEntry(String id, long hrtBeat){
    		this.id = id;
    		this.hrtBeat = hrtBeat;
    		jiffies = System.currentTimeMillis();
    		hasFailed = false;
    	}
    	
    	public void incHrtBeat() {
    		this.hrtBeat++;
    		this.jiffies = System.currentTimeMillis();
      	}
    	
    	public void cmpAndUpdateHrtBeat(long hrtBeat, long currentTime) {
    		if (hrtBeat <= this.hrtBeat || hasFailed) {
    			return;
    		}
    		this.hrtBeat = hrtBeat;
    		this.jiffies = currentTime;
    	}
    	
    	public void timerCheck(long currentTime) {
    		if (currentTime > jiffies + OFFSET) {
    			hasFailed = true;
    		}
    		if (hasFailed) {
    			if (deadCount == 0) {
    				membTable.remove(id);
    			} else {
    				deadCount--;
    			}
    		}
    	}
    }

    public synchronized void send(DatagramPacket packet) {
		try {
			socket.send(packet);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	
	private class Receiver extends Thread {

		private boolean processPacket(DatagramPacket packet) {
			String rx = new String(packet.getData(), 0, packet.getLength());
			System.out.println("Recieve: Got " + rx);
			
			if (rx.equals("Table")) {
                mergeMembTable(null);
			} if (rx.equals("OK")) {
			}
			return false;
		}


		public void mergeMembTable(HashMap<String, TableEntry> temp) {
			long currentTime = System.currentTimeMillis();
			for (TableEntry entry: temp.values()) {
				if (membTable.containsKey(entry.id)) {
					TableEntry oldEntry = membTable.get(entry.id);
					oldEntry.cmpAndUpdateHrtBeat(entry.hrtBeat, currentTime);
				}
			}
		}
		@Override
		public void run() {
			System.out.println("Recieve: Running");
			// TODO Auto-generated method stub
			while(true) {	
				byte[] buf = new byte[512];
				DatagramPacket packet = new DatagramPacket(buf, buf.length);
				try {
					socket.receive(packet);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				if(processPacket(packet)) {
					System.out.println("Recieve: Done");
					break;
				}
			}
		}
	}
	
	private class Transmitter extends TimerTask {

		public void start() {
			// TODO Run this every some other time
			keepAliveTimer = new Timer();
			keepAliveTimer.schedule(this, 0, 3*1000);
		}

		@Override
		public void run() {
			System.out.println("Transmitter: Running");
			// TODO Get two machines at random from membTable and send membTable to those machines.
			
			InetAddress address = selfInetSock.getAddress();
			int port = selfInetSock.getPort();
            String tx = "Hi";
            byte[] outbuf = tx.getBytes();
			DatagramPacket sendpacket = new DatagramPacket(outbuf, outbuf.length, address, port);
			send(sendpacket);
			System.out.println("Transmitter: Done");
		}
	}
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		GossipUdpServer gen = new GossipUdpServer();
		
	}
}
