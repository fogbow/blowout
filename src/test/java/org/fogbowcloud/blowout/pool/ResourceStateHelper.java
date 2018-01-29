package org.fogbowcloud.blowout.pool;

import org.fogbowcloud.blowout.infrastructure.model.AbstractResource;
import org.fogbowcloud.blowout.infrastructure.model.ResourceState;

public class ResourceStateHelper {
	
	public static AbstractResource changeResourceToState(AbstractResource resource, ResourceState state){
		resource.setState(state);
		return resource;
	}

}
