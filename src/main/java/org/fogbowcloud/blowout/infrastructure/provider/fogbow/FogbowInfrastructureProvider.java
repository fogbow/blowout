package org.fogbowcloud.blowout.infrastructure.provider.fogbow;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import org.apache.log4j.Logger;
import org.fogbowcloud.blowout.core.model.Specification;
import org.fogbowcloud.blowout.core.util.AppPropertiesConstants;
import org.fogbowcloud.blowout.core.util.AppUtil;
import org.fogbowcloud.blowout.database.FogbowResourceDatastore;
import org.fogbowcloud.blowout.infrastructure.exception.InfrastructureException;
import org.fogbowcloud.blowout.infrastructure.exception.RequestResourceException;
import org.fogbowcloud.blowout.infrastructure.http.HttpWrapper;
import org.fogbowcloud.blowout.infrastructure.model.FogbowResource;
import org.fogbowcloud.blowout.infrastructure.provider.InfrastructureProvider;
import org.fogbowcloud.blowout.infrastructure.token.AbstractTokenUpdatePlugin;
import org.fogbowcloud.blowout.pool.AbstractResource;
import org.fogbowcloud.manager.core.UserdataUtils;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.model.Token.User;
import org.fogbowcloud.manager.occi.order.OrderAttribute;
import org.fogbowcloud.manager.occi.order.OrderConstants;

public class FogbowInfrastructureProvider implements InfrastructureProvider {

	// TODO: put in resource the user, token and localCommand
	private static final Logger LOGGER = Logger.getLogger(FogbowInfrastructureProvider.class);

	private static final int MEMORY_1Gbit = 1024;

	private static final String NULL_VALUE = "null";
	private static final String CATEGORY = "Category";
	private static final String X_OCCI_ATTRIBUTE = "X-OCCI-Attribute";
	private static final User DEFAULT_USER = new Token.User("9999", "User");

	public static final String REQUEST_ATTRIBUTE_MEMBER_ID = "org.fogbowcloud.order.providing-member";

	public static final String INSTANCE_ATTRIBUTE_SSH_PUBLIC_ADDRESS_ATT = "org.fogbowcloud.order.ssh-public-address";
	public static final String INSTANCE_ATTRIBUTE_SSH_USERNAME_ATT = "org.fogbowcloud.order.ssh-username";
	public static final String INSTANCE_ATTRIBUTE_EXTRA_PORTS_ATT = "org.fogbowcloud.order.extra-ports";
	public static final String INSTANCE_ATTRIBUTE_MEMORY_SIZE = "occi.compute.memory";
	public static final String INSTANCE_ATTRIBUTE_VCORE = "occi.compute.cores";

	// TODO: alter when fogbow are returning this attribute
	public static final String INSTANCE_ATTRIBUTE_DISKSIZE = "TODO-AlterWhenFogbowReturns";
	public static final String INSTANCE_ATTRIBUTE_REQUEST_TYPE = "org.fogbowcloud.order.type";

	private HttpWrapper httpWrapper;
	private String managerUrl;
	private Token token;
	private Properties properties;
	private AbstractTokenUpdatePlugin tokenUpdatePlugin;
	private FogbowResourceDatastore frDatastore;

	private Map<String, FogbowResource> resourcesMap = new ConcurrentHashMap<String, FogbowResource>();

	protected FogbowInfrastructureProvider(Properties properties,
			ScheduledExecutorService handleTokeUpdateExecutor, boolean cleanPrevious)
			throws Exception {

		this(properties, handleTokeUpdateExecutor, createTokenUpdatePlugin(properties));

		this.frDatastore = new FogbowResourceDatastore(properties);
		for (FogbowResource fogbowResource : this.frDatastore.getAllFogbowResources()) {

			this.resourcesMap.put(fogbowResource.getId(), fogbowResource);

			if (cleanPrevious) {
				try {
					this.deleteResource(fogbowResource.getId());
				} catch (Exception e) {
					LOGGER.error("Error while trying to delete resource on initialization: "
							+ fogbowResource.getId());
				}
			}

		}
	}

