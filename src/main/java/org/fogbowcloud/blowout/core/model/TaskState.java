package org.fogbowcloud.blowout.core.model;

public enum TaskState {

	READY("Ready"), RUNNING("Running"), FINNISHED("Finished"), COMPLETED("Completed"), FAILED("Failed"), NOT_CREATED("Not Created"), TIMEDOUT("Timedout");
	
	private String desc;
	
	private TaskState(String desc){
		this.desc = desc;
	}
	
	public String getDesc(){
		return this.desc;
	}
	
	public static TaskState getTaskStateFromDesc(String desc) throws Exception{
		for (TaskState ts : values()) {
			if(ts.getDesc().equals(desc)){
				return ts;
			}
		}
		throw new Exception("Invalid task state");
	}
	
}
