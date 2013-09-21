

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;

/*
 * This class works to test grep a file sent over by the client. The file is saved in the /tmp/
 * folder. The class also expects a grep string of the form used by GrepServer. We then execute 
 * grep on the file and return it to the caller.
 *
 * This is implemtented as a protocol to make it easier to work with for sockets.
 * The usage for this class can be illustrated with these test steps. processInput can be fed from 
 * a socket.
 	    {
	    	String testthis = null;
	    	
	    	testthis = testcase1.processInput("test");
	    	System.out.println(testthis);
	    	testthis = testcase1.processInput("Fren: There where are");
	    	System.out.println(testthis);
	    	testthis = testcase1.processInput("Fren: There you where");
	    	System.out.println(testthis);
	    	testthis = testcase1.processInput("DONE_FILE_TRANSFER");
	    	System.out.println(testthis);
	    	testthis = testcase1.processInput("grep Fren where$");
	    	System.out.println(testthis);
	    	
	    }
 * testcase1 is a object of class. It expects test..<FILE LINES>..DONE_FILE_TRANSFER..grep <key> <value>
 * It then returns the output of the grep on the FILE LINES.
 */
public class TestGrepProtocol {
    private static final int WAITING = 0;
    private static final int PROCESS = 1;
    private static final int GREP = 2;
    private static final int DONE = 3;
 
    private int state = WAITING;
    String cmdOut = "", filePath = null, temp = null, suffix = null;
    PrintWriter writer = null;
    GrepProtocol grep = null;

    TestGrepProtocol(String suffix) {
    	this.suffix = suffix;
    }
    public String processInput(String inputLine) {

    	if (inputLine == null) {
    		return null;
    	}

        if (state == WAITING) {
        	try {
				writer = new PrintWriter("/tmp/testlogfile45_" + suffix, "UTF-8");
			} catch (FileNotFoundException e2) {
				System.out.println("File not found, creating");
			} catch (UnsupportedEncodingException e2) {
				e2.printStackTrace();
			}
        	if (writer == null) {
				System.out.println("Could not create file, Exiting");
        		return null;
        	}
        	grep = new GrepProtocol();
        	state = PROCESS;
        	return "None__here";
        } else if (state == PROCESS) {
        	if (inputLine.equals("DONE_FILE_TRANSFER")) {
	        	state = GREP;
	        	writer.close();
	        } else {
            	writer.println(inputLine);
	        }
        	return "None__here";
        } else if (state == GREP) {
        	System.out.println("Testgrep: Core Grep:" + inputLine);
			while ((temp = grep.processInput(inputLine, "/tmp/testlogfile45_" + suffix)) != null) {
				cmdOut +=  temp + "\n";
			}
				state = DONE;
			    return cmdOut;
        }
        return null;
    }
}
