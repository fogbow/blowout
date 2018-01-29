package org.fogbowcloud.blowout.infrastructure.model;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.fogbowcloud.blowout.core.model.Specification;
import org.fogbowcloud.blowout.core.util.AppPropertiesConstants;

public abstract class AbstractResource {

	public static final String ENV_HOST = "HOST";
	public static final String ENV_SSH_PORT = "SSH_PORT";
	public static final String ENV_SSH_USER = "SSH_USER";
	public static final String ENV_PRIVATE_KEY_FILE = "PRIVATE_KEY_FILE";

	public static final String METADATA_TOKEN_USER = "tokenUser";
	public static final String METADATA_SU_COMMAND_PATH = "metadataSuCommandPath";

	public static final String METADATA_SSH_HOST = "metadataSSHHost";
	public static final String METADATA_SSH_PORT = "metadataSSHPort";
	public static final String METADATA_SSH_USERNAME_ATT = "metadateSshUsername";
	public static final String METADATA_EXTRA_PORTS_ATT = "metadateExtraPorts";

	public static final String METADATA_IMAGE = "metadataImage";
	public static final String METADATA_PUBLIC_KEY = "metadataPublicKey";

	public static final String METADATA_VCPU = "metadataVcpu";
	public static final String METADATA_MEN_SIZE = "metadataMenSize";
	public static final String METADATA_DISK_SIZE = "metadataDiskSize";
	public static final String METADATA_LOCATION = "metadataLocation";

	public static final String METADATA_REQUEST_TYPE = "metadataRequestType";

	private ResourceState state = ResourceState.NOT_READY;

	private String id;
	private Map<String, String> metadata = new HashMap<String, String>();
	private int timesReused;
	private int connectionFailTries;
	private String localCommandInterpreter;
	private Specification requestedSpec;

	public AbstractResource(String id, Specification requestedSpec) {
		this.id = id;
		this.timesReused = 0;
		this.connectionFailTries = 0;
		this.requestedSpec = requestedSpec;
		this.localCommandInterpreter = requestedSpec
				.getRequirementValue(AppPropertiesConstants.SU_COMMAND_PATH);
		this.setState(ResourceState.NOT_READY);
	}

	public abstract boolean match(Specification spec);

	protected abstract boolean internalCheckConnectivity();

	public boolean checkConnectivity() {

		boolean success = this.internalCheckConnectivity();
		if(success) {
			this.connectionFailTries = 0;
		} else {
			this.connectionFailTries++;
		}
		return success;
	}

	public void putMetadata(String attributeName, String value) {
		this.metadata.put(attributeName, value);
	}

	public void putAllMetadatas(Map<String, String> instanceAttributes) {
		for (Entry<String, String> entry : instanceAttributes.entrySet()) {
			this.putMetadata(entry.getKey(), entry.getValue());
		}
	}

	public String getMetadataValue(String attributeName) {
		return this.metadata.get(attributeName);
	}

	public Map<String, String> getAllMetadata() {
		return this.metadata;
	}

	public void copyInformations(AbstractResource resource) {
		this.metadata.clear();
		this.metadata.putAll(resource.getAllMetadata());
	}

	public String getId() {
		return this.id;
	}

	public void incrementReuse() {
		this.timesReused++;
	}

	public int getReusedTimes() {
		return this.timesReused;
	}

	public int getConnectionFailTries() {
		return this.connectionFailTries;
	}

	public ResourceState getState() {
		return this.state;
	}

	public synchronized void setState(ResourceState state) {
		this.state = state;
	}

	public String getLocalCommandInterpreter() {
		return this.localCommandInterpreter;
	}

	public void setLocalCommandInterpreter(String localCommandInterpreter) {
		this.localCommandInterpreter = localCommandInterpreter;
	}

	public Specification getRequestedSpec() {
		return this.requestedSpec;
	}

	public void setRequestedSpec(Specification requestedSpec) {
		this.requestedSpec = requestedSpec;
	}

}
