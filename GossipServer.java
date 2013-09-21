
public class GossipServer {
	public static void main(String[] args) {
		System.out.println("Main: Begin");
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

		GossipUdpServer gen = new GossipUdpServer();
		System.out.println("Server:started ");		
		System.out.println("Main: Done");
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
