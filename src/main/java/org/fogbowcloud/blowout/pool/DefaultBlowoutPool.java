package org.fogbowcloud.blowout.pool;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.Logger;
import org.fogbowcloud.blowout.core.SchedulerInterface;
import org.fogbowcloud.blowout.core.model.Task;
import org.fogbowcloud.blowout.infrastructure.manager.InfrastructureManager;
import org.fogbowcloud.blowout.infrastructure.model.AbstractResource;
import org.fogbowcloud.blowout.infrastructure.model.ResourceState;

public class DefaultBlowoutPool implements BlowoutPool {

	public static final Logger LOGGER = Logger.getLogger(DefaultBlowoutPool.class);

	private Map<String, AbstractResource> resourcePool = new ConcurrentHashMap<>();
	private List<Task> taskPool = new ArrayList<>();
	private InfrastructureManager infraManager;
	private SchedulerInterface schedulerInterface;

	@Override
	public void start(InfrastructureManager infraManager, SchedulerInterface schedulerInterface) {
		this.infraManager = infraManager;
		this.schedulerInterface = schedulerInterface;
	}

	@Override
	public void addResource(AbstractResource resource) {
		resource.setState(ResourceState.IDLE);
		this.resourcePool.put(resource.getId(), resource);
		this.callAct();
	}

	@Override
	public void addResourceList(List<AbstractResource> resources) {
		for (AbstractResource resource : resources) {
			resource.setState(ResourceState.IDLE);
			this.resourcePool.put(resource.getId(), resource);
		}
		this.callAct();
	}

	@Override
	public void updateResource(AbstractResource resource, ResourceState state) {
		AbstractResource currentResource = this.resourcePool.get(resource.getId());
		if (currentResource != null) {
			currentResource.setState(state);
			this.resourcePool.put(resource.getId(), currentResource);
			this.callAct();
		}
	}

	protected synchronized void callAct() {
		try {
			this.infraManager.act(this.getAllResources(), this.getAllTasks());
			this.schedulerInterface.act(this.getAllTasks(), this.getAllResources());
		} catch (Exception e) {
			LOGGER.error("Error while calling act", e);
		}
	}

	@Override
	public List<AbstractResource> getAllResources() {
		return new ArrayList<>(this.resourcePool.values());
	}

	@Override
	public AbstractResource getResourceById(String resourceId) {
		return this.resourcePool.get(resourceId);
	}

	@Override
	public synchronized void removeResource(AbstractResource resource) {
		this.resourcePool.remove(resource.getId());
	}

	@Override
	public void putTask(Task task) {
		this.taskPool.add(task);
		this.callAct();
	}

	@Override
	public void addTasks(List<Task> tasks) {
		this.taskPool.addAll(tasks);
		this.callAct();
	}

	@Override
	public List<Task> getAllTasks() {
		return new ArrayList<>(this.taskPool);
	}

	@Override
	public Task getTaskById(String taskId) {
		for (int i = 0; i <= this.taskPool.size(); i++) {
			Task task = this.taskPool.get(i);
			if (task.getId().equals(taskId)) {
				return this.taskPool.get(i);
			}
		}
		return null;
	}

	@Override
	public void removeTask(Task task) {
		this.taskPool.remove(task);
		this.callAct();
	}

	protected InfrastructureManager getInfraManager() {
		return this.infraManager;
	}

	protected void setInfraManager(InfrastructureManager infraManager) {
		this.infraManager = infraManager;
	}

	protected SchedulerInterface getSchedulerInterface() {
		return this.schedulerInterface;
	}

	protected void setSchedulerInterface(SchedulerInterface schedulerInterface) {
		this.schedulerInterface = schedulerInterface;
	}

	protected Map<String, AbstractResource> getResourcePool() {
		return this.resourcePool;
	}

	protected void setResourcePool(Map<String, AbstractResource> resourcePool) {
		this.resourcePool = resourcePool;
	}

	protected List<Task> getTaskPool() {
		return this.taskPool;
	}

	protected void setTaskPool(List<Task> taskPool) {
		this.taskPool = taskPool;
	}
}