	protected FogbowInfrastructureProvider(Properties properties,
			ScheduledExecutorService handleTokeUpdateExecutor,
			AbstractTokenUpdatePlugin tokenUpdatePlugin) throws Exception {

		this.httpWrapper = new HttpWrapper();
		this.properties = properties;
		this.managerUrl = properties
				.getProperty(AppPropertiesConstants.INFRA_FOGBOW_MANAGER_BASE_URL);
		this.tokenUpdatePlugin = tokenUpdatePlugin;
		this.token = tokenUpdatePlugin.generateToken();

		ScheduledExecutorService handleTokenUpdateExecutor = handleTokeUpdateExecutor;
		this.handleTokenUpdate(handleTokenUpdateExecutor);
	}

	public FogbowInfrastructureProvider(Properties properties, boolean removePrevious)
			throws Exception {
		this(properties, Executors.newScheduledThreadPool(1), removePrevious);
	}

	protected void handleTokenUpdate(ScheduledExecutorService handleTokenUpdateExecutor) {
		LOGGER.debug("Turning on handle token update.");

		// TODO: Could be a better strategy call this method from
		// InfrastructureManager on its main service thread ?
		// The main thread could check if the token has expired and so call this
		// method.
		handleTokenUpdateExecutor.scheduleWithFixedDelay(new Runnable() {
			@Override
			public void run() {
				setToken(tokenUpdatePlugin.generateToken());
			}
		}, this.tokenUpdatePlugin.getUpdateTime(), this.tokenUpdatePlugin.getUpdateTime(),
				this.tokenUpdatePlugin.getUpdateTimeUnits());
	}

	@Override
	public String requestResource(Specification spec) throws RequestResourceException {

		LOGGER.debug("Requesting resource on Fogbow with specifications: " + spec.toString());

		String requestInformation;

		try {
			this.validateSpecification(spec);

			List<Header> headers = (LinkedList<Header>) this.requestNewInstanceHeaders(spec);
			LOGGER.debug("Headers: " + headers.toString());

			String requestMethod = "post";
			requestInformation = this.doRequest(requestMethod,
					this.managerUrl + "/" + OrderConstants.TERM, headers);

		} catch (Exception e) {
			LOGGER.error("Error while requesting resource on Fogbow", e);
			throw new RequestResourceException(
					"Request for Fogbow Resource has FAILED: " + e.getMessage(), e);
		}

		String orderId = getOrderId(requestInformation);
		String resourceId = String.valueOf(UUID.randomUUID());

		FogbowResource fogbowResource = new FogbowResource(resourceId, orderId, spec);
		this.insertResourceSpecifications(spec, fogbowResource);

		this.resourcesMap.put(resourceId, fogbowResource);
		this.frDatastore.addFogbowResource(fogbowResource);

		LOGGER.debug("Request for Fogbow Resource was Successful. Resource ID: [" + resourceId
				+ "] Order ID: [" + orderId + "]");
		return fogbowResource.getId();
	}

	@Override
	public AbstractResource getResource(String resourceId) {

		LOGGER.debug("Getting resource from request id: [" + resourceId + "]");
		try {
			FogbowResource resource = this.getFogbowResource(resourceId);

			if (resource != null) {
				LOGGER.debug("Returning Resource from Resource id: [" + resourceId
						+ "] - Instance ID : [" + resource.getInstanceId() + "]");
			} else {
				LOGGER.warn("Still can not get Resource from Resource id: [" + resourceId + "]");
			}

			return resource;
		} catch (Exception e) {
			LOGGER.error("Error while getting resource with id: [" + resourceId + "] ", e);
			return null;
		}
	}

