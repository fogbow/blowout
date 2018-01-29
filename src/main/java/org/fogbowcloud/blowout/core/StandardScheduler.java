package org.fogbowcloud.blowout.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.fogbowcloud.blowout.core.model.Specification;
import org.fogbowcloud.blowout.core.model.Task;
import org.fogbowcloud.blowout.core.model.TaskProcess;
import org.fogbowcloud.blowout.core.model.TaskProcessImpl;
import org.fogbowcloud.blowout.core.monitor.TaskMonitor;
import org.fogbowcloud.blowout.infrastructure.model.ResourceState;
import org.fogbowcloud.blowout.infrastructure.model.AbstractResource;

public class StandardScheduler implements SchedulerInterface {

	private Map<AbstractResource, Task> runningTasks = new HashMap<AbstractResource, Task>();
	private TaskMonitor taskMonitor;

	public StandardScheduler(TaskMonitor taskMon) {
		this.taskMonitor = taskMon;
	}

	@Override
	public void act(List<Task> tasks, List<AbstractResource> resources) {
		for (AbstractResource resource : resources) {
			this.actOnResource(resource, tasks);
		}
		for (Task runningTask : this.runningTasks.values()) {
			if (!tasks.contains(runningTask)) {
				this.stopTask(runningTask);
			}
		}
		for (AbstractResource resourceInUse : this.runningTasks.keySet()) {
			if (!resources.contains(resourceInUse)) {
				this.stopTask(this.getTaskRunningInResouce(resourceInUse));
			}
		}
	}

	protected void actOnResource(AbstractResource resource, List<Task> tasks) {
		ResourceState resourceState = resource.getState();
		if (resourceState.equals(ResourceState.IDLE)) {
			Task task = this.chooseTaskForRunning(resource, tasks);
			if (task != null) {
				this.runTask(task, resource);
			}
		}

		if (resourceState.equals(ResourceState.TO_REMOVE)) {
			this.removeRunningResource(resource);
		}
	}

	protected Task chooseTaskForRunning(AbstractResource resource, List<Task> tasks) {
		for (Task task : tasks) {
			Specification resourceSpecification = resource.getRequestedSpec();
			if (!task.isFinished() && !this.taskIsRunning(task)
					&& resourceSpecification.equals(task.getSpecification())) {
				return task;
			}
		}
		return null;
	}

	@Override
	public void stopTask(Task task) {
		// TODO: Find out how to stop the execution of the process
		for (AbstractResource resource : this.runningTasks.keySet()) {
			Task runningTask = this.runningTasks.get(resource);
			if (runningTask.equals(task)) {
				this.taskMonitor.stopTask(task);
				this.removeRunningResource(resource);
			}
		}
	}

	@Override
	public void runTask(Task task, AbstractResource resource) {
		task.setRetries(task.getRetries()+1);
		this.runningTasks.put(resource, task);
		this.submitToMonitor(task, resource);
	}

	public void submitToMonitor(Task task, AbstractResource resource) {
		this.taskMonitor.runTask(task, resource);
	}

	protected TaskProcess createProcess(Task task) {
		TaskProcess taskProcess = new TaskProcessImpl(task.getId(), task.getAllCommands(),
				task.getSpecification(), task.getUUID());
		return taskProcess;
	}

	@Override
	public List<Task> getRunningTasks() {
		return new ArrayList<Task>(this.runningTasks.values());
	}

	protected void setRunningTasks(Map<AbstractResource, Task> runningTasks) {
		this.runningTasks = runningTasks;
	}

	private boolean taskIsRunning(Task task) {
		return this.runningTasks.containsValue(task);
	}

	private Task getTaskRunningInResouce(AbstractResource resource) {
		return this.runningTasks.get(resource);
	}

	private void removeRunningResource(AbstractResource resource) {
		this.runningTasks.remove(resource);
	}
}
