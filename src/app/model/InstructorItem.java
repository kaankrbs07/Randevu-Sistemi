package app.model;

public class InstructorItem {
	private final int userId;
	private final int instructorId;
	private final String name;

	public InstructorItem(int userId, int instructorId, String name) {
		this.userId = userId;
		this.instructorId = instructorId;
		this.name = name;
	}

	public int getUserId() {
		return userId;
	}

	public int getInstructorId() {
		return instructorId;
	}

	public String getName() {
		return name;
	}

	@Override
	public String toString() {
		return name;
	}
}