	private FogbowResource getFogbowResource(String resourceId) throws Exception {

		LOGGER.debug("Initiating Resource Instanciation - Resource id: [" + resourceId + "]");
		String instanceId;
		Map<String, String> requestAttributes;

		FogbowResource fogbowResource = this.resourcesMap.get(resourceId);

		if (fogbowResource == null) {
			throw new InfrastructureException(
					"The resource is not a valid. Was never requested or is already deleted");
		}

		LOGGER.debug("Getting request attributes - Retrieve Instance ID.");

		requestAttributes = this.getFogbowRequestAttributes(fogbowResource.getOrderId());

		instanceId = requestAttributes.get(OrderAttribute.INSTANCE_ID.getValue());

		if (instanceId != null && !instanceId.trim().isEmpty()) { 
			LOGGER.debug("Instance ID returned: " + instanceId);

			Map<String, String> instanceAttributes = getFogbowInstanceAttributes(instanceId);

			if (this.validateInstanceAttributes(instanceAttributes)) {

				fogbowResource.setInstanceId(instanceId);

				LOGGER.debug("Getting Instance attributes.");

				this.insertResourceMetadata(fogbowResource, requestAttributes, instanceAttributes);

				// TODO: Make fogbow return these attributes:
				// newResource.putMetadata(Resource.METADATA_DISK_SIZE and
				// instanceAttributes.get(INSTANCE_ATTRIBUTE_DISKSIZE));

				LOGGER.debug("New Fogbow Resource created - Instance ID: [" + instanceId + "]");

				this.frDatastore.updateFogbowResource(fogbowResource);
				return fogbowResource;

			} else {
				LOGGER.debug(
						"Instance attributes not yet ready for instance: [" + instanceId + "]");
			}
		}

		return null;
	}

	@Override
	public List<AbstractResource> getAllResources() {
		return new ArrayList<AbstractResource>(this.resourcesMap.values());
	}

	@Override
	public void deleteResource(String resourceId) throws InfrastructureException {

		FogbowResource fogbowResource = this.resourcesMap.get(resourceId);

		if (fogbowResource == null) {
			throw new InfrastructureException(
					"The resource is not a valid. Was never requested or is already deleted");
		}

		LOGGER.debug("Deleting resource with ID = " + fogbowResource.getId());

		try {
			String requestMethod = "delete";

			if (fogbowResource.getInstanceId() != null) {
				this.doRequest(requestMethod,
						this.managerUrl + "/compute/" + fogbowResource.getInstanceId(),
						new ArrayList<Header>());
			}

			this.doRequest(requestMethod,
					this.managerUrl + "/" + OrderConstants.TERM + "/" + fogbowResource.getOrderId(),
					new ArrayList<Header>());

			this.resourcesMap.remove(resourceId);
			this.frDatastore.deleteFogbowResourceById(fogbowResource);

			LOGGER.debug("Resource " + fogbowResource.getId() + " deleted successfully");
		} catch (Exception e) {
			throw new InfrastructureException(
					"Error when trying to delete resource id[" + fogbowResource.getId() + "]", e);
		}
	}

	protected Token createNewTokenFromFile(String certificateFilePath)
			throws FileNotFoundException, IOException {

		String certificate = IOUtils.toString(new FileInputStream(certificateFilePath))
				.replaceAll("\n", "");
		Date date = new Date(System.currentTimeMillis() + (long) Math.pow(10, 9));

		return new Token(certificate, DEFAULT_USER, date, new HashMap<String, String>());
	}

	private Map<String, String> getFogbowRequestAttributes(String orderId) throws Exception {

		String requestMethod = "get";
		String endpoint = this.managerUrl + "/" + OrderConstants.TERM + "/" + orderId;
		String requestResponse = this.doRequest(requestMethod, endpoint, new ArrayList<Header>());

		Map<String, String> attrs = this.parseRequestAttributes(requestResponse);
		return attrs;
	}

	private Map<String, String> getFogbowInstanceAttributes(String instanceId) throws Exception {

		String requestMethod = "get";
		String endpoint = this.managerUrl + "/compute/" + instanceId;
		String instanceInformation = this.doRequest(requestMethod, endpoint,
				new ArrayList<Header>());

		Map<String, String> attrs = this.parseAttributes(instanceInformation);
		return attrs;
	}

