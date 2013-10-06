
import java.lang.reflect.Type;
import java.net.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.TimerTask;
import java.util.Timer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.io.*;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class GossipUdpServer {
	public static final long TRANSMITTER_PERIOD = 500;
    DatagramSocket socket = null;
	InetSocketAddress selfInetSock = null;
    Transmitter txObj = null;
    Receiver rxObj = null;
    TimeOutManager toObj = null;
    ConcurrentHashMap<String, TableEntry> membTable = null;
	TableEntry selfEntry = null;
	public static final long TIMEOUT = 3000;
	public static final long TIMER_PERIOD = 300;
	public static final long DEADCOUNT = 15;
	public static final long TEST_MAX = 3;
	private final ReentrantLock lock = new ReentrantLock();
	BufferedWriter bw = null;
	checkDrop cDrop = null;
	checkBand cBand = null;

    public GossipUdpServer(String[] args, String localPort) {
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
    	//System.out.println("Experiment localInet" + localInet.getHostAddress());
    	//System.out.println("Experiment socket" + socket.getLocalAddress().getHostAddress());
    	//System.out.println("Experiment getInetAddress" + socket.getInetAddress());
    	selfInetSock = new InetSocketAddress(socket.getLocalAddress(), socket.getLocalPort());
    	
    	String id = selfInetSock.getHostName() + "___" + 
    	            	selfInetSock.getPort() + "___" + currentTime;
    	selfEntry = new TableEntry(id, 0);
    	membTable = new ConcurrentHashMap<String, TableEntry>();
    	membTable.put(id, selfEntry);
    	System.out.println(selfEntry.id);
    	if( args != null && args.length != 0 && args[0] != null ){
    		TableEntry contact = new TableEntry(args[0], 1);
        	membTable.put(contact.id, contact);
    	}
    	
    	try {
			bw = new BufferedWriter(new FileWriter("/tmp/testlog_" + localPort, false));
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
    	
    	//TODO get from CLI the details of the first contact and fill table.
    	cBand = new checkBand();

    	rxObj = new Receiver();
    	rxObj.start();
    	txObj = new Transmitter();
    	txObj.start();
    	toObj = new TimeOutManager();
    	toObj.start();
    	
    	cDrop = new checkDrop();
	}

    public class TableEntry {
    	String id = null;
    	long hrtBeat, jiffies;
    	boolean hasFailed;
    	long deadCount = DEADCOUNT;
    	
    	TableEntry(String id, long hrtBeat){
    		this.id = id;
    		this.hrtBeat = hrtBeat;
    		jiffies = System.currentTimeMillis();
    		hasFailed = false;
    	}
    	
    	public synchronized void updateTime(){
    		this.jiffies = System.currentTimeMillis();
    	}
    	
    	public synchronized void incHrtBeat() {
    		this.hrtBeat++;
    		updateTime();
      	}
    	
    	public synchronized boolean cmpAndUpdateHrtBeat(long hrtBeat, long currentTime) {
    		if (hrtBeat == 0) {
    			hasFailed = true;
    			System.out.println("Machine marked as Left : "+ id);
    			return true;
    		}
    		if (hrtBeat <= this.hrtBeat || hasFailed) {
    			return false;
    		}
    		//System.out.println("Changing heartbeat");
    		this.hrtBeat = hrtBeat;
    		this.jiffies = currentTime;
    		return false;
    	}
    	
    	public synchronized int timerCheck(long currentTime) {
    		if (!hasFailed && currentTime > jiffies + TIMEOUT) {
    			hasFailed = true;
    			System.out.println("Machine marked as Failed : "+ id);
    			return 0;
    		}
    		if (hasFailed) {
    			//System.out.println("Deadcount: "+ deadCount);
    			if (deadCount == 0) {
    				return 1;
    			} else {
    				deadCount--;
    			}
    		}
    		return -1;
    	}
    }

    private synchronized void SendMembList(TableEntry entry) throws UnknownHostException {
		String[] dataItems = entry.id.split("___");
		
		InetAddress address = null;
		address = InetAddress.getByName(dataItems[0]);
		int port = Integer.parseInt(dataItems[1]);
        Gson gson = new Gson();
		String tx = gson.toJson(membTable);
        byte[] outbuf = tx.getBytes();
		DatagramPacket sendpacket = new DatagramPacket(outbuf, outbuf.length, address, port);
		send(sendpacket);

    }

    private ArrayList<TableEntry> getReservoir() {
    	// Lock already taken, safe to change membTable.
    	ArrayList<TableEntry> tE = null;
    	int k = 0, size = 0;

    	
    	tE = new ArrayList<TableEntry>(10);
    	
		for(TableEntry entry : membTable.values()) {
			if (entry == selfEntry) {
				continue;
			} else if (entry.hasFailed) {
				continue;
			} else {
				tE.add(entry);
			}
		}

    	size = tE.size();
		if (size < 1) {
			tE = null;
			return tE;
    	} else if (size == 1) {
    		return tE;
    	}
    	// The array may have size elements, but there are a total of size + 1 nodes.
    	k = (int)Math.floor(Math.sqrt(size + 1));

    	ArrayList<TableEntry> subList = new ArrayList<TableEntry>(10);
    	//System.out.println("Sublist size: " + subList.size());

        Random ra = new Random();
    	int randomNumber;
    	TableEntry temp = null;
    	//System.out.println("Size to send: " + k + "Out of: " + size);
    	for (int i = k; i < size; i ++) {
    		randomNumber = (int)ra.nextInt(i + 1);
        	//System.out.println("Random Number: " + randomNumber);
       		if (randomNumber < k) {
    			//swap
    			temp = tE.get(i);
    			tE.set(i, tE.get(randomNumber));
    			tE.set(randomNumber, temp);
    		}
    	}

        subList.addAll(tE.subList(0, k));
    	//System.out.println("Sublist size: " + subList.size());
        return subList;
    }
    
    private class checkDrop {
    	int running_count = 0;
    	long counts = 0;
    	int[] arrCheck = {29,34,41,72,97};
    	boolean trigger = false;
    }
    
    private void dropSend(DatagramPacket packet) {
    	if (membTable.size() < TEST_MAX && !cDrop.trigger) {
    		send(packet);
    		return;
    	}
		cDrop.counts++;
		if (cDrop.counts < 60) {
    		send(packet);
    		return;
		} else if (cDrop.counts == 60) {
			System.out.println("----------Start Dropping---------");
		}
		cDrop.trigger = true;
    	
    	if (cDrop.running_count == 99) {
    		System.out.println("--------Cycle Boundary-------");
    		cDrop.running_count = -1;
    	}
		cDrop.running_count ++;
    	for (int i : cDrop.arrCheck) {
    		if (i == cDrop.running_count) {
    			//System.out.println("-----Skipping Packet Send-----");
    			return;
    		}
    	}
    	send(packet);
    }
 
    private class checkBand {
    	double byte_count = 0;
    	double time_last = 0;
    	double bandWidth = 0;
    	double prev_count = 0;
    	int lastBand = 0;
    	public checkBand() {
    		time_last = System.currentTimeMillis();
    	}
    	
    	public void calcBand(long time) {
    		if (lastBand != 20) {
    			lastBand++;
    			return;
    		}
    		bandWidth = (byte_count - prev_count)/((time - time_last));
    		time_last = time;
    		prev_count = byte_count;
    		System.out.println(time + " " + bandWidth);
    		lastBand = 0;
    	}
    	
    }

    private void bandSend(DatagramPacket packet) {
    	cBand.byte_count += packet.getData().length;
    	send(packet);
    }
    
    private void send(DatagramPacket packet) {
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
			//System.out.println("Recieve: Got " + rx);
			Gson gson = new Gson();
			Type collectionType = new TypeToken<HashMap<String,TableEntry>>(){}.getType();
			HashMap<String, TableEntry> temp = gson.fromJson(rx,collectionType);
            mergeMembTable(temp);
			return false;
		}


		public void mergeMembTable(HashMap<String, TableEntry> temp) {
			long currentTime = System.currentTimeMillis();
			for (TableEntry entry: temp.values()) {
				if (entry.hasFailed) {
					continue;
				}
				if (membTable.containsKey(entry.id)) {
					//System.out.println("Known Machine");
					TableEntry oldEntry = membTable.get(entry.id);
					if (oldEntry.cmpAndUpdateHrtBeat(entry.hrtBeat, currentTime)) {
		    			try {
							bw.write(entry.id + ": Left   at " + new Date(currentTime));
							bw.newLine(); 
							bw.flush();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				}else{
					//System.out.println("New Entry: " + entry.id);
					membTable.put(entry.id, entry);
					entry.updateTime();
					if (entry.hrtBeat >= 1) {
						if (entry.hrtBeat < 5) {
							try {
								System.out.println("Joined: " + entry.id);
								bw.write(entry.id + ": Joined at " + new Date(currentTime));
								bw.newLine();
								bw.flush();
							} catch (IOException e) {
								e.printStackTrace();
							}
						}
						if (entry.hrtBeat > 1) {
							//Spaghetti code.
							return;
						}
					}
					// Only the first to be introduced need to send all of his
					// table to joinee, rest will be taken up by joinee in Tx,
					// as the rest of the members are not waiting more than
					// joinee can reach some form of gossip to them.
					try {
						lock.lock();
						// This was added to take care of corner case when first member joins
						// and the intro itself is the first join.
						selfEntry.hrtBeat++;
						selfEntry.incHrtBeat();
						//System.out.println("Tx Intro to " + entry.id);
						SendMembList(entry);
					} catch (UnknownHostException e) {
						e.printStackTrace();
					} finally {
						lock.unlock();
					}
				}
			}
		}
		@Override
		public void run() {
			//System.out.println("Recieve: Running");
			// TODO Auto-generated method stub
			while(true) {	
				byte[] buf = new byte[4096];
				DatagramPacket packet = new DatagramPacket(buf, buf.length);
				try {
					socket.receive(packet);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				if(processPacket(packet)) {
					//System.out.println("Recieve: Done");
					break;
				}
			}
		}
	}
	
	private class TimeOutManager extends TimerTask{

		Timer timeoutTimer = null;

		public void start() {
			// TODO Run this every some other time
			timeoutTimer = new Timer();
			timeoutTimer.schedule(this, 0, TIMER_PERIOD);
		}

		@Override
		public void run() {
			lock.lock();
			// TODO Auto-generated method stub
			long currentTime = System.currentTimeMillis();
			List<String> toBeDeleted = new ArrayList<String>();
			
			//cBand.calcBand(currentTime);
			
			Iterator<TableEntry> iterator = membTable.values().iterator();
			while(iterator.hasNext()) {
				TableEntry entry = iterator.next();
				if(entry.equals(selfEntry)){
					continue;
				}
				int k = entry.timerCheck(currentTime);
				if(k == 1){
					toBeDeleted.add(entry.id);
				} else if (k == 0) { 
	    			try {
						bw.write(entry.id + ": Failed at " + new Date(currentTime));
						bw.newLine();
						bw.flush();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
			
			for(String id: toBeDeleted){
				membTable.remove(id);
			}
			lock.unlock();
		}
		
	}

	private class Transmitter extends TimerTask {
	    Timer transmitterTimer = null;
		
		public void start() {
			// TODO Run this every some other time
			transmitterTimer = new Timer();
			transmitterTimer.schedule(this, 0, TRANSMITTER_PERIOD);
		}

		public void stop() {
			// TODO Auto-generated method stub
			transmitterTimer.cancel();
		}

		@Override
		public void run() {
			lock.lock();
			//System.out.println("Transmitter: Running");
			// TODO Get two machines at random from membTable and send membTable to those machines.
			
			ArrayList<TableEntry> tE = getReservoir();
			
			if (tE == null) {
				lock.unlock();
				return;
			} else {
				// Increment heart beat as there is at least one member.
				selfEntry.incHrtBeat();
			}

			//Loop over the members of the randomized list.
			//System.out.println("--------------------------");
			for (TableEntry entry: tE) {
				try {
					//System.out.println("Tx Gossip to " + entry.id);
					SendMembList(entry);
				} catch (UnknownHostException e) {
					e.printStackTrace();
					continue;
				}
			}
			//System.out.println("Transmitter: Done");
			lock.unlock();
		}
	}

	public void shutdown() {
		toObj.cancel();
		txObj.cancel();
		rxObj.interrupt();
		System.out.println("Shutting down Gossip");
		selfEntry.hrtBeat = 0;
		try {
			System.out.println("Leaving " + selfEntry.id);
			for (TableEntry entry: membTable.values()) {
				if (entry != selfEntry && !entry.hasFailed) {
					SendMembList(entry);
				}
			}
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		try {
			bw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		
	}
}
