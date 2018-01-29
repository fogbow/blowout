package org.fogbowcloud.blowout.database;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.spy;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.fogbowcloud.blowout.core.model.Specification;
import org.fogbowcloud.blowout.core.util.AppPropertiesConstants;
import org.fogbowcloud.blowout.infrastructure.model.FogbowResource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TestResourceIdDataStore {
	private static final Logger LOGGER = Logger.getLogger(TestResourceIdDataStore.class);

	private final String DATASTORE_PATH = "src/test/resources/persistance/";

	private final String FAKE_ORDER_ID1 = "fakeOrderId1";
	private final String FAKE_ORDER_ID2 = "fakeOrderId2";

	private final String FAKE_RESOURCE_ID1 = "fakeResourceId1";
	private final String FAKE_RESOURCE_ID2 = "fakeResourceId2";

	Properties properties = null;
	FogbowResourceDatastore db = null;
	Specification spec = new Specification("imageA", "userA", "publicKey", "filePath");

	@Before
	public void initialize() {
		LOGGER.debug("Creating data store.");
		new File(DATASTORE_PATH).mkdir();
		properties = new Properties();
		properties.put(AppPropertiesConstants.DB_DATASTORE_URL,
				"jdbc:sqlite:" + new File(DATASTORE_PATH).getAbsolutePath() + "/resourceRequests");

		db = spy(new FogbowResourceDatastore(properties));
	}

	@After
	public void tearDown() throws IOException {
		FileUtils.cleanDirectory(new File(DATASTORE_PATH));
	}

	@Test
	public void testeAddFogbowResource() throws SQLException, InterruptedException {

		FogbowResource resource = new FogbowResource(FAKE_RESOURCE_ID1, FAKE_ORDER_ID1, spec);

		db.addFogbowResource(resource);
		List<FogbowResource> fogbowResources = db.getAllFogbowResources();

		assertEquals(1, fogbowResources.size());
		for (FogbowResource fogbowResource : fogbowResources) {
			assertEquals(FAKE_RESOURCE_ID1, fogbowResource.getId());
			assertEquals(FAKE_ORDER_ID1, fogbowResource.getOrderId());
			assertNull(fogbowResource.getInstanceId());
		}
	}

	@Test
	public void testeAddFogbowResources() throws SQLException, InterruptedException {

		FogbowResource resourceA = new FogbowResource(FAKE_RESOURCE_ID1, FAKE_ORDER_ID1, spec);
		FogbowResource resourceB = new FogbowResource(FAKE_RESOURCE_ID2, FAKE_ORDER_ID2, spec);

		List<FogbowResource> fogbowResources = new ArrayList<FogbowResource>();
		fogbowResources.add(resourceA);
		fogbowResources.add(resourceB);

		db.addResourceIds(fogbowResources);

		List<FogbowResource> returnedFogbowResources = db.getAllFogbowResources();

		assertEquals(fogbowResources.size(), returnedFogbowResources.size());
		for (FogbowResource resource : fogbowResources) {
			if (FAKE_RESOURCE_ID1.equals(resource.getId())) {
				assertEquals(FAKE_ORDER_ID1, resource.getOrderId());
				assertNull(resource.getInstanceId());
			} else if (FAKE_RESOURCE_ID2.equals(resource.getId())) {
				assertEquals(FAKE_ORDER_ID2, resource.getOrderId());
				assertNull(resource.getInstanceId());
			} else {
				fail();
			}
		}
	}

	@Test
	public void tesSpecificFogbowResource() throws SQLException, InterruptedException {

		FogbowResource resourceA = new FogbowResource(FAKE_RESOURCE_ID1, FAKE_ORDER_ID1, spec);
		FogbowResource resourceB = new FogbowResource(FAKE_RESOURCE_ID2, FAKE_ORDER_ID2, spec);

		List<FogbowResource> fogbowResources = new ArrayList<FogbowResource>();
		fogbowResources.add(resourceA);
		fogbowResources.add(resourceB);

		db.addResourceIds(fogbowResources);
		db.deleteFogbowResourceById(resourceA);
		List<FogbowResource> returnedFogbowResources = db.getAllFogbowResources();

		assertEquals(1, returnedFogbowResources.size());
		for (FogbowResource resource : returnedFogbowResources) {
			if (FAKE_RESOURCE_ID1.equals(resource.getId())) {
				fail();
			} else if (FAKE_RESOURCE_ID2.equals(resource.getId())) {
				assertEquals(FAKE_ORDER_ID2, resource.getOrderId());
				assertNull(resource.getInstanceId());
			} else {
				fail();
			}
		}
	}

	@Test
	public void testDeleteAll() throws SQLException, InterruptedException {

		FogbowResource resourceA = new FogbowResource(FAKE_RESOURCE_ID1, FAKE_ORDER_ID1, spec);
		FogbowResource resourceB = new FogbowResource(FAKE_RESOURCE_ID2, FAKE_ORDER_ID2, spec);

		List<FogbowResource> fogbowResources = new ArrayList<FogbowResource>();
		fogbowResources.add(resourceA);
		fogbowResources.add(resourceB);

		db.addResourceIds(fogbowResources);
		db.deleteAll();
		List<FogbowResource> returnedFogbowResources = db.getAllFogbowResources();

		assertEquals(0, returnedFogbowResources.size());
	}

}