	private void validateSpecification(Specification specification)
			throws RequestResourceException {

		if (specification.getImage() == null || specification.getImage().trim().isEmpty()) {
			throw new RequestResourceException("Resource image can not be null or empty");
		}
		if (specification.getPublicKey() == null || specification.getPublicKey().trim().isEmpty()) {
			throw new RequestResourceException("Public key can not be null or empty");
		}

		String fogbowRequirements = specification
				.getRequirementValue(FogbowRequirementsHelper.METADATA_FOGBOW_REQUIREMENTS);

		if (!FogbowRequirementsHelper.validateFogbowRequirementsSyntax(fogbowRequirements)) {
			LOGGER.debug("FogbowRequirements [" + fogbowRequirements
					+ "] is not in valid format. e.g: [Glue2vCPU >= 1 && Glue2RAM >= 1024 && Glue2disk >= 20 && Glue2CloudComputeManagerID ==\"servers.your.domain\"]");
			throw new RequestResourceException("FogbowRequirements [" + fogbowRequirements
					+ "] is not in valid format. e.g: [Glue2vCPU >= 1 && Glue2RAM >= 1024 && Glue2disk >= 20 && Glue2CloudComputeManagerID ==\"servers.your.domain\"]");
		}
	}

	private List<Header> requestNewInstanceHeaders(Specification specs) {
		String fogbowImage = specs.getImage();
		String fogbowRequirements = specs
				.getRequirementValue(FogbowRequirementsHelper.METADATA_FOGBOW_REQUIREMENTS);
		String fogbowRequestType = specs
				.getRequirementValue(FogbowRequirementsHelper.METADATA_FOGBOW_REQUEST_TYPE);

		List<Header> headers = new LinkedList<Header>();
		headers.add(new BasicHeader(CATEGORY, OrderConstants.TERM + "; scheme=\""
				+ OrderConstants.SCHEME + "\"; class=\"" + OrderConstants.KIND_CLASS + "\""));
		headers.add(new BasicHeader(X_OCCI_ATTRIBUTE,
				OrderAttribute.INSTANCE_COUNT.getValue() + "=" + 1));
		headers.add(new BasicHeader(X_OCCI_ATTRIBUTE,
				OrderAttribute.TYPE.getValue() + "=" + fogbowRequestType));

		headers.add(new BasicHeader(CATEGORY,
				fogbowImage + "; scheme=\"" + OrderConstants.TEMPLATE_OS_SCHEME + "\"; class=\""
						+ OrderConstants.MIXIN_CLASS + "\""));

		headers.add(new BasicHeader(X_OCCI_ATTRIBUTE,
				OrderAttribute.REQUIREMENTS.getValue() + "=" + fogbowRequirements));

		if (specs.getUserDataFile() != null && !specs.getUserDataFile().trim().isEmpty()) {
			try {
				String userDataContent = getFileContent(specs.getUserDataFile());
				String userData = userDataContent.replace("\n",
						UserdataUtils.USER_DATA_LINE_BREAKER);
				userData = new String(Base64.encodeBase64(userData.getBytes()));
				headers.add(new BasicHeader("X-OCCI-Attribute",
						OrderAttribute.EXTRA_USER_DATA_ATT.getValue() + "=" + userData));
				headers.add(new BasicHeader("X-OCCI-Attribute",
						OrderAttribute.EXTRA_USER_DATA_CONTENT_TYPE_ATT.getValue() + "="
								+ specs.getUserDataType()));
			} catch (IOException e) {
				LOGGER.error("User data file not found.", e);
				return null;
			}
		}

		headers.add(new BasicHeader(X_OCCI_ATTRIBUTE,
				OrderAttribute.RESOURCE_KIND.getValue() + "=" + "compute"));
		if (specs.getPublicKey() != null && !specs.getPublicKey().isEmpty()) {
			headers.add(new BasicHeader(CATEGORY,
					OrderConstants.PUBLIC_KEY_TERM + "; scheme=\""
							+ OrderConstants.CREDENTIALS_RESOURCE_SCHEME + "\"; class=\""
							+ OrderConstants.MIXIN_CLASS + "\""));
			headers.add(new BasicHeader(X_OCCI_ATTRIBUTE,
					OrderAttribute.DATA_PUBLIC_KEY.getValue() + "=" + specs.getPublicKey()));
		}

		headers.add(new BasicHeader("X-OCCI-Attribute", OrderAttribute.RESOURCE_KIND.getValue()
				+ "=" + FogbowRequirementsHelper.METADATA_FOGBOW_RESOURCE_KIND));

		return headers;
	}

