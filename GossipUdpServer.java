

import java.lang.reflect.Type;
import java.net.*;
import java.util.HashMap;
import java.util.TimerTask;
import java.util.Timer;
import java.io.*;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class GossipUdpServer {
	final long OFFSET = 3000;
    DatagramSocket socket = null;
	InetSocketAddress selfInetSock = null;
    Timer keepAliveTimer = null;
    Transmitter kpObj = null;
    Receiver rxObj = null;
    HashMap<String, TableEntry> membTable = null;
	TableEntry selfEntry = null;

 
    public GossipUdpServer(String[] args) {
    	long currentTime = System.currentTimeMillis();
    	InetAddress localInet = null;
		try {
			localInet = InetAddress.getLocalHost();
		} catch (UnknownHostException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			System.out.println("Could not get local host, kill");
			System.exit(0);
		}
    	for (int i = 1024; i < 1500; i++) {
		    try {
				socket = new DatagramSocket(i, localInet);
			} catch (SocketException e) {
			    System.err.printf("Could not listen on port: %d, Trying %d\n", i, i+1);
				continue;
			}
		    break;
    	}
    	System.out.println("Experiment localInet" + localInet.getHostAddress());
    	System.out.println("Experiment socket" + socket.getLocalAddress().getHostAddress());
    	System.out.println("Experiment getInetAddress" + socket.getInetAddress());
    	selfInetSock = new InetSocketAddress(socket.getLocalAddress(), socket.getLocalPort());
    	
    	String id = selfInetSock.getHostName() + ":" + 
    	            	selfInetSock.getPort() + ":" + currentTime;
    	selfEntry = new TableEntry(id, 0);
    	membTable = new HashMap<String, TableEntry>();
    	membTable.put(id, selfEntry);
    	System.out.println(args);
    	System.out.println(selfEntry.id);
    	if( args != null && args.length != 0 && args[0] != null ){
    		TableEntry contact = new TableEntry(args[0], 0);
        	membTable.put(contact.id, contact);
    	}
    	
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
    	
    	public void updateTime(){
    		this.jiffies = System.currentTimeMillis();
    	}
    	
    	public void incHrtBeat() {
    		this.hrtBeat++;
    		updateTime();
      	}
    	
    	public void cmpAndUpdateHrtBeat(long hrtBeat, long currentTime) {
    		if (hrtBeat <= this.hrtBeat || hasFailed) {
    			return;
    		}
    		System.out.println("Changing heartbeat");
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
			Gson gson = new Gson();
			Type collectionType = new TypeToken<HashMap<String,TableEntry>>(){}.getType();
			HashMap<String, TableEntry> temp = gson.fromJson(rx,collectionType);
            mergeMembTable(temp);
			return false;
		}


		public void mergeMembTable(HashMap<String, TableEntry> temp) {
			System.out.println(temp.toString());
			long currentTime = System.currentTimeMillis();
			for (TableEntry entry: temp.values()) {
				if (membTable.containsKey(entry.id)) {
					System.out.println("Known Machine");
					TableEntry oldEntry = membTable.get(entry.id);
					oldEntry.cmpAndUpdateHrtBeat(entry.hrtBeat, currentTime);
				}else{
					System.out.println("New Machine");
					membTable.put(entry.id, entry);
					entry.updateTime();
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
			boolean incr_hrtbt = true;
			//Loop over the members of membTable
			for (TableEntry entry: membTable.values()) {
				if(entry.equals(selfEntry)){
					continue;
				}

				// Increment Heartbeat once only if there are other known machines
				if(incr_hrtbt){
					selfEntry.incHrtBeat();
					incr_hrtbt = false;
				}
				String[] dataItems = entry.id.split(":");
				
				InetAddress address = null;
				try {
					address = InetAddress.getByName(dataItems[0]);
				} catch (UnknownHostException e) {
					e.printStackTrace();
					continue;
				}
				int port = Integer.parseInt(dataItems[1]);
	            Gson gson = new Gson();
				String tx = gson.toJson(membTable);
	            byte[] outbuf = tx.getBytes();
				DatagramPacket sendpacket = new DatagramPacket(outbuf, outbuf.length, address, port);
				send(sendpacket);
			}
			System.out.println("Transmitter: Done");
		}
	}
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		GossipUdpServer gen = new GossipUdpServer(args);
		
	}
}
