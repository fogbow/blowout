package org.fogbowcloud.blowout.core.model;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import org.fogbowcloud.blowout.core.model.Command.Type;
import org.json.JSONObject;

public interface Task extends Serializable{

	Specification getSpecification();

	Task clone();

	String getId();

	void finish();
	
	void fail();

	boolean isFinished();
	
	boolean isFailed();
	
	boolean checkTimeOuted();

	void addCommand(Command command);
	
	List<Command> getCommandsByType(Type commandType);
	
	List<Command> getAllCommands();
	
	void startedRunning();

	void putMetadata(String attributeName, String value);

	String getMetadata(String attributeName);
	
	Map<String, String> getAllMetadata();
	
	boolean mayRetry();

	int getRetries();

	void setRetries(int retries);
	
	int getNumberOfCommands();

	void addProcessId(String procId);
	
	List<String> getProcessId();
	
	JSONObject toJSON();
	
	TaskState getState();

	void setState(TaskState state);

	String getUUID();
}