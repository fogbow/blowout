package org.fogbowcloud.blowout.infrastructure.answer;

import org.fogbowcloud.blowout.infrastructure.model.AbstractResource;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/**
 * This class simulate the Scheduler Resources Hold's structure
 * @author gustavorag
 *
 */
public class ResourceReadyAnswer implements Answer<AbstractResource>{

	private AbstractResource resourceReady = null;
	
	@Override
	public AbstractResource answer(InvocationOnMock invocation) throws Throwable {
		
		resourceReady = (AbstractResource) invocation.getArguments()[0];
		
		return null;
	}

	public AbstractResource getResourceReady() {
		return resourceReady;
	}
	
}
