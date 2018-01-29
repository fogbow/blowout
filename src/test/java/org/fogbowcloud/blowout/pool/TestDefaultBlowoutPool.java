package org.fogbowcloud.blowout.pool;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doReturn;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.fogbowcloud.blowout.core.StandardScheduler;
import org.fogbowcloud.blowout.core.model.Specification;
import org.fogbowcloud.blowout.core.model.Task;
import org.fogbowcloud.blowout.core.model.TaskImpl;
import org.fogbowcloud.blowout.core.monitor.TaskMonitor;
import org.fogbowcloud.blowout.infrastructure.manager.DefaultInfrastructureManager;
import org.fogbowcloud.blowout.infrastructure.manager.InfrastructureManager;
import org.fogbowcloud.blowout.infrastructure.model.AbstractResource;
import org.fogbowcloud.blowout.infrastructure.model.FogbowResource;
import org.fogbowcloud.blowout.infrastructure.model.ResourceState;
import org.fogbowcloud.blowout.infrastructure.monitor.ResourceMonitor;
import org.fogbowcloud.blowout.infrastructure.provider.InfrastructureProvider;
import org.fogbowcloud.blowout.infrastructure.provider.fogbow.FogbowRequirementsHelper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestDefaultBlowoutPool {
	
	private static final String FAKE_UUID = "1234";
	private DefaultBlowoutPool defaultBlowoutPool;
	private InfrastructureManager infraManager;
	private StandardScheduler standardScheduler;
	private Specification spec;
	
	@Before
	public void setUp() {
		defaultBlowoutPool = spy(new DefaultBlowoutPool());
		spec = new Specification("fakeimage", "fakeusername", "fakepublickey", "fakekeypath");
		spec.addRequirement(FogbowRequirementsHelper.METADATA_FOGBOW_REQUIREMENTS, "fakeRequirements");
	}
	
	@Test
	public void testCallAct() {
		// set up
		FogbowResource resourceOne = spy(new FogbowResource("resource-one-id", "order-one-id", spec));
		resourceOne.setState(ResourceState.IDLE);
		doReturn(true).when(resourceOne).match(spec);
		
		FogbowResource resourceTwo = spy( new FogbowResource("resource-two-id", "order-two-id", spec));
		resourceTwo.setState(ResourceState.IDLE);
		doReturn(true).when(resourceTwo).match(spec);
		
		Map<String, AbstractResource> resourcePool = new ConcurrentHashMap<String, AbstractResource>();
		resourcePool.put(resourceTwo.getId(), resourceTwo);
		resourcePool.put(resourceOne.getId(), resourceOne);
		
		TaskImpl taskOne = new TaskImpl("task-one-id", spec, FAKE_UUID);
		TaskImpl taskTwo = new TaskImpl("task-two-id", spec, FAKE_UUID);
		
		List<Task> taskList = new ArrayList<Task>();
		taskList.add(taskOne);
		taskList.add(taskTwo);
		
		defaultBlowoutPool.setResourcePool(resourcePool);
		defaultBlowoutPool.setTaskPool(taskList);
		
		InfrastructureProvider fogbowInfraProvider = mock(InfrastructureProvider.class);
		ResourceMonitor resourceMonitor = mock(ResourceMonitor.class);
		
		TaskMonitor taskMon = new TaskMonitor(defaultBlowoutPool, 3000);
		
		infraManager = new DefaultInfrastructureManager(fogbowInfraProvider, resourceMonitor);
		standardScheduler = new StandardScheduler(taskMon);
		
		defaultBlowoutPool.start(infraManager, standardScheduler);
		
		// exercise
		defaultBlowoutPool.callAct();
		
		// expect
		Assert.assertEquals(ResourceState.BUSY, defaultBlowoutPool.getResourceById(resourceOne.getId()).getState());
		Assert.assertEquals(ResourceState.BUSY, defaultBlowoutPool.getResourceById(resourceTwo.getId()).getState());
	}
	
	@Test
	public void testAddTask(){
		// set up
				
				Map<String, AbstractResource> resourcePool = new ConcurrentHashMap<String, AbstractResource>();
				
				
				List<Task> taskList = new ArrayList<Task>();
				
				defaultBlowoutPool.setResourcePool(resourcePool);
				defaultBlowoutPool.setTaskPool(taskList);
				
				InfrastructureProvider fogbowInfraProvider = mock(InfrastructureProvider.class);
				ResourceMonitor resourceMonitor = mock(ResourceMonitor.class);
				
				TaskMonitor taskMon = new TaskMonitor(defaultBlowoutPool, 3000);
				
				infraManager = spy( new DefaultInfrastructureManager(fogbowInfraProvider, resourceMonitor));
				standardScheduler = spy( new StandardScheduler(taskMon));
				
				defaultBlowoutPool.start(infraManager, standardScheduler);
				
				// exercise
				defaultBlowoutPool.callAct();
				TaskImpl task = new TaskImpl("task-two-id", spec, FAKE_UUID);

				
				defaultBlowoutPool.putTask(task);
				verify(resourceMonitor).addPendingResource(any(String.class), any (Specification.class));
				// expect
				
	}
	
	@Test
	public void testAddTaskWithFreeResourceJobs(){
		// set up
		FogbowResource resourceOne = spy(new FogbowResource("resource-one-id", "order-one-id", spec));
		doReturn(true).when(resourceOne).match(spec);
		resourceOne.setState(ResourceState.IDLE);
		
		Map<String, AbstractResource> resourcePool = new ConcurrentHashMap<String, AbstractResource>();
		
		resourcePool.put(resourceOne.getId(), resourceOne);
		
		List<Task> taskList = new ArrayList<Task>();
		
		defaultBlowoutPool.setResourcePool(resourcePool);
		defaultBlowoutPool.setTaskPool(taskList);
		
		InfrastructureProvider fogbowInfraProvider = mock(InfrastructureProvider.class);
		ResourceMonitor resourceMonitor = mock(ResourceMonitor.class);
		
		TaskMonitor taskMon = new TaskMonitor(defaultBlowoutPool, 3000);
		
		infraManager = spy( new DefaultInfrastructureManager(fogbowInfraProvider, resourceMonitor));
		standardScheduler = spy( new StandardScheduler(taskMon));
		
		defaultBlowoutPool.start(infraManager, standardScheduler);
		
		// exercise
		defaultBlowoutPool.callAct();
		TaskImpl task = new TaskImpl("task-two-id", spec, FAKE_UUID);

		defaultBlowoutPool.putTask(task);
		
//		TaskImpl task2 = new TaskImpl("task-two-id2", spec, FAKE_UUID);
//
//		defaultBlowoutPool.putTask(task2);
		
		verify(resourceMonitor, never()).addPendingResource(any(String.class), any (Specification.class));
		// expect
		
	}
	
	@Test
	public void testAddTaskWithRunningTask(){
		// set up
		FogbowResource resourceOne = spy(new FogbowResource("resource-one-id", "order-one-id", spec));
		resourceOne.setState(ResourceState.BUSY);
		doReturn(true).when(resourceOne).match(spec);
		
		Map<String, AbstractResource> resourcePool = new ConcurrentHashMap<String, AbstractResource>();
		
		resourcePool.put(resourceOne.getId(), resourceOne);
		
		List<Task> taskList = new ArrayList<Task>();
		
		TaskImpl task = new TaskImpl("task-two-id2", spec, FAKE_UUID);
		taskList.add(task);
		defaultBlowoutPool.setResourcePool(resourcePool);
		defaultBlowoutPool.setTaskPool(taskList);
		
		InfrastructureProvider fogbowInfraProvider = mock(InfrastructureProvider.class);
		ResourceMonitor resourceMonitor = mock(ResourceMonitor.class);
		
		TaskMonitor taskMon = new TaskMonitor(defaultBlowoutPool, 3000);
		
		infraManager = spy( new DefaultInfrastructureManager(fogbowInfraProvider, resourceMonitor));
		standardScheduler = spy( new StandardScheduler(taskMon));
		
		defaultBlowoutPool.start(infraManager, standardScheduler);
		
		// exercise
		TaskImpl task2 = new TaskImpl("task-two-id", spec, FAKE_UUID);

		defaultBlowoutPool.putTask(task2);
		
//		TaskImpl task2 = new TaskImpl("task-two-id2", spec, FAKE_UUID);
//
//		defaultBlowoutPool.putTask(task2);
		verify(defaultBlowoutPool).callAct();
		verify(resourceMonitor).addPendingResource(any(String.class), any (Specification.class));
		// expect
		
	}
	
	@Test
	public void testStopTask(){
		// set up
				
				Map<String, AbstractResource> resourcePool = new ConcurrentHashMap<String, AbstractResource>();
				
				
				List<Task> taskList = new ArrayList<Task>();
				TaskImpl task = new TaskImpl("task-two-id", spec, FAKE_UUID);
				taskList.add(task);
				defaultBlowoutPool.setResourcePool(resourcePool);
				defaultBlowoutPool.setTaskPool(taskList);
				
				InfrastructureProvider fogbowInfraProvider = mock(InfrastructureProvider.class);
				ResourceMonitor resourceMonitor = mock(ResourceMonitor.class);
				
				TaskMonitor taskMon = new TaskMonitor(defaultBlowoutPool, 3000);
				
				infraManager = spy( new DefaultInfrastructureManager(fogbowInfraProvider, resourceMonitor));
				standardScheduler = spy( new StandardScheduler(taskMon));
				
				defaultBlowoutPool.start(infraManager, standardScheduler);
				
				// exercise
				defaultBlowoutPool.callAct();

				//TODO: Create pending resource remover on monitor
				defaultBlowoutPool.removeTask(task);
				verify(resourceMonitor).addPendingResource(any(String.class), any (Specification.class));
				// expect
				
	}
	
	
	@Test
	public void testUpdateResource() {
		// set up
		FogbowResource resource = spy(new FogbowResource("resource-id", "order-id", spec));
		resource.setState(ResourceState.BUSY);
		
		Map<String, AbstractResource> resourcePool = new ConcurrentHashMap<String, AbstractResource>();
		resourcePool.put(resource.getId(), resource);
		
		defaultBlowoutPool.setResourcePool(resourcePool);
		
		// exercise
		defaultBlowoutPool.updateResource(resource, ResourceState.IDLE);
		
		// expect
		Assert.assertEquals(ResourceState.IDLE, resource.getState());
	}
	
	@Test
	public void testAddResourceToList() {
		
	}
}
