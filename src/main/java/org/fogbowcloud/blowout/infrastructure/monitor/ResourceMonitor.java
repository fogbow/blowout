package org.fogbowcloud.blowout.infrastructure.monitor;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.Logger;
import org.fogbowcloud.blowout.core.model.Specification;
import org.fogbowcloud.blowout.core.util.AppPropertiesConstants;
import org.fogbowcloud.blowout.infrastructure.model.ResourceState;
import org.fogbowcloud.blowout.infrastructure.provider.InfrastructureProvider;
import org.fogbowcloud.blowout.pool.AbstractResource;
import org.fogbowcloud.blowout.pool.BlowoutPool;
import org.fogbowcloud.manager.occi.order.OrderType;

public class ResourceMonitor {

	private static final Logger LOGGER = Logger.getLogger(ResourceMonitor.class);

	private InfrastructureProvider infraProvider;
	private BlowoutPool resourcePool;
	private Map<String, Long> idleResources = new ConcurrentHashMap<String, Long>();
	private Map<String, Specification> pendingResources = new ConcurrentHashMap<String, Specification>();

	private Thread monitoringServiceRunner;
	private MonitoringService monitoringService;
	private long infraMonitoringPeriod;
	private Long noExpirationTime = new Long(0);
	private Long idleLifeTime = new Long(0);
	private int maxConnectionTries;
	private int maxReuse;
	
	public ResourceMonitor(InfrastructureProvider infraProvider, BlowoutPool blowoutPool, Properties properties) {
		this.infraProvider = infraProvider;
		this.resourcePool = blowoutPool;
		infraMonitoringPeriod = Long
				.parseLong(properties.getProperty(AppPropertiesConstants.INFRA_MONITOR_PERIOD, "30000"));
		this.idleLifeTime = Long
				.parseLong(properties.getProperty(AppPropertiesConstants.INFRA_RESOURCE_IDLE_LIFETIME, "0"));
		this.maxConnectionTries = Integer
				.parseInt(properties.getProperty(AppPropertiesConstants.INFRA_RESOURCE_CONNECTION_RETRY, "1"));
		this.maxReuse = Integer
				.parseInt(properties.getProperty(AppPropertiesConstants.INFRA_RESOURCE_REUSE_TIMES, "1"));
		
		monitoringService = new MonitoringService();
		monitoringServiceRunner = new Thread(monitoringService);
		List<AbstractResource> previouResources = infraProvider.getAllResources();
		if (previouResources != null && !previouResources.isEmpty()) {
			resourcePool.addResourceList(previouResources);
		}
		
	}

	public void start() {
		monitoringServiceRunner.start();
		LOGGER.warn("Started");
	}

	public void addPendingResource(String resourceId, Specification spec){
		pendingResources.put(resourceId, spec);
		if(monitoringService.isPaused()){
			monitoringService.resume();
		}
	}
	
	protected class MonitoringService implements Runnable {

		private boolean paused = false;
		private boolean active = true;

		@Override
		public void run() {

			while (active) {

				try {
					//checkIsPaused();
					monitorProcess();
					Thread.sleep(infraMonitoringPeriod);
					
				} catch (InterruptedException e) {
					LOGGER.error("Error while executing MonitoringService");
				}
			}

		}

		protected void monitorProcess() throws InterruptedException {
			
			List<AbstractResource> resources = resourcePool.getAllResources();
			monitoringPendingResources();
			monitoringResources(resources);
		}
		
		private void monitoringPendingResources() {

			for (String resourceId : getPendingResources()) {
				AbstractResource resource = infraProvider.getResource(resourceId);
				if (resource != null) {
					pendingResources.remove(resourceId);
					resourcePool.addResource(resource);
				}
			}
		}
		
