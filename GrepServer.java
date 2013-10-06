

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/*
 * The main class for server. The mainstay of a server is the socket on 
 * which it listens. It finds the useable ports and gets the socket.
 * The Acceptor is a thread within the server. This takes the socket, binds
 * on it and listens on it. It also fills the hostname, socket into a config
 * file so that potential clients can find the server.
 */

public class GrepServer {


	ServerSocket sock = null;
	String hostname = null, localPort = null;
	Acceptor acceptor;

	public GrepServer() {
		//System.out.println("Listen: Construct Start");
		for (int i = 1024; i < 1500; i ++) {
			try {
			    sock = new ServerSocket(i);
			} 
			catch (IOException e) {
			    System.err.printf("Could not listen on port: %d, Trying %d\n", i, i+1);
			    continue;
			}
			break;
		}
		
		//System.out.println("Listen: Construct End");
		Properties prop = new Properties();
		String ports = null;
		try {
			hostname = InetAddress.getLocalHost().getHostName();
			localPort = Integer.toString(sock.getLocalPort());
			System.out.println("Running on host: "+ hostname + " port: " + localPort);
		} catch (UnknownHostException e) {
			e.printStackTrace();
			System.exit(-1);
		}
	    try {
	    	try {
	    		prop.load(new FileInputStream("config.properties"));
			} catch (FileNotFoundException e) {
				System.err.println("Cannot load file, exiting");
				e.printStackTrace();
				System.exit(-1);
			}

			ports = prop.getProperty(hostname);
			if (ports  == null) {
				prop.setProperty(hostname, localPort);
			} else if (!ports.contains(localPort)) {
				ports = ports + "," + localPort;
				prop.put(hostname, ports);
			} else {
				System.out.println("Unhandled Case, Ports already in file");
			}
			prop.store(new FileOutputStream("config.properties"), null);		
		} catch (IOException e) {
			e.printStackTrace();
			System.err.println("IOException, exiting");
			System.exit(-1);
		}
	}
    /*
     * The worker works with the sockets that represent the connections from the
     * clients. Worker thread calls the appropriate protocol to deal with the 
     * socket based on the packet. Currently supported protocols are test and
     * grep.
     */	
	private class Worker extends Thread {
		
		Socket clientSocket;
		GrepProtocol grep = null;
		TestGrepProtocol testcase1 = null;
		
		public Worker(Socket clientSocket) {
		    this.clientSocket = clientSocket;
		    testcase1 = new TestGrepProtocol(localPort);
		    grep = new GrepProtocol();
		}
		
		public void run() {
			PrintWriter out = null;
			BufferedReader in = null;
			try {
				String inputLine, cmdOut;
				out = new PrintWriter(clientSocket.getOutputStream(), true);
				in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
				if ((inputLine = in.readLine()) != null) {
					//System.out.printf("Worker: Received %s\n", inputLine);
					if (inputLine.startsWith("grep ")) {
						while ((cmdOut = grep.processInput(inputLine, "/tmp/testlog_" + localPort)) != null) {
							out.println(cmdOut);
						}
					} else if (inputLine.startsWith("test")) {
						cmdOut = testcase1.processInput(inputLine);
						while ((inputLine = in.readLine()) != null) {
							cmdOut = testcase1.processInput(inputLine);
							if (!cmdOut.equals("None__here")) {
								out.println(cmdOut);
								break;
							}
						}
					} else {
						out.println("Unexpected");
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
				// Going to return anyway
			} finally {
				if (out != null)
					out.close();
				try {
					if (in != null) 
						in.close();
					if (clientSocket != null)
						clientSocket.close();
				} catch (IOException e) {
					e.printStackTrace();
					//Going to return anyway
				}
			}
		}
	}

    /*
     * Acceptor thread listens for connections. On arrival of a connection, a worker is spawned,
     * and the socket is given to the connection.
     */
	private class Acceptor extends Thread {
		Socket clientSocket = null;
		List<Worker> workers = new ArrayList<Worker>();

		public void run() {
			while (true) {
				try {
				    clientSocket = sock.accept();
				} 
				catch (IOException e) {
				    System.out.printf("Accept failed for main socket %d", sock.getLocalPort());
				    System.exit(-1);
				}
				//System.out.println("Acceptor: Got Connection");
				Worker worker = new Worker(clientSocket);
				worker.start();
				workers.add(worker);
			}
		}
	
    }

	public void startrun() {
		acceptor = new Acceptor();
		acceptor.start();
	}
	
    /*
     * This is a shutdown thread, this waits for interrupts to acceptor thread.
     * The accpetor does not attmept to close any channels or sockets, it just 
     * updates the config file to delete the entry of this server from the 
     * config.properties file
     */
	public void shutdown(){
		acceptor.interrupt();
    	try {
			System.out.println("Updating config");
    		Properties prop = new Properties();
    		prop.load(new FileInputStream("config.properties"));
			String ports = prop.getProperty(hostname);
			if (ports  != null && ports.contains(localPort)) {
				System.out.println("Old Ports for this host - "+ports);
				System.out.println(localPort);
				ports = ports.replaceAll(localPort, "");
				ports = ports.replaceAll(",,", ",");
				if(ports.startsWith(","))
					ports = ports.substring(1);
				if(ports.endsWith(","))
					ports = ports.substring(0,ports.length()-1);
				System.out.println("New Ports for this host - "+ ports);
                if(ports.isEmpty())
                    prop.remove(hostname);
                else
				    prop.put(hostname, ports);
			}
			prop.store(new FileOutputStream("config.properties"), null);		
		} catch (FileNotFoundException e) {
			System.out.println("Config file not found.");
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		System.out.println("Server:stopped ");		
	}
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		//System.out.println("Main: Begin");
		final GrepServer serv = new GrepServer();
		System.out.println("Server: Port Acquired");		
		Runtime.getRuntime().addShutdownHook(new Thread(){
			@Override
			public void run(){
				System.out.println("Server: Trying to stop ");		
				serv.shutdown();
			}
		});
		System.out.println("Server: Shutdown hook attached");		
		serv.startrun();
		System.out.println("Server:started ");		
		//System.out.println("Main: Done");
		while(true){
			try {
				Thread.sleep(1000L);
			} catch (InterruptedException e) {
				System.out.println("Main interrupted!");
				e.printStackTrace();
			}
		}
	}
}