	protected static String getFileContent(String path) throws IOException {
		BufferedReader fileStream = null;
		try {
			fileStream = new BufferedReader(new FileReader(path));
			String fileContent = "";
			String line = null;

			do {
				line = fileStream.readLine();
				if (line != null) {
					fileContent += line + System.lineSeparator();
				}
			} while (line != null);

			return fileContent.trim();

		} catch (IOException e) {
			LOGGER.error("Error while trying to open the file at: " + path, e);
			return null;
		} finally {
			if (fileStream != null) {
				fileStream.close();
			}
		}
	}

	private String doRequest(String method, String endpoint, List<Header> headers)
			throws Exception {
		return this.httpWrapper.doRequest(method, endpoint, this.token.getAccessId(), headers);
	}

	protected String getOrderId(String requestInformation) {
		String[] requestRes = requestInformation.split(":");
		String[] requestId = requestRes[requestRes.length - 1].split("/");
		return requestId[requestId.length - 1];
	}

	private boolean validateInstanceAttributes(Map<String, String> instanceAttributes) {

		LOGGER.debug("Validating instance attributes.");

		boolean isValid = true;

		if (instanceAttributes != null && !instanceAttributes.isEmpty()) {

			String sshInformation = instanceAttributes
					.get(INSTANCE_ATTRIBUTE_SSH_PUBLIC_ADDRESS_ATT);
			String vcore = instanceAttributes.get(INSTANCE_ATTRIBUTE_VCORE);
			String memorySize = instanceAttributes.get(INSTANCE_ATTRIBUTE_MEMORY_SIZE);

			// If any of these attributes are empty, then return invalid.
			// TODO: add to "isStringEmpty diskSize and memberId when fogbow
			// being returning this two attributes.
			isValid = !AppUtil.isStringEmpty(sshInformation, vcore, memorySize);
			if (!isValid) {
				LOGGER.debug("Instance attributes invalids.");
			} else {
				String[] addressInfo = sshInformation.split(":");
				if (addressInfo != null && addressInfo.length > 1) {
					String host = addressInfo[0];
					String port = addressInfo[1];
					isValid = !AppUtil.isStringEmpty(host, port);
				} else {
					LOGGER.debug("Instance attributes invalids.");
					isValid = false;
				}
			}

		} else {
			LOGGER.debug("Instance attributes invalids.");
			isValid = false;
		}

		return isValid;
	}

	private Map<String, String> parseRequestAttributes(String response) {
		Map<String, String> atts = new HashMap<String, String>();
		for (String responseLine : response.split("\n")) {
			if (responseLine.contains(X_OCCI_ATTRIBUTE + ": ")) {
				String[] responseLineSplit = responseLine
						.substring((X_OCCI_ATTRIBUTE + ": ").length()).split("=");
				String valueStr = responseLineSplit[1].trim().replace("\"", "");
				if (!valueStr.equals(NULL_VALUE)) {
					atts.put(responseLineSplit[0].trim(), valueStr);
				}
			}
		}
		return atts;
	}

	private Map<String, String> parseAttributes(String response) {
		Map<String, String> atts = new HashMap<String, String>();
		for (String responseLine : response.split("\n")) {
			if (responseLine.contains(X_OCCI_ATTRIBUTE + ": ")) {
				String[] responseLineSplit = responseLine
						.substring((X_OCCI_ATTRIBUTE + ": ").length()).split("=");
				String valueStr = responseLineSplit[1].trim().replace("\"", "");
				if (!valueStr.equals(NULL_VALUE)) {
					atts.put(responseLineSplit[0].trim(), valueStr);
				}
			}
		}
		return atts;
	}

