

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Properties;

/* This is the main client class. Connects to servers and sends queries to servers.
 * The queries are based on the commandline string sent to the client. There might
 * be multiple servers. The server information is readin from config.properties.
 * Client thus has requester objects which run threads foreach server.
 */

 /*
  * Testmode is a special mode for the client. The client needs to maintain test results
  * for each of the requester. The test results are compiled from each of the requestor
  * and printed out.
  */
public class GrepClient {

    ArrayList<Requester> req_list;
	String query = "";
    boolean testmode = false;
    int diff = 0;

	GrepClient() {
		Properties prop = new Properties();
		String[] ports = null;
	    try {
	    	try {
	    		prop.load(new FileInputStream("config.properties"));
			} catch (FileNotFoundException e) {
				System.out.println("No config file");
				e.printStackTrace();
				System.exit(0);
			}
		    req_list = new ArrayList<Requester>();
	    	for (Object hostname: prop.keySet()) {
	    		ports = prop.getProperty((String)hostname).split(",");
	    	    for (String portOne: ports) {
	    	    	req_list.add(new Requester((String)hostname, portOne));
	    	    }
	    	}
			if (req_list.size() == 0) {
				System.out.println("No Machines, Exit");
		    }
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	/* 
     * The requester thread gets the host/port information. 
     * thread tires to connect to the remote server. Based on the
     * command line, the protocol used to talk to the server is 
     * different.
     * The supported protocols are grep and test.
     */
	private class Requester extends Thread {
		Socket sock = null;
        String hostName = null, portOne = null;
        TestClientGrep tgrep = null;
        int diff = 0;

        public Requester(String _hostName, String _portOne) {
            hostName = _hostName;
            portOne = _portOne;
			//System.out.printf("Client: Construct: Reg Servers: %s %s\n",
			                  //hostName, portOne);
	        try {
	            sock = new Socket(hostName, Integer.parseInt(portOne));
	        } catch (UnknownHostException e) {
	            System.err.println("Don't know about host: "+ hostName + " " + portOne);
	            return;
	        } catch (IOException e) {
	            System.err.println("No socket for " + hostName + " " + portOne);
	            return;
	        }
			//System.out.println("Client: Construct Done");
        }

		@Override
		public void run() {
			//System.out.println("Client: Thread Start Send");
			PrintWriter out = null;
	        BufferedReader in = null;
	        String servLine = null;
	        if (sock == null) {
	        	return;
	        }
	        try {
	            out = new PrintWriter(sock.getOutputStream(), true);
	            in = new BufferedReader(new InputStreamReader(
	                                        sock.getInputStream()));
	        } catch (IOException e) {
	            System.err.println("No Buffered I/O for " + hostName + " " + portOne);
	            return;
	        }
	        
	        if (query.startsWith(" __test")) {
	        	String outReq = null;
	        	tgrep = new TestClientGrep(query);
	        	while ((outReq = tgrep.processInput(null)) != null) {
	        		out.println(outReq);
	        	}
	        	testmode = true;
	        } else {
	        	out.println("grep" + query);
	        }
	        
	        try {
	        	while ((servLine = in.readLine()) != null) {
	        		if (!testmode) {
	        			System.out.println("(" + hostName + "," + portOne + "): " + servLine);
	        		} else {
	        			tgrep.processInput(servLine);
	        		}
	        	}
	        	if (testmode) {
	        		System.out.println("TestMode: Diff is " + tgrep.diff);
	        	}
	        } catch (IOException e) {
	            System.err.println("Couldnot read from Buffered I/O " + hostName + " " + portOne);
	            return;
	        }
			//System.out.println("Client: Thread Finish Send");
		}

	}
	
	public void startrun(String[] arg) {
        if (arg.length == 0) {
			System.out.println("Client: Empty query, return");
            return;
        }
        for (String cli : arg)
            query += " " + cli;
        //System.out.println(query);
		for (Requester req: req_list) {
			req.start();
		}
		joinThreads();
 	}

	public void joinThreads() {
		for (Requester req: req_list) {
			try {
				req.join();
				if (testmode) {
					diff += req.diff;
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		if (testmode) {
			if (diff == 0) {
				System.out.println("Test mode, test passed");
			} else {
				System.out.println("Test mode, test failed");
			}
		}
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		GrepClient client = new GrepClient();
		client.startrun(args);
	}

}
