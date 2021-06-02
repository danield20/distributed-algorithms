import java.io.File;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Ring extends UnicastRemoteObject implements RingInterface {


	private int simulationSize;
	private Integer[] ring;
	private Node[] childStubs;
	private ArrayList<String> liveNodes = new ArrayList<String>();
	private int totalMessages = 0;
	private ArrayList<Process> myProcesses =  new ArrayList<>();

	public Ring(int simulationSize) throws RemoteException {
		this.simulationSize = simulationSize;
		ring = new Integer[simulationSize];
	}

	public static void main(String[] args) throws Exception {
		int simSize = 128;
		Ring ring_class = new Ring(simSize);
		Registry registry = LocateRegistry.createRegistry(RingInterface.REG_PORT);
		registry.rebind("ring_class", ring_class);
		System.out.println("Starting simulation");
		ring_class.startSimulation();
	}

	private void startSimulation() throws Exception {
		int nodeIndex = 0;

		for (nodeIndex = 0; nodeIndex < simulationSize; nodeIndex++) {
			ring[nodeIndex] = nodeIndex + 1;
			liveNodes.add(String.valueOf(nodeIndex + 1));
		}

		List<Integer> inList = Arrays.asList(ring);
		Collections.shuffle(inList);
		inList.toArray(ring);

		for (nodeIndex = 0; nodeIndex < simulationSize; nodeIndex++) {
			int waitMillis = (int) (Math.random() * 5000);
			String left = (nodeIndex == 0) ? String
					.valueOf(ring[simulationSize - 1]) : String
					.valueOf(ring[nodeIndex - 1]);

			String right = (nodeIndex == simulationSize - 1) ? String
					.valueOf(ring[0]) : String.valueOf(ring[nodeIndex + 1]);

			ProcessBuilder builder = new ProcessBuilder("java",
					"-Djava.security.policy=security.policy", "Node",
					String.valueOf(waitMillis),
					String.valueOf(ring[nodeIndex]), left, right);
			builder.directory(new File(System.getProperty("user.dir")));
			Process current = builder.start();
			myProcesses.add(current);
		}
	}


	@Override
	public void reportException(String nodeID, Exception e)
	throws RemoteException {
		System.out.println("Exception @ " + nodeID);
		e.printStackTrace();
	}

	@Override
	public void reportInfo(String message) throws RemoteException {
		System.out.println(message);
	}

	@Override
	public void reportNodeDeath(String nodeID) throws RemoteException {
		synchronized (Ring.class) {
			liveNodes.remove(nodeID);
			System.out.println(nodeID + " is out");
			if(liveNodes.size() == 1){
				System.out.println("THE LEADER IS: " + liveNodes.get(0));
			}
		}

	}

	@Override
	public void reportCount(int messageCount) {
		totalMessages += messageCount;
	}

	@Override
	public void ringDestroyed() {
		System.out.println(String.valueOf(totalMessages) + " exchanged amongst " + simulationSize + " nodes ");
		try {
			LocateRegistry.getRegistry(REG_PORT).unbind("ring_class");
		} catch (Exception e) {
			e.printStackTrace();
		}
		for (Process p : myProcesses) {
			p.destroy();
		}
		System.exit(0);
	}
}
