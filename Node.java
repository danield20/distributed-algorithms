import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

public class Node extends UnicastRemoteObject implements ElectionNode {
	private String nodeID;
	private String left;
	private String right;
	private NodeState nodeState;
	private RingInterface ring_class;
	private ElectionNode leftStub;
	private ElectionNode rightStub;
	private int round;
	private SynchronousQueue<Message> messageQueue = new SynchronousQueue<Message>();
	private int inCount = 0;
	CountDownLatch latch = new CountDownLatch(1);
	private int messageCount = 0;
	int leaderID = -1;
	int electionRounds = 0;

	private class NeighborLookupTask implements Callable<ElectionNode> {

		private String stubID;

		NeighborLookupTask(String stubID) {
			this.stubID = stubID;
		}

		@Override
		public ElectionNode call() throws Exception {
			ElectionNode stub = null;
			boolean lookupComplete = false;
			Registry registry = null;
			registry = LocateRegistry.getRegistry(RingInterface.REG_PORT);
			while (!lookupComplete) {
				try {
					Thread.sleep(200);
					stub = (ElectionNode) registry.lookup(stubID);
					lookupComplete = true;
				} catch (Exception e) {
				}
			}
			return stub;
		}
	}

	private class MessageGrinder implements Runnable {

		@Override
		public void run() {
			while (true) {
				try {
					processMessage(messageQueue.take());
				} catch (Exception e) {
					log(e);
				}
			}
		}

		private void processMessage(Message message) throws Exception {

			messageCount++;
			int myUID = Integer.parseInt(nodeID);
			int incomingUID = Integer.parseInt(message.senderUId);

			if (message.type == MessageType.NODE_DIED) {

				if (myUID != incomingUID) {
					invoke(rightStub, message);
				}

				if (incomingUID == leaderID) {
					log("Leader is dead - starting new election");
					electionRounds++;
					messageQueue.clear();;
					leaderID = -1;

					invoke(leftStub, new Message(nodeID, 1, 1, MessageType.SEND,
					Direction.RIGHT));

					invoke(rightStub, new Message(nodeID, 1, 1, MessageType.SEND,
							Direction.LEFT));
				}

				return;
			}

			if (message.type == MessageType.ELECTED) {
				ring_class.reportCount(++messageCount);
				leaderID = incomingUID;
				log("received election message, node " + leaderID + " is the leader");
				if (nodeState != NodeState.LEADER)
					invoke(rightStub, message);
				round = 0;
				nodeState = NodeState.READY;
				if (electionRounds == 1)
					LocateRegistry.getRegistry(RingInterface.REG_PORT).unbind(nodeID);
				if (myUID == leaderID) {
					if (electionRounds != 1) {
						log("Starting case when leader dies, leader dying now");
						electionRounds++;
						leftStub.setRight(rightStub);
						rightStub.setLeft(leftStub);
						nodeState = NodeState.LIMBO;

						relayDead();
					} else {
						ring_class.ringDestroyed();
					}
				}
				// if (electionRounds == 1)
				// 	System.exit(0);
				return;
			}

			// Eat up messages meant for inferior nodes if you have moved on to
			// the next round.
			if (round > message.round && myUID > incomingUID)
				return;

			switch (message.direction) {
			case LEFT:
				doAction(message, myUID, incomingUID, leftStub, rightStub);
				break;
			case RIGHT:
				doAction(message, myUID, incomingUID, rightStub, leftStub);
				break;
			}

			if (inCount == 2 && nodeState == NodeState.READY) {
				inCount = 0;
				round += 1;
				invoke(leftStub,
						new Message(nodeID, round, (int) Math.pow(2, round),
								MessageType.SEND, Direction.RIGHT));
				invoke(rightStub,
						new Message(nodeID, round, (int) Math.pow(2, round),
								MessageType.SEND, Direction.LEFT));
			}
		}

