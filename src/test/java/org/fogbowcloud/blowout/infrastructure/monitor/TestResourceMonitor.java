package org.fogbowcloud.blowout.infrastructure.monitor;

import static org.junit.Assert.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.fogbowcloud.blowout.core.model.Specification;
import org.fogbowcloud.blowout.core.util.AppPropertiesConstants;
import org.fogbowcloud.blowout.infrastructure.exception.RequestResourceException;
import org.fogbowcloud.blowout.infrastructure.model.FogbowResource;
import org.fogbowcloud.blowout.infrastructure.provider.InfrastructureProvider;
import org.fogbowcloud.blowout.infrastructure.model.AbstractResource;
import org.fogbowcloud.blowout.pool.BlowoutPool;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TestResourceMonitor {

	private ResourceMonitor resourceMonitor;
	private InfrastructureProvider infraProvider;
	private BlowoutPool resourcePool;
	
	@Before
	public void setUp() {
		
		infraProvider = Mockito.mock(InfrastructureProvider.class);
		resourcePool = Mockito.mock(BlowoutPool.class);
		Properties properties = new Properties();
		properties.setProperty(AppPropertiesConstants.INFRA_MONITOR_PERIOD, "1000");
		properties.setProperty(AppPropertiesConstants.INFRA_RESOURCE_IDLE_LIFETIME, "120000");
		properties.setProperty(AppPropertiesConstants.INFRA_RESOURCE_CONNECTION_RETRY, "3");
		
		resourceMonitor = Mockito.spy(new ResourceMonitor(infraProvider, resourcePool, properties));
	}

	@Test
	public void testProcessPendingResource() throws RequestResourceException {

		List<AbstractResource> resources = new ArrayList<>();
		
		String resourceId = "resourceA";
		String orderId = "orderA";
		
		Specification specA = new Specification("ImageA", "Fogbow", "myKeyA", "path");
		
		AbstractResource resource = new FogbowResource(resourceId, orderId, specA);
		
		doReturn(resources).when(resourcePool).getAllResources();
		doReturn(resource).when(infraProvider).getResource(resourceId);
		
		resourceMonitor.addPendingResource(resourceId, specA);
		resourceMonitor.monitorProcess();
		
		verify(resourcePool, times(1)).addResource(resource);
		assertTrue(resourceMonitor.getPendingResources().isEmpty());

	}
	
	@Test
	public void testProcessTwoPendingResourceOnReady() throws RequestResourceException {

		List<AbstractResource> resources = new ArrayList<>();
		
		String resourceIdA = "resourceA";
		String orderIdA = "orderA";
		
		String resourceIdB = "resourceB";
		
		Specification spec = new Specification("ImageA", "Fogbow", "myKeyA", "path");
		
		AbstractResource resource = new FogbowResource(resourceIdA, orderIdA, spec);
		
		doReturn(resources).when(resourcePool).getAllResources();
		doReturn(resource).when(infraProvider).getResource(resourceIdA);
		doReturn(null).when(infraProvider).getResource(resourceIdB);
		
		resourceMonitor.addPendingResource(resourceIdA, spec);
		resourceMonitor.addPendingResource(resourceIdB, spec);
		resourceMonitor.monitorProcess();
		
		verify(resourcePool, times(1)).addResource(resource);
		assertEquals(1, resourceMonitor.getPendingResources().size());

	}

	@Test
	public void testStopThread() throws InterruptedException {
		Mockito.doNothing().when(resourceMonitor).getPreviousResources();
		Mockito.doNothing().when(resourceMonitor).monitorProcess();
		Assert.assertFalse("Task monitor shouldn't have started yet", resourceMonitor.isRunning());
		resourceMonitor.start();
		Thread.yield();
		Assert.assertTrue("Task monitor should have started yet", resourceMonitor.isRunning());
		resourceMonitor.stop();
		Thread.sleep(100);
		Assert.assertFalse("Task monitor should have stopped", resourceMonitor.isRunning());
	}
	
}
