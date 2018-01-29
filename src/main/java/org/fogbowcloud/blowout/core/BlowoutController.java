package org.fogbowcloud.blowout.core;

import org.apache.log4j.Logger;
import org.fogbowcloud.blowout.core.exception.BlowoutException;
import org.fogbowcloud.blowout.core.model.Task;
import org.fogbowcloud.blowout.core.model.TaskState;
import org.fogbowcloud.blowout.core.monitor.TaskMonitor;
import org.fogbowcloud.blowout.core.util.AppPropertiesConstants;
import org.fogbowcloud.blowout.core.util.BlowoutDefaultConstants;
import org.fogbowcloud.blowout.infrastructure.manager.InfrastructureManager;
import org.fogbowcloud.blowout.infrastructure.monitor.ResourceMonitor;
import org.fogbowcloud.blowout.infrastructure.provider.InfrastructureProvider;
import org.fogbowcloud.blowout.pool.BlowoutPool;

import java.util.List;
import java.util.Properties;

public class BlowoutController {

    public static final Logger LOGGER = Logger.getLogger(BlowoutController.class);

    private BlowoutPool blowoutPool;

    private SchedulerInterface schedulerInterface;
    private TaskMonitor taskMonitor;

    private InfrastructureProvider infraProvider;
    private InfrastructureManager infraManager;
    private ResourceMonitor resourceMonitor;

    private Properties properties;
    protected boolean started;

    public BlowoutController(Properties properties) throws BlowoutException {
        this.started = false;

        try {
            if (!BlowoutController.checkProperties(properties)) {
                throw new BlowoutException("Error on validate the file ");
            }
        } catch (Exception e) {
            throw new BlowoutException("Error while initialize Blowout Controller.", e);
        }

        this.properties = properties;
    }

    public void start(boolean removePreviousResources) throws Exception {
        long taskMonitorPeriod = Long.parseLong(
                this.properties.getProperty(
                        AppPropertiesConstants.TASK_MONITOR_PERIOD,
                        BlowoutDefaultConstants.TASK_MONITOR_PERIOD
                )
        );

        this.blowoutPool = this.createBlowoutInstance();
        this.infraProvider = this.createInfraProviderInstance(removePreviousResources);

        // Requires blowout pool to be created
        this.taskMonitor = createTaskMonitor(blowoutPool, taskMonitorPeriod);

        // Requires task monitor to be created
        this.schedulerInterface = this.createSchedulerInstance(taskMonitor);

        // Requires infrastructure provider, blowout pool, properties
        this.resourceMonitor = createResourceMonitor(infraProvider, blowoutPool, properties);

        // Requires infrastructure provider, resource monitor
        this.infraManager = this.createInfraManagerInstance(
                this.infraProvider,
                this.resourceMonitor
        );

        this.blowoutPool.start(this.infraManager, this.schedulerInterface);

        this.started = true;
    }

    public void stop() {
        this.taskMonitor.stop();
        this.resourceMonitor.stop();

        this.started = false;
    }

    public void addTask(Task task) throws BlowoutException {
        if (!this.started) {
            throw new BlowoutException("Blowout hasn't been started yet");
        }
        this.blowoutPool.putTask(task);
    }

    public void addTaskList(List<Task> tasks) throws BlowoutException {
        if (!started) {
            throw new BlowoutException("Blowout hasn't been started yet");
        }
        this.blowoutPool.addTasks(tasks);
    }

    public void cleanTask(Task task) {
        this.blowoutPool.removeTask(task);
    }

    public TaskState getTaskState(String taskId) {
        Task task = null;
        for (Task taskInPool : this.blowoutPool.getAllTasks()) {
            if (taskInPool.getId().equals(taskId)) {
                task = taskInPool;
            }
        }
        if (task == null) {
            return TaskState.NOT_CREATED;
        } else {
            return this.taskMonitor.getTaskState(task);
        }
    }

    public BlowoutPool createBlowoutInstance() throws Exception {
        String blowoutPoolClassName = this.properties.getProperty(
                AppPropertiesConstants.IMPLEMENTATION_BLOWOUT_POOL,
                BlowoutDefaultConstants.IMPLEMENTATION_BLOWOUT_POOL);

        Class<?> blowoutPoolClass = Class.forName(blowoutPoolClassName);

        Object blowoutPool = blowoutPoolClass.getConstructor().newInstance();

        if (!(blowoutPool instanceof BlowoutPool)) {
            throw new Exception("Blowout Pool Class Name is not a BlowoutPool implementation");
        }
        return (BlowoutPool) blowoutPool;
    }

