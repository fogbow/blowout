package org.fogbowcloud.blowout.infrastructure.manager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.fogbowcloud.blowout.core.model.Specification;
import org.fogbowcloud.blowout.core.model.Task;
import org.fogbowcloud.blowout.infrastructure.exception.RequestResourceException;
import org.fogbowcloud.blowout.infrastructure.model.ResourceState;
import org.fogbowcloud.blowout.infrastructure.monitor.ResourceMonitor;
import org.fogbowcloud.blowout.infrastructure.provider.InfrastructureProvider;
import org.fogbowcloud.blowout.pool.AbstractResource;

public class DefaultInfrastructureManager implements InfrastructureManager {

	private InfrastructureProvider infraProvider;
	private ResourceMonitor resourceMonitor;

	public DefaultInfrastructureManager(InfrastructureProvider infraProvider,
			ResourceMonitor resourceMonitor) {
		this.infraProvider = infraProvider;
		this.resourceMonitor = resourceMonitor;
	}

	@Override
	public synchronized void act(List<AbstractResource> resources,
			List<Task> tasks) throws Exception {
		//Get all resource requests that were not addressed yet
		Map<Specification, Integer> specsDemand = (HashMap<Specification, Integer>) generateDemandBySpec(
				tasks, resources);
		//request enough resources to address the demands (excluding the pending requests)
		requestResources(specsDemand);
	}

	private void requestResources(Map<Specification, Integer> specsDemand)
			throws RequestResourceException {
		// Request resources according to the demand.
		for (Entry<Specification, Integer> entry : specsDemand.entrySet()) {

			Specification spec = entry.getKey();
			// Reduce requests by pending resources
			Integer requested = this.resourceMonitor.getPendingRequests().get(
					spec);
			if (requested == null)
				requested = 0;
			int requiredResources = entry.getValue() - requested;
			;
			for (int count = 0; count < requiredResources; count++) {

				String resourceId = infraProvider.requestResource(spec);
				resourceMonitor.addPendingResource(resourceId, spec);
			}
		}
	}

	private List<AbstractResource> filterResourcesByState(
			List<AbstractResource> resources, ResourceState... resourceStates) {

		List<AbstractResource> filteredResources = new ArrayList<AbstractResource>();
		for (AbstractResource resource : resources) {
			for (ResourceState state : resourceStates) {
				if (state.equals(resource.getState())) {
					filteredResources.add(resource);
				}
			}
		}

		return filteredResources;

	}

	private Map<Specification, Integer> generateDemandBySpec(List<Task> tasks,
			List<AbstractResource> resources) {
		Map<Specification, Integer> specsDemand = new HashMap<Specification, Integer>();

		// FIXME: this variable name is incorrect, since the list will not
		// contain only idle resources
		List<AbstractResource> currentResources = filterResourcesByState(
				resources, ResourceState.IDLE, ResourceState.BUSY,
				ResourceState.FAILED);
		// Generate demand for tasks
		for (Task task : tasks) {

			if (!task.isFinished()) {

				boolean resourceResolved = false;

				for (AbstractResource resource : currentResources) {
					if (resource.match(task.getSpecification())) {
						resourceResolved = true;
						currentResources.remove(resource);
						break;
					}
				}
				if (!resourceResolved) {
					incrementDecrementDemand(specsDemand,
							task.getSpecification(), true);
				}
			}
		}
		return specsDemand;
	}

	private void incrementDecrementDemand(
			Map<Specification, Integer> specsDemand, Specification spec,
			boolean increment) {
		Integer zero = new Integer(0);
		Integer demand = specsDemand.get(spec);
		if (demand == null) {
			demand = zero;
		}
		demand = new Integer(demand.intValue() + (increment ? 1 : -1));
		specsDemand.put(spec, zero.compareTo(demand) > 0 ? zero : demand);
	}

}