package org.fogbowcloud.blowout.infrastructure.manager;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import org.fogbowcloud.blowout.core.model.Specification;
import org.fogbowcloud.blowout.core.model.Task;
import org.fogbowcloud.blowout.core.model.TaskImpl;
import org.fogbowcloud.blowout.infrastructure.model.FogbowResource;
import org.fogbowcloud.blowout.infrastructure.model.ResourceState;
import org.fogbowcloud.blowout.infrastructure.monitor.ResourceMonitor;
import org.fogbowcloud.blowout.infrastructure.provider.InfrastructureProvider;
import org.fogbowcloud.blowout.infrastructure.provider.fogbow.FogbowRequirementsHelper;
import org.fogbowcloud.blowout.infrastructure.model.AbstractResource;
import org.fogbowcloud.blowout.pool.ResourceStateHelper;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class TestDefaultInfrastructureManager {

    private static final String FAKE_UUID = "1234";
    private ResourceMonitor resourceMonitor;
    private InfrastructureProvider infraProvider;
    private DefaultInfrastructureManager defaultInfrastructureManager;

    @Before
    public void setUp() {
        infraProvider = Mockito.mock(InfrastructureProvider.class);
        resourceMonitor = Mockito.mock(ResourceMonitor.class);

        defaultInfrastructureManager = Mockito.spy(new DefaultInfrastructureManager(infraProvider, resourceMonitor));
    }

    @Test
    public void testActOneReadyTaskNoResource() throws Exception {
        String resourceId = "Rsource01";
        String taskId = "Task01";
        Specification spec = new Specification("Image", "Fogbow", "myKey", "path");

        Task task = new TaskImpl(taskId, spec, FAKE_UUID);

        List<Task> tasks = new ArrayList<>();
        tasks.add(task);
        List<AbstractResource> resources = new ArrayList<>();

        doReturn(resourceId).when(infraProvider).requestResource(spec);
        doReturn(new ArrayList<AbstractResource>()).when(resourceMonitor).getPendingResources();

        defaultInfrastructureManager.act(resources, tasks);
        verify(infraProvider, times(1)).requestResource(spec);
        verify(resourceMonitor, times(1)).addPendingResource(resourceId, spec);
    }

    @Test
    public void testActThreeReadyTaskNoResource() throws Exception {
        String resourceIdA = "Rsource01";
        String resourceIdB = "Rsource02";
        String resourceIdC = "Rsource03";

        String orderIdA = "order01";
        String orderIdB = "order02";
        String orderIdC = "order03";

        String taskIdA = "Task01";
        String taskIdB = "Task02";
        String taskIdC = "Task03";

        Specification spec = new Specification("Image", "Fogbow", "myKey", "path");

        Task taskA = new TaskImpl(taskIdA, spec, FAKE_UUID);
        Task taskB = new TaskImpl(taskIdB, spec, FAKE_UUID);
        Task taskC = new TaskImpl(taskIdC, spec, FAKE_UUID);

        //These are the resources returned when the InfrastructureManager ask for new resources.
        new FogbowResource(resourceIdA, orderIdA, spec);
        new FogbowResource(resourceIdB, orderIdB, spec);
        new FogbowResource(resourceIdC, orderIdC, spec);

        final Queue<String> resourcesToReturn = new LinkedList<>();
        resourcesToReturn.add(resourceIdA);
        resourcesToReturn.add(resourceIdB);
        resourcesToReturn.add(resourceIdC);

        List<Task> tasks = new ArrayList<>();
        tasks.add(taskA);
        tasks.add(taskB);
        tasks.add(taskC);
        List<AbstractResource> resources = new ArrayList<>();

        Answer<String> requestResourceAnswer = new Answer<String>() {

            @Override
            public String answer(InvocationOnMock invocation) {
                return resourcesToReturn.poll();
            }
        };

        doAnswer(requestResourceAnswer).when(infraProvider).requestResource(spec);
        doReturn(new ArrayList<AbstractResource>()).when(resourceMonitor).getPendingResources();

        defaultInfrastructureManager.act(resources, tasks);
        verify(infraProvider, times(3)).requestResource(spec);
        verify(resourceMonitor, times(1)).addPendingResource(resourceIdA, spec);
        verify(resourceMonitor, times(1)).addPendingResource(resourceIdB, spec);
        verify(resourceMonitor, times(1)).addPendingResource(resourceIdC, spec);
    }


    @Test
    public void testActOneReadyTaskOnePendingResource() throws Exception {
        String resourceId = "Rsource01";
        String taskId = "Task01";
        Specification spec = new Specification("Image", "Fogbow", "myKey", "path");

        Task task = new TaskImpl(taskId, spec, FAKE_UUID);
        Map<Specification, Integer> specsDemand = new HashMap<>();
        List<Task> tasks = new ArrayList<>();
        tasks.add(task);
        List<AbstractResource> resources = new ArrayList<>();
        List<String> pendingResources = new ArrayList<>();
        pendingResources.add(resourceId);
        List<Specification> pendingSpecs = new ArrayList<>();
        pendingSpecs.add(spec);
        Map<Specification, Integer> pendingRequests = new HashMap<>();
        pendingRequests.put(spec, 1);

        doReturn(pendingResources).when(resourceMonitor).getPendingResources();
        doReturn(pendingSpecs).when(resourceMonitor).getPendingSpecification();
        doReturn(pendingRequests).when(resourceMonitor).getPendingRequests();
        doReturn(specsDemand).when(defaultInfrastructureManager).generateDemandBySpec(tasks, resources);

        defaultInfrastructureManager.act(resources, tasks);
        verify(infraProvider, times(0)).requestResource(spec);
        verify(
                resourceMonitor,
                times(0)
        ).addPendingResource(Mockito.any(String.class), Mockito.any(Specification.class));
    }

    @Test
    public void testActTwoReadyTasksOnePendingResource() throws Exception {
        String resourceId = "Rsource01";
        String taskIdA = "Task01";
        String taskIdB = "Task02";
        Specification spec = new Specification("Image", "Fogbow", "myKey", "path");

        List<AbstractResource> resources = new ArrayList<>();

        Task taskA = new TaskImpl(taskIdA, spec, FAKE_UUID);
        Task taskB = new TaskImpl(taskIdB, spec, FAKE_UUID);

        List<Task> tasks = new ArrayList<>();
        tasks.add(taskA);
        tasks.add(taskB);

        Map<Specification, Integer> specsDemand = new HashMap<>();
        specsDemand.put(spec, 2);
        doReturn(specsDemand).when(defaultInfrastructureManager).generateDemandBySpec(tasks, resources);

        List<String> pendingResources = new ArrayList<>();
        pendingResources.add(resourceId);
        doReturn(pendingResources).when(resourceMonitor).getPendingResources();

        List<Specification> pendingSpecs = new ArrayList<>();
        pendingSpecs.add(spec);
        doReturn(pendingSpecs).when(resourceMonitor).getPendingSpecification();

        Map<Specification, Integer> pendingRequests = new HashMap<>();
        pendingRequests.put(spec, 1);
        doReturn(pendingRequests).when(resourceMonitor).getPendingRequests();

        defaultInfrastructureManager.act(resources, tasks);
        verify(infraProvider, times(1)).requestResource(spec);
        verify(
                resourceMonitor,
                times(1)
        ).addPendingResource(Mockito.any(String.class), Mockito.any(Specification.class));
    }

    @Test
    public void testActOnReadyTasksOnePendingResourceDiffSpec() throws Exception {
        String resourceId = "Rsource01";
        String orderId = "order01";
        String taskIdA = "Task01";

        Specification specA = new Specification("ImageA", "Fogbow", "myKeyA", "path");
        Specification specB = new Specification("ImageB", "Fogbow", "myKeyB", "path");

        Task taskA = new TaskImpl(taskIdA, specA, FAKE_UUID);
        AbstractResource pendingResource = new FogbowResource(resourceId, orderId, specB);

        List<Task> tasks = new ArrayList<>();
        tasks.add(taskA);
        List<AbstractResource> resources = new ArrayList<>();
        List<AbstractResource> pendingResources = new ArrayList<>();
        pendingResources.add(pendingResource);
        Map<Specification, Integer> pendingRequests = new HashMap<>();
        pendingRequests.put(specB, 1);
        doReturn(pendingRequests).when(resourceMonitor).getPendingRequests();

        doReturn(pendingResources).when(resourceMonitor).getPendingResources();

        defaultInfrastructureManager.act(resources, tasks);
        verify(infraProvider, times(1)).requestResource(specA);
        verify(resourceMonitor, times(1)).addPendingResource(Mockito.any(String.class), Mockito.any(Specification.class));
    }


    @Test
    public void testActOnReadyTasksOneIdleResource() throws Exception {
        String resourceId = "Rsource01";
        String orderId = "order01";
        String taskIdA = "Task01";

        String image = "image";
        String userName = "userName";
        String publicKey = "publicKey";
        String privateKey = "privateKey";
        String fogbowRequirement = "Glue2vCPU >= 1 && Glue2RAM >= 1024 ";
        String userDataFile = "scripts/lvl-user-data.sh";
        String userDataType = "text/x-shellscript";

        String coreSize = "1";
        String menSize = "1024";
        String diskSize = "20";
        String location = "edu.ufcg.lsd.cloud_1s";

        Specification specA = new Specification(image, userName, publicKey, privateKey, userDataFile, userDataType);
        specA.addRequirement(FogbowRequirementsHelper.METADATA_FOGBOW_REQUIREMENTS, fogbowRequirement);

        AbstractResource idleResource = new FogbowResource(resourceId, orderId, specA);
        idleResource.putMetadata(AbstractResource.METADATA_IMAGE, "ImageA");
        idleResource.putMetadata(AbstractResource.ENV_PRIVATE_KEY_FILE, "path");
        ResourceStateHelper.changeResourceToState(idleResource, ResourceState.IDLE);


        idleResource.putMetadata(FogbowResource.METADATA_IMAGE, image);
        idleResource.putMetadata(FogbowResource.METADATA_PUBLIC_KEY, publicKey);
        idleResource.putMetadata(FogbowResource.METADATA_VCPU, coreSize);
        idleResource.putMetadata(FogbowResource.METADATA_MEN_SIZE, menSize);
        idleResource.putMetadata(FogbowResource.METADATA_DISK_SIZE, diskSize);
        idleResource.putMetadata(FogbowResource.METADATA_LOCATION, location);

        Task taskA = new TaskImpl(taskIdA, specA, FAKE_UUID);
        List<Task> tasks = new ArrayList<>();
        tasks.add(taskA);
        List<AbstractResource> resources = new ArrayList<>();
        resources.add(idleResource);
        List<AbstractResource> pendingResources = new ArrayList<>();

        doReturn(pendingResources).when(resourceMonitor).getPendingResources();

        defaultInfrastructureManager.act(resources, tasks);
        verify(infraProvider, times(0)).requestResource(specA);
        verify(resourceMonitor, times(0)).addPendingResource(Mockito.any(String.class), Mockito.any(Specification.class));
    }

    @Test
    public void testActOnReadyTasksOneIdleResourceDiffSpec() throws Exception {
        String resourceId = "Rsource01";
        String orderId = "order01";
        String taskIdA = "Task01";

        Specification specA = new Specification("ImageA", "Fogbow", "myKeyA", "path");
        Specification specB = new Specification("ImageB", "Fogbow", "myKeyB", "path");

        Task taskA = new TaskImpl(taskIdA, specA, FAKE_UUID);
        AbstractResource idleResource = new FogbowResource(resourceId, orderId, specB);
        ResourceStateHelper.changeResourceToState(idleResource, ResourceState.IDLE);

        List<Task> tasks = new ArrayList<>();
        tasks.add(taskA);
        List<AbstractResource> resources = new ArrayList<>();
        resources.add(idleResource);
        List<AbstractResource> pendingResources = new ArrayList<>();

        doReturn(pendingResources).when(resourceMonitor).getPendingResources();

        defaultInfrastructureManager.act(resources, tasks);
        verify(infraProvider, times(1)).requestResource(specA);
        verify(resourceMonitor, times(1)).addPendingResource(Mockito.any(String.class), Mockito.any(Specification.class));
    }
	
}
