

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/* This class spawns a unix process with the executable as the grep command with
 * command line options. This class is implemented as a protocol, where the inputs
 * expected to the processInput() are the cli options within grep. The CLI option
 * is slightly modified, in terms of key/value. The key and value may be specified
 * in regex independently.
 * The regex for key/value for example should be in a single line of the following
 * form: <key-regex> <value-regex>
 * example: ^debug ^this.
 * This can be invoked as object.processInput("^debug ^this", <absolute file path>);
 * 
 * More detail on calling convention, object.processInput is to be called with the same
 * command, until a null is returned. One can expect multiple lines of results until
 * then.
 */
public class GrepProtocol {
    private static final int WAITING = 0;
    private static final int PROCESS = 1;
    private static final int INVALID = 2;
 
    private int state = WAITING;
    Process p = null;
    BufferedReader pin = null;
    String cmdOut = null;
    
    private void Cleanup() {
    	if (p != null) {
			try {
				p.waitFor();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
    	}
		//System.out.println("GrepCore: Exit Status " + p.exitValue());       	
    }
    
    public String processInput(String inputLine, String filePath) {
 
        if (state == WAITING) {
            String args[] = inputLine.substring(5).split(":");
            String command = null;
            if (!args[0].endsWith("$"))
                command = args[0] + ".*";
            else
                command = args[0].substring(0, (args[0].length() - 1));
            command += ":";
            if (args.length >= 2) {
                if (args[1].startsWith("^")) {
                    command += args[1].substring(1);
                } else {
                    command += ".*" + args[1];
                }
            }
            String command1[] = {"grep", command, filePath};
            try {
                p = Runtime.getRuntime().exec(command1);
            } catch (IOException e1) {
                e1.printStackTrace();
                Cleanup();
                state = INVALID;
                return null;
            }

			//System.out.println("GrepCore: Command " + command);
			pin = new BufferedReader(new InputStreamReader(p.getInputStream()));
			String cmdOut = null;
			try {
				if ((cmdOut = pin.readLine()) == null) {
					Cleanup();
					state = INVALID;
					return null;
				} else {
					state = PROCESS;
					return cmdOut;
				}
			} catch (IOException e) {
				e.printStackTrace();
				state = INVALID;
				Cleanup();
				return null;
			}
        } else if (state == PROCESS) {
        	if (pin == null) {
        		return null;
        	}
			try {
				if ((cmdOut = pin.readLine()) == null) {
					Cleanup();
					state = INVALID;
					return null;
				} else {
					return cmdOut;
				}
			} catch (IOException e) {
				e.printStackTrace();
				Cleanup();
				state = INVALID;
				return null;
			}
        } else {
			return null;
		}
    }

}