		private void monitoringResources(List<AbstractResource> resources) {

			for (AbstractResource resource : resources) {

				if (ResourceState.IDLE.equals(resource.getState())) {
					resolveIdleResource(resource);
				} else if (ResourceState.BUSY.equals(resource.getState())) {
					idleResources.remove(resource.getId());
				} else if (ResourceState.FAILED.equals(resource.getState())) {
					idleResources.remove(resource.getId());
					boolean isAlive = this.checkResourceConnectivity(resource);
					if(isAlive){
						if(moveResourceToIdle(resource)){
							resourcePool.updateResource(resource, ResourceState.IDLE);
						}
					}
				} else if (ResourceState.TO_REMOVE.equals(resource.getState())) {
					try {
						idleResources.remove(resource.getId());
						infraProvider.deleteResource(resource.getId());
						resourcePool.removeResource(resource);
					} catch (Exception e) {
						LOGGER.error("Error while tring to remove resource "+resource.getId()+" - "+e.getMessage());
					}
					
				}

			}
		}

		private void resolveIdleResource(AbstractResource resource) {

			Long expirationDateTime = idleResources.get(resource.getId());

			// If since == null, resource must go to IDLE list.
			if (expirationDateTime == null) {
				moveResourceToIdle(resource);
			} else {

				String requestType = resource.getMetadataValue(AbstractResource.METADATA_REQUEST_TYPE);
				if (OrderType.ONE_TIME.getValue().equals(requestType)) {

					boolean isAlive = checkResourceConnectivity(resource);
					// Has expiration time?
					if (isAlive && noExpirationTime.compareTo(expirationDateTime) != 0) {
						Date expirationDate = new Date(expirationDateTime.longValue());
						Date currentDate = new Date();
						if (expirationDate.before(currentDate)) {
							LOGGER.warn("Removing resource "+resource.getId()+" due Idle time expired.");
							resourcePool.updateResource(resource, ResourceState.TO_REMOVE);
							idleResources.remove(resource.getId());
						}
					}
				}
			}
		}

		private boolean moveResourceToIdle(AbstractResource resource) {
			// TODO: Check the following options for maxReuse problem
			//       1. See if it's viable to only mark resource as TO_REMOVE
			//          if there's no task processes READY or RUNNING
			//       2. Make maxReuse indefinite by default and not one
			//       3. Always reuse instance
			if(resource.getReusedTimes() < maxReuse){
				Long expirationDate = (long) 0;
				expirationDate = Long.valueOf(+idleLifeTime);
				Calendar c = Calendar.getInstance();
				c.setTime(new Date());
				c.add(Calendar.MILLISECOND, idleLifeTime.intValue());
				expirationDate = c.getTimeInMillis();
				idleResources.put(resource.getId(), expirationDate);
				return true;
			}else{
				resourcePool.updateResource(resource, ResourceState.TO_REMOVE);
				return false;
			}
		}

		private boolean checkResourceConnectivity(AbstractResource resource) {
			if (!resource.checkConnectivity()) {
				if(resource.getConnectionFailTries() >= maxConnectionTries){
					resourcePool.updateResource(resource, ResourceState.TO_REMOVE);
				}else{
					resourcePool.updateResource(resource, ResourceState.FAILED);
				}
				return false;
			}
			return true;
		}

		public void checkIsPaused() throws InterruptedException {
			synchronized (this) {
				while (paused) {
					wait();
				}
			}
		}
		

		public synchronized void stop() {
			if(paused){
				resume();
			}
			active = false;
		}

		public synchronized void pause() {
			paused = true;
		}

		public synchronized void resume() {
			paused = false;
			notify();
		}

		public boolean isPaused() {
			return paused;
		}
	}
	
	public void stop(){
		if(monitoringService.isPaused()){
			monitoringService.resume();
		}
		monitoringService.stop();
	}

	protected void setMonitoringService(MonitoringService monitoringService){
		this.monitoringService = monitoringService;
	}
	
	protected MonitoringService getMonitoringService(){
		return monitoringService;
	}
	
	public List<String> getPendingResources() {
		return new ArrayList<String>(pendingResources.keySet());
	}
	
	public List<Specification> getPendingSpecification() {
		return new ArrayList<Specification>(pendingResources.values());
	}
	
	public Map<Specification, Integer> getPendingRequests() {
		Map<Specification, Integer> specCount = new HashMap<Specification, Integer>();
		for (Entry<String, Specification> e : this.pendingResources.entrySet()) {
			if (specCount.containsKey(e.getValue())) {
				specCount.put(e.getValue(), specCount.get(e.getValue()) +1);
			} else {
				specCount.put(e.getValue(), 1);
			}
		}
		return specCount;
		
	}
	
}
