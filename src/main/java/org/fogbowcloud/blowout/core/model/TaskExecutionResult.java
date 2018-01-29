package org.fogbowcloud.blowout.core.model;

public class TaskExecutionResult {

	public static final int OK = 0;
	public static final int SU_COMMAND_BAD_ARGUMENTS = 17;
	public static final int NOK = 128;
	public static final int TIMEOUT = 124;

	private boolean taskFinished;
	private int exitValue;

	public TaskExecutionResult() {
		this.taskFinished = false;
		this.exitValue = -1;
	}

	public int getExitValue() {
		return this.exitValue;
	}

	public boolean isExecutionFinished() {
		return this.taskFinished;
	}

	public void finish(int exitValue) {
		this.taskFinished = true;
		this.exitValue = exitValue;
	}

	public String toString() {
		return "taskFinished=" + this.taskFinished + ", exitValue=" + this.exitValue;
	}
}
