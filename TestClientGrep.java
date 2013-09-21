

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

/*
 * This is main class to do tests in the client. Its implemented as a protocol to make it
 * easy to wirk with sockets.
 * This class expects to be talking to a socket, to a remote server, from processInput method.
 * This class also expects to be talking to the TestGrepProtocol in the remote server.
 * The class talks to the TestGrepProtocol locally and compares the results.
 * The difference in the strings is stored in diff member of the class, this can be accessed by
 * the caller to know the result.
 */
public class TestClientGrep {
    private static final int WAITING = 0;
    private static final int SEND_FILE = 1;
    private static final int SEND_GREP = 2;
    private static final int WAIT_PROCESS = 3;
    private static final int CHECK_RESULT = 4;
	private static final int INVALID = 5;
 
    private int state = WAITING;
	File file = null;
	BufferedReader read = null;
	String cmdOut = null, localResult = null;
	String[] result = null;
	TestGrepProtocol tgrep = null;
	int index = 0, diff = 0;
	String grepString = null;

	TestClientGrep(String testQuery) {
		file = new File("testlogfile");
	    try {
			read = new BufferedReader(new FileReader(file));
			grepString = testQuery.substring(7).trim();
			if (grepString.length() < 2) {
				System.err.println("Zero length query, cancel test");
				state = INVALID;
		    }
			System.out.println(grepString);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}
	
	public String processInput(String InputLine) {
	
		if (state == WAITING) {
			tgrep = new TestGrepProtocol("client");
			cmdOut = "test";
			state = SEND_FILE;
			tgrep.processInput(cmdOut);
			return cmdOut;
		} else if (state == SEND_FILE) {
		    try {
				if ((cmdOut = read.readLine()) != null) {
					tgrep.processInput(cmdOut);
					return cmdOut;
				} else {
					state = SEND_GREP;
					tgrep.processInput("DONE_FILE_TRANSFER");
					return "DONE_FILE_TRANSFER";
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else if (state == SEND_GREP) {
			state = WAIT_PROCESS;
			System.out.println("Sending:" + grepString);
			result = tgrep.processInput(grepString).split("\n");
			return grepString;
		} else if (state == WAIT_PROCESS) {
			state = CHECK_RESULT;
			return null;
		} else {
			if (InputLine != null) {
				if (index < result.length) {
					localResult = result[index++];
					diff += InputLine.compareTo(localResult);
				}
				return null;
			} else return null;
		}
		return null;
	}
}