	private static AbstractTokenUpdatePlugin createTokenUpdatePlugin(Properties properties)
			throws Exception {

		String tokenClassName = properties
				.getProperty(AppPropertiesConstants.INFRA_AUTH_TOKEN_UPDATE_PLUGIN);

		Object tokenClass = Class.forName(tokenClassName).getConstructor(Properties.class)
				.newInstance(properties);
		if (!(tokenClass instanceof AbstractTokenUpdatePlugin)) {
			throw new Exception(
					"Provider Class Name is not a TokenUpdatePluginInterface implementation");
		}
		AbstractTokenUpdatePlugin tokenUpdatePlugin = (AbstractTokenUpdatePlugin) tokenClass;
		tokenUpdatePlugin.validateProperties();
		return tokenUpdatePlugin;
	}

	private void insertResourceSpecifications(Specification spec, FogbowResource fogbowResource) {
		String requestType = spec
				.getRequirementValue(FogbowRequirementsHelper.METADATA_FOGBOW_REQUEST_TYPE);
		fogbowResource.putMetadata(AbstractResource.METADATA_REQUEST_TYPE, requestType);
		fogbowResource.putMetadata(AbstractResource.METADATA_IMAGE, spec.getImage());
		fogbowResource.putMetadata(AbstractResource.METADATA_PUBLIC_KEY, spec.getPublicKey());
	}

	private void insertResourceMetadata(FogbowResource fogbowResource,
			Map<String, String> requestAttributes, Map<String, String> instanceAttributes) {

		String sshInformation = instanceAttributes.get(INSTANCE_ATTRIBUTE_SSH_PUBLIC_ADDRESS_ATT);

		String[] addressInfo = sshInformation.split(":");
		String host = addressInfo[0];
		String port = addressInfo[1];

		fogbowResource.setLocalCommandInterpreter(
				this.properties.getProperty(AppPropertiesConstants.LOCAL_COMMAND_INTERPRETER));
		fogbowResource.putMetadata(AbstractResource.METADATA_SSH_HOST, host);
		fogbowResource.putMetadata(AbstractResource.METADATA_SSH_PORT, port);
		fogbowResource.putMetadata(AbstractResource.METADATA_SSH_USERNAME_ATT,
				instanceAttributes.get(INSTANCE_ATTRIBUTE_SSH_USERNAME_ATT));
		fogbowResource.putMetadata(AbstractResource.METADATA_EXTRA_PORTS_ATT,
				instanceAttributes.get(INSTANCE_ATTRIBUTE_EXTRA_PORTS_ATT));
		fogbowResource.putMetadata(AbstractResource.METADATA_VCPU,
				instanceAttributes.get(INSTANCE_ATTRIBUTE_VCORE));
		float menSize = Float.parseFloat(instanceAttributes.get(INSTANCE_ATTRIBUTE_MEMORY_SIZE));
		String menSizeFormated = String.valueOf(menSize * MEMORY_1Gbit);
		fogbowResource.putMetadata(AbstractResource.METADATA_MEN_SIZE, menSizeFormated);
		fogbowResource.putMetadata(AbstractResource.METADATA_LOCATION,
				"\"" + requestAttributes.get(REQUEST_ATTRIBUTE_MEMBER_ID) + "\"");
	}

	public HttpWrapper getHttpWrapper() {
		return this.httpWrapper;
	}

	public void setHttpWrapper(HttpWrapper httpWrapper) {
		this.httpWrapper = httpWrapper;
	}

	public String getManagerUrl() {
		return this.managerUrl;
	}

	public void setManagerUrl(String managerUrl) {
		this.managerUrl = managerUrl;
	}

	public Token getToken() {
		return this.token;
	}

	protected void setToken(Token token) {
		this.token = token;
	}

	protected Map<String, FogbowResource> getResourcesMap() {
		return this.resourcesMap;
	}

	protected void setResourcesMap(Map<String, FogbowResource> resourcesMap) {
		this.resourcesMap = resourcesMap;
	}

	protected FogbowResourceDatastore getFrDatastore() {
		return this.frDatastore;
	}

	protected void setFrDatastore(FogbowResourceDatastore frDatastore) {
		this.frDatastore = frDatastore;
	}

}