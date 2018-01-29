package org.fogbowcloud.blowout.infrastructure.manager;

import org.fogbowcloud.blowout.infrastructure.model.AbstractResource;

public interface ResourceNotifier {
	
	void resourceReady(final AbstractResource resource);
	
	void resourceDeleted(final AbstractResource resource);

}