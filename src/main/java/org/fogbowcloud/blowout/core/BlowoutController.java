package org.fogbowcloud.blowout.core;

import java.util.List;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.fogbowcloud.blowout.core.exception.BlowoutException;
import org.fogbowcloud.blowout.core.model.Task;
import org.fogbowcloud.blowout.core.model.TaskState;
import org.fogbowcloud.blowout.core.monitor.TaskMonitor;
import org.fogbowcloud.blowout.core.util.AppPropertiesConstants;
import org.fogbowcloud.blowout.infrastructure.manager.InfrastructureManager;
import org.fogbowcloud.blowout.infrastructure.monitor.ResourceMonitor;
import org.fogbowcloud.blowout.infrastructure.provider.InfrastructureProvider;
import org.fogbowcloud.blowout.pool.AbstractResource;
import org.fogbowcloud.blowout.pool.BlowoutPool;

public class BlowoutController {

	public static final Logger LOGGER = Logger.getLogger(BlowoutController.class);

	private String DEFAULT_IMPLEMENTATION_BLOWOUT_POOL = "org.fogbowcloud.blowout.pool.DefaultBlowoutPool";
	private String DEFAULT_IMPLEMENTATION_SCHEDULER = "org.fogbowcloud.blowout.core.StandardScheduler";
	private String DEFAULT_IMPLEMENTATION_INFRA_MANAGER = "org.fogbowcloud.blowout.infrastructure.manager.DefaultInfrastructureManager";
	private String DEFAULT_IMPLEMENTATION_INFRA_PROVIDER = "org.fogbowcloud.blowout.infrastructure.provider.fogbow.FogbowInfrastructureProvider";

	protected BlowoutPool blowoutPool;

	// Scheduler elements
	private SchedulerInterface schedulerInterface;
	private TaskMonitor taskMonitor;

	// Infrastructure elements.
	protected InfrastructureProvider infraProvider;

	protected InfrastructureManager infraManager;
	protected ResourceMonitor resourceMonitor;

	protected boolean started = false;
	private Properties properties;

	public BlowoutController(Properties properties) throws BlowoutException {
		this.properties = properties;
		try {
			if (!BlowoutController.checkProperties(properties)) {
				throw new BlowoutException("Error on validate the file ");
			}

		} catch (Exception e) {
			throw new BlowoutException("Error while initialize Blowout Controller.", e);
		}

	}

	public void start(boolean removePreviousResouces) throws Exception {

		started = true;

		blowoutPool = createBlowoutInstance();
		infraProvider = createInfraProviderInstance(removePreviousResouces);

		taskMonitor = new TaskMonitor(blowoutPool, 30000);
		taskMonitor.start();
		resourceMonitor = new ResourceMonitor(infraProvider, blowoutPool, properties);
		resourceMonitor.start();

		schedulerInterface = createSchedulerInstance(taskMonitor);
		infraManager = createInfraManagerInstance();

		blowoutPool.start(infraManager, schedulerInterface);
	}

	public void stop() throws Exception {

		for (AbstractResource resource : blowoutPool.getAllResources()) {
			infraProvider.deleteResource(resource.getId());
		}

		taskMonitor.stop();
		resourceMonitor.stop();

		started = false;
	}

	public void addTask(Task task) throws BlowoutException {
		if (!started) {
			throw new BlowoutException("Blowout hasn't been started yet");
		}
		blowoutPool.putTask(task);
	}

	public void addTaskList(List<Task> tasks) throws BlowoutException {
		if (!started) {
			throw new BlowoutException("Blowout hasn't been started yet");
		}
		blowoutPool.addTasks(tasks);
	}

	public void cleanTask(Task task) {
		// TODO remove task from the pool.
		blowoutPool.removeTask(task);
	}

	public TaskState getTaskState(String taskId) {
		Task task = null;
		for (Task t : blowoutPool.getAllTasks()) {
			if (t.getId().equals(taskId)) {
				task = t;
			}
		}
		if (task == null) {
			return TaskState.NOT_CREATED;
		} else {
			return taskMonitor.getTaskState(task);
		}
	}

	public BlowoutPool createBlowoutInstance() throws Exception {
		String providerClassName = this.properties.getProperty(AppPropertiesConstants.IMPLEMENTATION_BLOWOUT_POOL,
				DEFAULT_IMPLEMENTATION_BLOWOUT_POOL);
		Class<?> forName = Class.forName(providerClassName);
		Object clazz = forName.getConstructor().newInstance();
		if (!(clazz instanceof BlowoutPool)) {
			throw new Exception("Blowout Pool Class Name is not a BlowoutPool implementation");
		}
		return (BlowoutPool) clazz;
	}