    public InfrastructureProvider createInfraProviderInstance(boolean removePreviousResouces)
            throws Exception {

        String infraProviderClassName = this.properties.getProperty(
                AppPropertiesConstants.IMPLEMENTATION_INFRA_PROVIDER,
                BlowoutDefaultConstants.IMPLEMENTATION_INFRA_PROVIDER
        );

        Class<?> infraProviderClass = Class.forName(infraProviderClassName);

        Object infraProvider = infraProviderClass.getConstructor(Properties.class, Boolean.TYPE)
                .newInstance(this.properties, removePreviousResouces);

        if (!(infraProvider instanceof InfrastructureProvider)) {
            throw new Exception(
                    "Infrastructure Provider Class Name is not a InfrastructureProvider implementation");
        }
        return (InfrastructureProvider) infraProvider;
    }

    public InfrastructureManager createInfraManagerInstance(InfrastructureProvider infraProvider,
                                                            ResourceMonitor resourceMonitor) throws Exception {

        String infraManagerClassName = this.properties.getProperty(
                AppPropertiesConstants.IMPLEMENTATION_INFRA_MANAGER,
                BlowoutDefaultConstants.IMPLEMENTATION_INFRA_MANAGER);

        Class<?> infraManagerClass = Class.forName(infraManagerClassName);

        Object infraManager = infraManagerClass
                .getConstructor(InfrastructureProvider.class, ResourceMonitor.class)
                .newInstance(infraProvider, resourceMonitor);

        if (!(infraManager instanceof InfrastructureManager)) {
            throw new Exception(
                    "Infrastructure Manager Class Name is not a InfrastructureManager implementation");
        }
        return (InfrastructureManager) infraManager;
    }

    // Method used in saps engine
    protected SchedulerInterface createSchedulerInstance(TaskMonitor taskMonitor) throws Exception {
        String schedulerClassName = this.properties.getProperty(
                AppPropertiesConstants.IMPLEMENTATION_SCHEDULER,
                BlowoutDefaultConstants.IMPLEMENTATION_SCHEDULER);

        Class<?> schedulerClass = Class.forName(schedulerClassName);

        Object scheduler = schedulerClass.getConstructor(TaskMonitor.class)
                .newInstance(taskMonitor);

        if (!(scheduler instanceof SchedulerInterface)) {
            throw new Exception("Scheduler Class Name is not a SchedulerInterface implementation");
        }
        return (SchedulerInterface) scheduler;
    }

    private static boolean checkProperties(Properties properties) {
        return propertiesContainsAll(properties,
                AppPropertiesConstants.IMPLEMENTATION_INFRA_PROVIDER,
                AppPropertiesConstants.INFRA_RESOURCE_IDLE_LIFETIME,
                AppPropertiesConstants.INFRA_IS_STATIC,
                AppPropertiesConstants.INFRA_AUTH_TOKEN_UPDATE_PLUGIN);
    }

    private static boolean propertiesContainsAll(Properties properties,
                                                 String... wantedProperties) {
        for (String property : wantedProperties) {
            if (!properties.containsKey(property)) {
                LOGGER.error("Required property " + property + " was not set");
                return false;
            }
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

    public int getTaskRetries(String taskId) {
        Task task = null;
        for (Task t : blowoutPool.getAllTasks()) {
            if (t.getId().equals(taskId)) {
                task = t;
            }
        }
        if (task == null) {
            return 0;
        } else {
            return task.getRetries();
        }
    }

    private TaskMonitor createTaskMonitor(BlowoutPool blowoutPool, long taskMonitorPeriod) {
        TaskMonitor taskMonitor = new TaskMonitor(blowoutPool, taskMonitorPeriod);
        taskMonitor.start();
        return taskMonitor;
    }

    private ResourceMonitor createResourceMonitor(InfrastructureProvider infraProvider, BlowoutPool blowoutPool, Properties properties) {
        ResourceMonitor resourceMonitor = new ResourceMonitor(
                infraProvider,
                blowoutPool,
                properties
        );
        resourceMonitor.start();
        return resourceMonitor;
    }
}
