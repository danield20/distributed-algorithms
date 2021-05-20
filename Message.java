import java.io.Serializable;

public class Message implements Serializable {
	String senderUId;
	int round;
	int distance;
	MessageType type;
	Direction direction;

	public Message(String senderUID, int phase, int distance, MessageType type, Direction direction) {
		this.senderUId = senderUID;
		this.round = phase;
		this.distance = distance;
		this.type = type;
		this.direction = direction;
	}

	public Message(Message message) {
		this(message.senderUId, message.round, message.distance, message.type, message.direction);
	}

	public String getSenderUId() {
		return senderUId;
	}

	public void setSenderUId(String senderUId) {
		this.senderUId = senderUId;
	}

	public int getRound() {
		return round;
	}

	public void setRound(int phase) {
		this.round = phase;
	}

	public int getDistance() {
		return distance;
	}

	public void setDistance(int distance) {
		this.distance = distance;
	}

	public MessageType getType() {
		return type;
	}

	public void setType(MessageType type) {
		this.type = type;
	}

	public Direction directionOfTravel() {
		return direction;
	}

	public void setDirection(Direction direction) {
		this.direction = direction;
	}
}