		private void doAction(Message message, int myUID, int incomingUID,
				ElectionNode leftStub, ElectionNode rightStub)
				throws RemoteException {
			switch (message.type) {
				case SEND:
					if (incomingUID > myUID) {
						if (nodeState != NodeState.LIMBO) {
							nodeState = NodeState.LIMBO;
							log("is out");
						}
						if (message.distance > 1) {
							invoke(rightStub, createForward(message));
						} else {
							invoke(leftStub, createReply(message));
						}
					} else if (incomingUID == myUID
							&& nodeState != NodeState.LEADER) {
						nodeState = NodeState.LEADER;
						log("I AM THE LEADER");
						relayElected();
					}
					break;
				case REPLY:
					if (incomingUID > myUID) {
						invoke(rightStub, message);
					} else {
						inCount++;
					}
					break;
			}
		}

		private void relayElected() {
			Message message = new Message(nodeID, 1, 1, MessageType.ELECTED,
					Direction.LEFT);
			invoke(rightStub, message);
		}

		private void relayDead() {
			Message message = new Message(nodeID, 1, 1, MessageType.NODE_DIED,
					Direction.LEFT);
			invoke(rightStub, message);
		}
	}

	public Node(String nodeID, String left, String right, NodeState startState)
			throws RemoteException {
		nodeState = NodeState.INITIALIZING;
		this.nodeID = nodeID;
		this.left = left;
		this.right = right;
		round = 1;
		init();
	}

	private void init() {
		try {
			Registry registry = LocateRegistry.getRegistry(RingInterface.REG_PORT);
			ring_class = (RingInterface) registry.lookup("ring_class");
			new Thread(new MessageGrinder()).start();
			registry.rebind(nodeID, this);

			ExecutorService executorService = Executors.newCachedThreadPool();

			Future<ElectionNode> leftResult = executorService
					.submit(new NeighborLookupTask(left));

			Future<ElectionNode> rightResult = executorService
					.submit(new NeighborLookupTask(right));

			leftStub = leftResult.get();
			rightStub = rightResult.get();
			nodeState = NodeState.READY;

			latch.countDown();

			invoke(leftStub, new Message(nodeID, 1, 1, MessageType.SEND,
					Direction.RIGHT));

			invoke(rightStub, new Message(nodeID, 1, 1, MessageType.SEND,
					Direction.LEFT));

		} catch (Exception e) {
			log(e);
		}
	}

	public static void main(String[] args) throws Exception {
		int state = Integer.parseInt(args[0]);
		new Node(args[1], args[2], args[3], NodeState.fromInt(state));
	}

	private Message createForward(Message message) {
		Message msg = new Message(message);
		msg.setDistance(message.distance - 1);
		return msg;
	}

	private Message createReply(Message message) {
		Message reply = new Message(message);
		reply.setDistance(1);
		reply.setType(MessageType.REPLY);
		reply.setDirection(message.direction.change());
		return reply;
	}

	public void setLeft(ElectionNode newLeft) throws RemoteException, InterruptedException {
		leftStub = newLeft;
	}

	public void setRight(ElectionNode newRight) throws RemoteException, InterruptedException {
		rightStub = newRight;
	}

	@Override
	public void passMessage(Message message) throws RemoteException,
			InterruptedException {
		latch.await();
		messageQueue.put(message);
	}

	private void log(String message) {
		try {
			ring_class.reportInfo(nodeID + " : " + message);
		} catch (RemoteException e) {
		}
	}

	private void log(Exception e) {
		try {
			ring_class.reportException(nodeID, e);
		} catch (RemoteException e1) {
		}
	}

	private void invoke(final ElectionNode stub, final Message message) {
		new Thread(new Runnable() {
			public void run() {
				try {
					TimeUnit.MILLISECONDS.sleep((long) (Math.random()%300));
					stub.passMessage(message);
				} catch (Exception e) {
					log(e);
				}
			}
		}).start();
	}
}