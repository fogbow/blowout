package org.fogbowcloud.blowout.core.model;

import java.util.List;

import org.fogbowcloud.blowout.infrastructure.model.AbstractResource;

public interface TaskProcess {

	String getProcessId();
	
	String getTaskId();

	List<Command> getCommands();

	TaskExecutionResult executeTask(AbstractResource resource);

	TaskState getStatus();
	
	Specification getSpecification();
	
	AbstractResource getResource();
	
	void setStatus(TaskState taskState);
}