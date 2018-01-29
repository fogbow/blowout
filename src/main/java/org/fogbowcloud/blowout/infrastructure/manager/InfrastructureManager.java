package org.fogbowcloud.blowout.infrastructure.manager;

import java.util.List;

import org.fogbowcloud.blowout.core.model.Task;
import org.fogbowcloud.blowout.infrastructure.model.AbstractResource;

public interface InfrastructureManager {
	
	void act(List<AbstractResource> resources, List<Task> tasks) throws Exception ;

}
