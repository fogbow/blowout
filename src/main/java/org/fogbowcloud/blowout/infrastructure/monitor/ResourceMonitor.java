package org.fogbowcloud.blowout.infrastructure.monitor;

import org.apache.log4j.Logger;
import org.fogbowcloud.blowout.core.model.Specification;
import org.fogbowcloud.blowout.core.util.AppPropertiesConstants;
import org.fogbowcloud.blowout.infrastructure.exception.RequestResourceException;
import org.fogbowcloud.blowout.infrastructure.model.AbstractResource;
import org.fogbowcloud.blowout.infrastructure.model.ResourceState;
import org.fogbowcloud.blowout.infrastructure.provider.InfrastructureProvider;
import org.fogbowcloud.blowout.pool.BlowoutPool;
import org.fogbowcloud.manager.occi.order.OrderType;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

public class ResourceMonitor implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(ResourceMonitor.class);

    private InfrastructureProvider infraProvider;
    private BlowoutPool resourcePool;
    private Map<String, Long> idleResources = new ConcurrentHashMap<>();
    private Map<String, Specification> pendingResources = new ConcurrentHashMap<>();

    private Thread monitoringServiceRunner;
    private Long infraMonitoringPeriod;
    private Long idleLifeTime;
    private int maxConnectionTries;
    private int maxReuse;

    private boolean paused = false;
    private boolean active = true;

    public ResourceMonitor(InfrastructureProvider infraProvider, BlowoutPool blowoutPool,
                           Properties properties) {

        this.infraProvider = infraProvider;
        this.resourcePool = blowoutPool;

        String defaultInfraMonitorPeriod = "30000";
        String defaultIdleLifeTime = "120000";
        String defaultMaxConnectTries = "1";
        String defaultMaxReuse = "1";

        this.infraMonitoringPeriod = Long.parseLong(properties.getProperty(
                AppPropertiesConstants.INFRA_MONITOR_PERIOD, defaultInfraMonitorPeriod));
        this.idleLifeTime = Long.parseLong(properties.getProperty(
                AppPropertiesConstants.INFRA_RESOURCE_IDLE_LIFETIME, defaultIdleLifeTime));
        this.maxConnectionTries = Integer.parseInt(properties.getProperty(
                AppPropertiesConstants.INFRA_RESOURCE_CONNECTION_RETRY, defaultMaxConnectTries));
        this.maxReuse = Integer.parseInt(properties
                .getProperty(AppPropertiesConstants.INFRA_RESOURCE_REUSE_TIMES, defaultMaxReuse));
    }

    public void start() {
        LOGGER.info("Starting Resource Monitor");
        this.getPreviousResources();
        this.monitoringServiceRunner = startRunner();
    }

    private Thread startRunner() {
        Thread t = new Thread(this);
        t.start();
        return t;
    }

    public void getPreviousResources() {
        List<AbstractResource> previousResources = this.infraProvider.getAllResources();
        for (AbstractResource resource : previousResources) {
            this.pendingResources.put(resource.getId(), resource.getRequestedSpec());
        }
    }

    public void stop() {
        LOGGER.info("Stopping Resource Monitor");
        active = false;
        resume();

        this.monitoringServiceRunner.interrupt();

        LOGGER.debug("Removing all resources to shutdown");
        this.deletePendingResources();
        this.deleteAllocatedResources();
    }

    private void deletePendingResources() {
        for (String pendingResourceId : this.pendingResources.keySet()) {
            try {
                this.infraProvider.deleteResource(pendingResourceId);
            } catch (Exception e) {
                LOGGER.error("Was not possible delete the pending resource [" + pendingResourceId
                        + "], delete manually", e);
            }
        }
    }

    private void deleteAllocatedResources() {
        for (AbstractResource resource : this.resourcePool.getAllResources()) {
            try {
                this.infraProvider.deleteResource(resource.getId());
            } catch (Exception e) {
                LOGGER.error("Was not possible delete the allocated resources [" + resource.getId()
                        + "], delete manually", e);
            }
        }
    }

    public void addPendingResource(String resourceId, Specification spec) {
        this.pendingResources.put(resourceId, spec);
        if (this.isPaused()) {
            this.resume();
        }
    }

    @Override
    public void run() {
        while (this.active) {
            try {
                this.monitorProcess();
                Thread.sleep(infraMonitoringPeriod);
            } catch (InterruptedException e) {
                LOGGER.debug("MonitoringService interrupted");
            }
        }
    }

    public void monitorProcess() {
        LOGGER.debug("Resource Monitor process");
        this.monitoringPendingResources();
        this.monitoringResources(resourcePool.getAllResources());
    }

    private void monitoringPendingResources() {
        for (String resourceId : getPendingResources()) {
            try {
                AbstractResource resource = infraProvider.getResource(resourceId);
                if (resource != null) {
                    pendingResources.remove(resourceId);
                    resourcePool.addResource(resource);
                }
            } catch (RequestResourceException e) {
                pendingResources.remove(resourceId);
                // hack to call method callAct on pool
                resourcePool.addResourceList(new ArrayList<AbstractResource>());
            }
        }
    }

    private void monitoringResources(List<AbstractResource> resources) {
        for (AbstractResource resource : resources) {
            AbstractResource updatedResource = null;
            try {
                updatedResource = infraProvider.getResource(resource.getId());
            } catch (RequestResourceException e) {
                LOGGER.debug("No resource with given ID was found.");
                resourcePool.updateResource(resource, ResourceState.TO_REMOVE);
            }
            if (updatedResource == null) {
                LOGGER.debug("No resource with given ID was found.");
                resourcePool.updateResource(resource, ResourceState.TO_REMOVE);
            } else {
                resource = updatedResource;
            }
            if (ResourceState.IDLE.equals(resource.getState())) {
                this.resolveIdleResource(resource);
            } else if (ResourceState.BUSY.equals(resource.getState())) {
                idleResources.remove(resource.getId());
            } else if (ResourceState.FAILED.equals(resource.getState())) {
                idleResources.remove(resource.getId());
                boolean isAlive = this.checkResourceConnectivity(resource);
                if (isAlive) {
                    Long expirationDate = this.canMoveResourceToIdle(resource);
                    this.moveResourceToIdle(resource, expirationDate);
                }
            }
            if (ResourceState.TO_REMOVE.equals(resource.getState())) {
                try {
                    idleResources.remove(resource.getId());
                    resourcePool.removeResource(resource);
                    infraProvider.deleteResource(resource.getId());
                } catch (Exception e) {
                    LOGGER.error("Error while tring to remove resource " + resource.getId(), e);
                }
            }
        }
    }

    private void resolveIdleResource(AbstractResource resource) {

        Long expirationDateTime = idleResources.get(resource.getId());

        if (expirationDateTime == null) {
            Long expirationDate = this.canMoveResourceToIdle(resource);
            this.moveResourceToIdle(resource, expirationDate);
        } else {

            if (this.isSameRequestType(resource)) {

                boolean isAlive = this.checkResourceConnectivity(resource);
                if (isAlive) {
                    Date expirationDate = new Date(expirationDateTime);
                    Date currentDate = new Date();

                    if (expirationDate.before(currentDate)) {
                        LOGGER.warn("Removing resource " + resource.getId()
                                + " due Idle time expired.");
                        idleResources.remove(resource.getId());
                        resourcePool.updateResource(resource, ResourceState.TO_REMOVE);
                    }
                }
            }
        }
    }

    private void moveResourceToIdle(AbstractResource resource, Long expirationDate) {
        if (expirationDate != null) {
            idleResources.put(resource.getId(), expirationDate);
            resourcePool.updateResource(resource, ResourceState.IDLE);
        } else {
            resourcePool.updateResource(resource, ResourceState.TO_REMOVE);
        }
    }

    private Long canMoveResourceToIdle(AbstractResource resource) {
        // TODO: Check the following options for maxReuse problem
        // 1. See if it's viable to only mark resource as TO_REMOVE
        // if there's no task processes READY or RUNNING
        // 2. Make maxReuse indefinite by default and not one
        // 3. Always reuse instance
        if (resource.getReusedTimes() < maxReuse) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(new Date());
            calendar.add(Calendar.MILLISECOND, idleLifeTime.intValue());
            return calendar.getTimeInMillis();
        } else {
            return null;
        }
    }

    private boolean checkResourceConnectivity(AbstractResource resource) {
        if (!resource.checkConnectivity()) {
            if (resource.getConnectionFailTries() >= maxConnectionTries) {
                resourcePool.updateResource(resource, ResourceState.TO_REMOVE);
            } else {
                resourcePool.updateResource(resource, ResourceState.FAILED);
            }
            return false;
        }
        return true;
    }

    private boolean isSameRequestType(AbstractResource resource) {
        String requestType = resource.getMetadataValue(AbstractResource.METADATA_REQUEST_TYPE);

        return OrderType.ONE_TIME.getValue().equals(requestType);
    }

    public void checkIsPaused() throws InterruptedException {
        synchronized (this) {
            while (this.paused) {
                super.wait();
            }
        }
    }

    public synchronized void pause() {
        this.paused = true;
    }

    private synchronized void resume() {
        this.paused = false;
        super.notify();
    }

    public boolean isPaused() {
        return this.paused;
    }

    public Map<Specification, Integer> getPendingRequests() {
        Map<Specification, Integer> specCount = new HashMap<>();
        for (Entry<String, Specification> e : this.pendingResources.entrySet()) {
            if (specCount.containsKey(e.getValue())) {
                specCount.put(e.getValue(), specCount.get(e.getValue()) + 1);
            } else {
                specCount.put(e.getValue(), 1);
            }
        }
        return specCount;
    }

    public List<String> getPendingResources() {
        return new ArrayList<>(this.pendingResources.keySet());
    }

    public List<Specification> getPendingSpecification() {
        return new ArrayList<>(this.pendingResources.values());
    }

    public boolean isRunning() {
        return monitoringServiceRunner != null && monitoringServiceRunner.isAlive();
    }
}