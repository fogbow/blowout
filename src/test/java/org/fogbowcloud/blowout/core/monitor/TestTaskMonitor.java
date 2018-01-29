package org.fogbowcloud.blowout.core.monitor;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.fogbowcloud.blowout.core.StandardScheduler;
import org.fogbowcloud.blowout.core.model.Command;
import org.fogbowcloud.blowout.core.model.Specification;
import org.fogbowcloud.blowout.core.model.Task;
import org.fogbowcloud.blowout.core.model.TaskImpl;
import org.fogbowcloud.blowout.core.model.TaskProcess;
import org.fogbowcloud.blowout.core.model.TaskProcessImpl;
import org.fogbowcloud.blowout.core.model.TaskState;
import org.fogbowcloud.blowout.infrastructure.manager.DefaultInfrastructureManager;
import org.fogbowcloud.blowout.infrastructure.model.FogbowResource;
import org.fogbowcloud.blowout.infrastructure.model.ResourceState;
import org.fogbowcloud.blowout.infrastructure.model.AbstractResource;
import org.fogbowcloud.blowout.pool.BlowoutPool;
import org.fogbowcloud.blowout.pool.DefaultBlowoutPool;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;


public class TestTaskMonitor {

	private static final String FAKE_UUID = "1234";
	private static final String FAKE_ID = "fakeId";
	private TaskMonitor taskMon;
	private BlowoutPool pool;
	private Specification spec;
	
	@Before
	public void setUp(){
		pool = mock(BlowoutPool.class);
		this.taskMon = spy(new TaskMonitor(pool, 100));
		spec = mock(Specification.class);
	}
	
	@Test
	public void testGetTaskStateCorrect() {
		// set up
		TaskImpl taskImpl = new TaskImpl("task-id", spec, FAKE_UUID);
		taskImpl.setState(TaskState.RUNNING);
		
		List<Command> commandListMock = mock(ArrayList.class);
		
		TaskProcessImpl taskProcessImpl = new TaskProcessImpl(taskImpl.getId(), commandListMock, spec, FAKE_UUID);
		taskProcessImpl.setStatus(TaskState.RUNNING);
		
		Map<Task, TaskProcess> runningTasks = mock(Map.class);
		
		TaskMonitor taskMon = new TaskMonitor(pool, 3000);
		taskMon.setRunningTasks(runningTasks);
		
		doReturn(taskProcessImpl).when(runningTasks).get(taskImpl);
		
		// exercise
		TaskState state = taskMon.getTaskState(taskImpl);
		
		// expect
		Assert.assertEquals(TaskState.RUNNING, state);
	}
	
	@Test
	public void testGetTaskStateCompleted() {
		// set up
		TaskImpl taskImpl = new TaskImpl("task-id", spec, FAKE_UUID);
		taskImpl.setState(TaskState.FINISHED);
		taskImpl.finish();
		
		Map<Task, TaskProcess> runningTasks = mock(Map.class);
		
		TaskMonitor taskMon = new TaskMonitor(pool, 3000);
		taskMon.setRunningTasks(runningTasks);
		
		doReturn(null).when(runningTasks).get(taskImpl);
		
		// exercise
		TaskState state = taskMon.getTaskState(taskImpl);
		
		// expect
		Assert.assertEquals(TaskState.COMPLETED, state);
	}
	
	@Test
	public void testGetTaskStateReady() {
		// set up
		TaskImpl taskImpl = new TaskImpl("task-id", spec, FAKE_UUID);
		taskImpl.setState(TaskState.READY);
		
		Map<Task, TaskProcess> runningTasks = mock(Map.class);
		
		TaskMonitor taskMon = new TaskMonitor(pool, 3000);
		taskMon.setRunningTasks(runningTasks);
		
		doReturn(null).when(runningTasks).get(taskImpl);
		
		// exercise
		TaskState state = taskMon.getTaskState(taskImpl);
		
		// expect
		Assert.assertEquals(TaskState.READY, state);
	}
	
	@Test
	public void testTaskFinished() {
		// set up
		DefaultInfrastructureManager infraManager = mock(DefaultInfrastructureManager.class);
		StandardScheduler standardScheduler = mock(StandardScheduler.class);
		
		DefaultBlowoutPool blowoutPool = new DefaultBlowoutPool();
		blowoutPool.start(infraManager, standardScheduler);

		TaskImpl taskOne = new TaskImpl("task-one-id", spec, FAKE_UUID);
		
		List<Task> taskList = new ArrayList<>();
		taskList.add(taskOne);
		
		blowoutPool.addTasks(taskList);
		
		AbstractResource resourceOne = new FogbowResource("resource-one-id", "order-one-id", spec);
		
		blowoutPool.addResource(resourceOne);
		
		List<Command> commandListMock = mock(ArrayList.class);
		
		TaskProcessImpl taskProcessOne = new TaskProcessImpl(taskOne.getId(), commandListMock, spec, FAKE_UUID);
		
		taskProcessOne.setResource(resourceOne);
		
		taskProcessOne.setStatus(TaskState.FINISHED);
		
		Map<Task, TaskProcess> runningTasks = new HashMap<>();
		runningTasks.put(taskOne, taskProcessOne);
		
		TaskMonitor taskMon = new TaskMonitor(blowoutPool, 3000);
		taskMon.setRunningTasks(runningTasks);
		
		// exercise
		taskMon.procMon();
		
		// expect
		Assert.assertEquals(ResourceState.IDLE, resourceOne.getState());
		Assert.assertEquals(true, taskOne.isFinished());
	}
	
