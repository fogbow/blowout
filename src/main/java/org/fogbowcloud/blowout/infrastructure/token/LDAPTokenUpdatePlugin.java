package org.fogbowcloud.blowout.infrastructure.token;

import java.util.HashMap;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.fogbowcloud.blowout.core.exception.BlowoutException;
import org.fogbowcloud.blowout.core.util.AppPropertiesConstants;
import org.fogbowcloud.manager.core.plugins.identity.ldap.LdapIdentityPlugin;
import org.fogbowcloud.manager.occi.model.Token;

public class LDAPTokenUpdatePlugin extends AbstractTokenUpdatePlugin {

	private static final Logger LOGGER = Logger.getLogger(LDAPTokenUpdatePlugin.class);
	private static final String LDAP_USERNAME = "fogbow.ldap.username";
	private static final String LDAP_PASSWORD = "fogbow.ldap.password";
	private static final String LDAP_AUTH_URL = "fogbow.ldap.auth.url";
	private static final String LDAP_BASE = "fogbow.ldap.base";
	private static final String LDAP_ENCRYPT_TYPE = AppPropertiesConstants.INFRA_AUTH_TOKEN_PREFIX
			+ "ldap_encrypt_type";
	private static final String LDAP_PRIVATE_KEY = "fogbow.ldap.private.key";
	private static final String LDAP_PUBLIC_KEY = "fogbow.ldap.public.key";

	private String userName;
	private String password;
	private String ldapUrl;
	private String ldapBase;
	private String encrypt;
	private String privateKeyPath;
	private String publicKeyPath;

	public LDAPTokenUpdatePlugin(Properties properties) {
		super(properties);

		this.userName = properties.getProperty(LDAP_USERNAME);
		this.password = properties.getProperty(LDAP_PASSWORD);
		this.ldapUrl = properties.getProperty(LDAP_AUTH_URL);
		this.ldapBase = properties.getProperty(LDAP_BASE);
		this.encrypt = properties.getProperty(LDAP_ENCRYPT_TYPE);
		this.privateKeyPath = properties.getProperty(LDAP_PRIVATE_KEY);
		this.publicKeyPath = properties.getProperty(LDAP_PUBLIC_KEY);
	}

	@Override
	public Token generateToken() {

		LdapIdentityPlugin ldapIdentityPlugin = new LdapIdentityPlugin(new Properties());

		HashMap<String, String> credentials = new HashMap<String, String>();

		credentials.put(LdapIdentityPlugin.CRED_USERNAME, this.userName);
		credentials.put(LdapIdentityPlugin.CRED_PASSWORD, this.password);
		credentials.put(LdapIdentityPlugin.CRED_AUTH_URL, this.ldapUrl);
		credentials.put(LdapIdentityPlugin.CRED_LDAP_BASE, this.ldapBase);
		credentials.put(LdapIdentityPlugin.CRED_LDAP_ENCRYPT, this.encrypt);
		credentials.put(LdapIdentityPlugin.CRED_PRIVATE_KEY, this.privateKeyPath);
		credentials.put(LdapIdentityPlugin.CRED_PUBLIC_KEY, this.publicKeyPath);

		LOGGER.debug("Creating token update with USERNAME=" + this.userName + " and PASSWORD="
				+ this.password);

		Token token = ldapIdentityPlugin.createToken(credentials);
		LOGGER.debug("LDAP token updated. New token is " + token.toString());

		return token;
	}

	@Override
	public void validateProperties() throws BlowoutException {
		Properties properties = super.getProperties();
		
		if (!properties.containsKey(LDAP_USERNAME)) {
			throw new BlowoutException("Required property " + LDAP_USERNAME + " was not set");
		}
		if (!properties.containsKey(LDAP_PASSWORD)) {
			throw new BlowoutException("Required property " + LDAP_PASSWORD + " was not set");
		}
		if (!properties.containsKey(LDAP_AUTH_URL)) {
			throw new BlowoutException("Required property " + LDAP_AUTH_URL + " was not set");
		}
		if (!properties.containsKey(LDAP_BASE)) {
			throw new BlowoutException("Required property " + LDAP_BASE + " was not set");
		}
		if (!properties.containsKey(LDAP_ENCRYPT_TYPE)) {
			throw new BlowoutException("Required property " + LDAP_ENCRYPT_TYPE + " was not set");
		}
		if (!properties.containsKey(LDAP_PRIVATE_KEY)) {
			throw new BlowoutException("Required property " + LDAP_PRIVATE_KEY + " was not set");
		}
		if (!properties.containsKey(LDAP_PUBLIC_KEY)) {
			throw new BlowoutException("Required property " + LDAP_PUBLIC_KEY + " was not set");
		}
	}
}
