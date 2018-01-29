package org.fogbowcloud.blowout.pool;

import java.util.List;

import org.fogbowcloud.blowout.core.SchedulerInterface;
import org.fogbowcloud.blowout.core.model.Task;
import org.fogbowcloud.blowout.infrastructure.manager.InfrastructureManager;
import org.fogbowcloud.blowout.infrastructure.model.AbstractResource;
import org.fogbowcloud.blowout.infrastructure.model.ResourceState;

public interface BlowoutPool{
	
	void start(InfrastructureManager infraManager, SchedulerInterface schedulerInterface);
	
	void addResource(AbstractResource resource);
	
	void addResourceList(List<AbstractResource> resources);
	
	void updateResource(AbstractResource resource, ResourceState state);
	
	List<AbstractResource> getAllResources();
	
	AbstractResource getResourceById(String resourceId);
	
	void removeResource(AbstractResource resource);
	
	void putTask(Task task);
	
	void addTasks(List<Task> tasks);
	
	List<Task> getAllTasks();
	
	Task getTaskById(String taskId);
	
	void removeTask(Task task);


}