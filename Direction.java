public enum Direction {
	RIGHT, LEFT;
	Direction change() {
		return (this == LEFT) ? RIGHT : LEFT;
	}
	public String toString() {
		if(this == RIGHT)
			return "RIGHT";
		else
			return "LEFT";
	}
}