	public InfrastructureProvider createInfraProviderInstance(boolean removePreviousResouces) throws Exception {
		String providerClassName = this.properties.getProperty(AppPropertiesConstants.IMPLEMENTATION_INFRA_PROVIDER,
				DEFAULT_IMPLEMENTATION_INFRA_PROVIDER);
		Class<?> forName = Class.forName(providerClassName);
		Object clazz = forName.getConstructor(Properties.class, Boolean.TYPE).newInstance(properties, removePreviousResouces);
		if (!(clazz instanceof InfrastructureProvider)) {
			throw new Exception("Provider Class Name is not a InfrastructureProvider implementation");
		}
		return (InfrastructureProvider) clazz;
	}

	public InfrastructureManager createInfraManagerInstance() throws Exception {
		String providerClassName = this.properties.getProperty(AppPropertiesConstants.IMPLEMENTATION_INFRA_MANAGER,
				DEFAULT_IMPLEMENTATION_INFRA_MANAGER);
		Class<?> forName = Class.forName(providerClassName);
		Object clazz = forName.getConstructor(InfrastructureProvider.class, ResourceMonitor.class).newInstance(infraProvider, resourceMonitor);
		if (!(clazz instanceof InfrastructureManager)) {
			throw new Exception("Infrastructure Manager Class Name is not a InfrastructureManager implementation");
		}
		return (InfrastructureManager) clazz;
	}

	protected SchedulerInterface createSchedulerInstance(TaskMonitor taskMonitor) throws Exception {
		String providerClassName = this.properties.getProperty(AppPropertiesConstants.IMPLEMENTATION_SCHEDULER,
				DEFAULT_IMPLEMENTATION_SCHEDULER);
		Class<?> forName = Class.forName(providerClassName);
		Object clazz = forName.getConstructor(TaskMonitor.class).newInstance(taskMonitor);
		if (!(clazz instanceof SchedulerInterface)) {
			throw new Exception("Scheduler Class Name is not a SchedulerInterface implementation");
		}
		return (SchedulerInterface) clazz;
	}

	protected static boolean checkProperties(Properties properties) {
		if (!properties.containsKey(AppPropertiesConstants.IMPLEMENTATION_INFRA_PROVIDER)) {
			LOGGER.error("Required property " + AppPropertiesConstants.IMPLEMENTATION_INFRA_PROVIDER + " was not set");
			return false;
		}
		if (!properties.containsKey(AppPropertiesConstants.INFRA_RESOURCE_IDLE_LIFETIME)) {
			LOGGER.error("Required property " + AppPropertiesConstants.INFRA_RESOURCE_IDLE_LIFETIME + " was not set");
			return false;
		}
		if (!properties.containsKey(AppPropertiesConstants.INFRA_RESOURCE_CONNECTION_TIMEOUT)) {
			LOGGER.error(
					"Required property " + AppPropertiesConstants.INFRA_RESOURCE_CONNECTION_TIMEOUT + " was not set");
			return false;
		}
		if (!properties.containsKey(AppPropertiesConstants.INFRA_IS_STATIC)) {
			LOGGER.error("Required property " + AppPropertiesConstants.INFRA_IS_STATIC + " was not set");
			return false;
		}
		if (!properties.containsKey(AppPropertiesConstants.INFRA_AUTH_TOKEN_UPDATE_PLUGIN)) {
			LOGGER.error(
					"Required property " + AppPropertiesConstants.INFRA_AUTH_TOKEN_UPDATE_PLUGIN + " was not set");
			return false;
		}
		LOGGER.debug("All properties are set");
		return true;
	}
	
	public BlowoutPool getBlowoutPool() {
		return this.blowoutPool;
	}

	public void setBlowoutPool(BlowoutPool blowoutPool) {
		this.blowoutPool = blowoutPool;
	}
	
	public TaskMonitor getTaskMonitor() {
		return taskMonitor;
	}

	public void setTaskMonitor(TaskMonitor taskMonitor) {
		this.taskMonitor = taskMonitor;
	}

	public SchedulerInterface getSchedulerInterface() {
		return schedulerInterface;
	}

	public void setSchedulerInterface(SchedulerInterface schedulerInterface) {
		this.schedulerInterface = schedulerInterface;
	}

	public InfrastructureProvider getInfraProvider() {
		return infraProvider;
	}

	public void setInfraProvider(InfrastructureProvider infraProvider) {
		this.infraProvider = infraProvider;
	}

	public InfrastructureManager getInfraManager() {
		return infraManager;
	}

	public void setInfraManager(InfrastructureManager infraManager) {
		this.infraManager = infraManager;
	}

	public ResourceMonitor getResourceMonitor() {
		return resourceMonitor;
	}

	public void setResourceMonitor(ResourceMonitor resourceMonitor) {
		this.resourceMonitor = resourceMonitor;
	}

	public boolean isStarted() {
		return started;
	}

	public void setStarted(boolean started) {
		this.started = started;
	}
}
