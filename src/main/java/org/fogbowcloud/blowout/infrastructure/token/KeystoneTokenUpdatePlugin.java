package org.fogbowcloud.blowout.infrastructure.token;

import java.util.HashMap;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.fogbowcloud.blowout.core.exception.BlowoutException;
import org.fogbowcloud.blowout.core.util.AppPropertiesConstants;
import org.fogbowcloud.manager.core.plugins.identity.openstackv2.KeystoneIdentityPlugin;
import org.fogbowcloud.manager.occi.model.Token;

public class KeystoneTokenUpdatePlugin extends AbstractTokenUpdatePlugin {

	private static final Logger LOGGER = Logger.getLogger(KeystoneTokenUpdatePlugin.class);

	private static final String FOGBOW_KEYSTONE_USERNAME = AppPropertiesConstants.INFRA_AUTH_TOKEN_PREFIX
			+ "keystone_username";
	private static final String FOGBOW_KEYSTONE_TENANTNAME = AppPropertiesConstants.INFRA_AUTH_TOKEN_PREFIX
			+ "keystone_tenantname";
	private static final String FOGBOW_KEYSTONE_PASSWORD = AppPropertiesConstants.INFRA_AUTH_TOKEN_PREFIX
			+ "keystone_password";
	private static final String FOGBOW_KEYSTONE_AUTH_URL = AppPropertiesConstants.INFRA_AUTH_TOKEN_PREFIX
			+ "keystone_auth_url";

	private final String username;
	private final String tenantname;
	private final String password;
	private final String authUrl;
	private Properties properties;

	public KeystoneTokenUpdatePlugin(Properties properties) {

		super(properties);

		this.properties = properties;

		this.username = properties.getProperty(FOGBOW_KEYSTONE_USERNAME);
		this.tenantname = properties.getProperty(FOGBOW_KEYSTONE_TENANTNAME);
		this.password = properties.getProperty(FOGBOW_KEYSTONE_PASSWORD);
		this.authUrl = properties.getProperty(FOGBOW_KEYSTONE_AUTH_URL);

		// bash bin/fogbow-cli token --create --type openstack -Dusername=fogbow
		// -Dpassword=nc3SRPS2
		// -DauthUrl=http://10.5.0.14:5000-DtenantName=Fogbow
	}

	@Override
	public Token generateToken() {

		try {
			return createToken(this.properties);
		} catch (Throwable e) {
			LOGGER.error("Error while setting token.", e);
		}
		return null;
	}

	protected Token createToken() {
		return createToken(new Properties());
	}

	protected Token createToken(Properties properties) {
		KeystoneIdentityPlugin keystoneIdentityPlugin = new KeystoneIdentityPlugin(properties);

		HashMap<String, String> credentials = new HashMap<String, String>();

		credentials.put(KeystoneIdentityPlugin.AUTH_URL, authUrl);
		credentials.put(KeystoneIdentityPlugin.USERNAME, username);
		credentials.put(KeystoneIdentityPlugin.PASSWORD, password);
		credentials.put(KeystoneIdentityPlugin.TENANT_NAME, tenantname);
		LOGGER.debug("Creating token update with USERNAME=" + username + " and PASSWORD=" + password);

		Token token = keystoneIdentityPlugin.createToken(credentials);
		LOGGER.debug("Keystone cert updated. New cert is " + token.toString());

		return token;
	}

	@Override
	public void validateProperties() throws BlowoutException {
		if (!properties.containsKey(FOGBOW_KEYSTONE_USERNAME)) {
			throw new BlowoutException("Required property " + FOGBOW_KEYSTONE_USERNAME + " was not set");
		}
		if (!properties.containsKey(FOGBOW_KEYSTONE_TENANTNAME)) {
			throw new BlowoutException("Required property " + FOGBOW_KEYSTONE_TENANTNAME + " was not set");
		}
		if (!properties.containsKey(FOGBOW_KEYSTONE_PASSWORD)) {
			throw new BlowoutException("Required property " + FOGBOW_KEYSTONE_PASSWORD + " was not set");
		}
		if (!properties.containsKey(FOGBOW_KEYSTONE_AUTH_URL)) {
			throw new BlowoutException("Required property " + FOGBOW_KEYSTONE_AUTH_URL + " was not set");
		}
	}
}