	@Test
	public void testProcMonNothingHappens() {
		TaskProcess fakeProcess = mock(TaskProcess.class);
		doReturn(TaskState.RUNNING).when(fakeProcess).getStatus();
		AbstractResource fakeResource = mock(AbstractResource.class);
		List<TaskProcess> runningPrc = new ArrayList<>();
		runningPrc.add(fakeProcess);
		doReturn(runningPrc).when(this.taskMon).getRunningProcesses();
		
		
		this.taskMon.procMon();
		verify(pool, never()).updateResource(fakeResource, ResourceState.FAILED);
		verify(pool, never()).updateResource(fakeResource, ResourceState.IDLE);
	}
	
	@Test
	public void testProcMonProcFails() {
		Task fakeTask = mock(Task.class);
		TaskProcess fakeProcess = mock(TaskProcess.class);
		doReturn(TaskState.FAILED).when(fakeProcess).getStatus();
		AbstractResource fakeResource = mock(AbstractResource.class);
		doReturn(FAKE_ID).when(fakeTask).getId();
		doReturn(FAKE_ID).when(fakeProcess).getTaskId();
		doReturn(fakeTask).when(this.taskMon).getTaskById(FAKE_ID);
		doReturn(fakeResource).when(fakeProcess).getResource();
		List<TaskProcess> runningPrc = new ArrayList<>();
		runningPrc.add(fakeProcess);
		doReturn(runningPrc).when(this.taskMon).getRunningProcesses();
		Map<Task, TaskProcess> runningTasks = new HashMap<>();
		runningTasks.put(fakeTask, fakeProcess);
		doReturn(runningTasks).when(this.taskMon).getRunningTasks();
		
		this.taskMon.procMon();
		
		verify(pool).updateResource(fakeResource, ResourceState.FAILED);
		verify(pool, never()).updateResource(fakeResource, ResourceState.IDLE);
	}
	
	@Test
	public void testProcMonProcFinnished() {
		Task fakeTask = mock(Task.class);
		TaskProcess fakeProcess = mock(TaskProcess.class);
		doReturn(TaskState.FINISHED).when(fakeProcess).getStatus();
		AbstractResource fakeResource = mock(AbstractResource.class);
		doReturn(FAKE_ID).when(fakeTask).getId();
		doReturn(FAKE_ID).when(fakeProcess).getTaskId();
		doReturn(fakeTask).when(this.taskMon).getTaskById(FAKE_ID);
		doReturn(fakeResource).when(fakeProcess).getResource();
		List<TaskProcess> runningPrc = new ArrayList<>();
		runningPrc.add(fakeProcess);
		doReturn(runningPrc).when(this.taskMon).getRunningProcesses();
		Map<Task, TaskProcess> runningTasks = new HashMap<>();
		runningTasks.put(fakeTask, fakeProcess);
		doReturn(runningTasks).when(this.taskMon).getRunningTasks();
		
		this.taskMon.procMon();
		
		verify(pool, never()).updateResource(fakeResource, ResourceState.FAILED);
		verify(pool ).updateResource(fakeResource, ResourceState.IDLE);
	}
	
	@Test
	public void testRunTask() {
		Task fakeTask = mock(Task.class);
		TaskProcess fakeProcess = mock(TaskProcess.class);
		AbstractResource fakeResource = mock(AbstractResource.class);
		doReturn(FAKE_ID).when(fakeTask).getId();
		doReturn(FAKE_ID).when(fakeProcess).getTaskId();
		List<TaskProcess> runningPrc = new ArrayList<>();
		runningPrc.add(fakeProcess);
		doReturn(runningPrc).when(this.taskMon).getRunningProcesses();
		doNothing().when(pool).updateResource(fakeResource, ResourceState.BUSY);
		ExecutorService execServ = mock(ExecutorService.class);
		doReturn(execServ).when(this.taskMon).getExecutorService();
		doReturn(mock(Future.class)).when(execServ).submit(any(Runnable.class));
		Map<Task, TaskProcess> runningTasks = new HashMap<>();
		runningTasks.put(fakeTask, fakeProcess);
		doReturn(runningTasks).when(this.taskMon).getRunningTasks();
		
		this.taskMon.runTask(fakeTask, fakeResource);
	}

	@Test
	public void testStopThread() throws InterruptedException {
        Mockito.doNothing().when(taskMon).procMon();
        Assert.assertFalse("Task monitor shouldn't have started yet", taskMon.isRunning());
        taskMon.start();
        Thread.yield();
        Assert.assertTrue("Task monitor should have started yet", taskMon.isRunning());
        taskMon.stop();
        Thread.sleep(100);
        Assert.assertFalse("Task monitor should have stopped", taskMon.isRunning());
	}
	
}
