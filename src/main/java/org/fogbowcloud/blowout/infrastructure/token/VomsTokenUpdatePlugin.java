package org.fogbowcloud.blowout.infrastructure.token;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Properties;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.fogbowcloud.blowout.core.exception.BlowoutException;
import org.fogbowcloud.blowout.core.util.AppPropertiesConstants;
import org.fogbowcloud.manager.core.plugins.identity.voms.VomsIdentityPlugin;
import org.fogbowcloud.manager.occi.model.Token;

public class VomsTokenUpdatePlugin extends AbstractTokenUpdatePlugin {

	private static final String VOMS_CERTIFICATE_FILE = AppPropertiesConstants.INFRA_AUTH_TOKEN_PREFIX
			+ "voms_certificate_file_path";
	private static final String VOMS_CERTIFICATE_PASSWORD = AppPropertiesConstants.INFRA_AUTH_TOKEN_PREFIX
			+ "voms_certificate_password";
	private static final String VOMS_SERVER = AppPropertiesConstants.INFRA_AUTH_TOKEN_PREFIX
			+ "voms_server";
	private static final Logger LOGGER = Logger.getLogger(VomsTokenUpdatePlugin.class);
	private static final Token.User DEFAULT_USER = new Token.User("9999", "User");

	private final String vomsServer;
	private final String password;

	public VomsTokenUpdatePlugin(Properties properties) {
		super(properties);
		this.vomsServer = properties.getProperty(VOMS_SERVER);
		this.password = properties.getProperty(VOMS_CERTIFICATE_PASSWORD);
	}

	@Override
	public Token generateToken() {

		try {
			return createToken();
		} catch (Throwable e) {
			LOGGER.error("Error while setting token.", e);
			try {
				return createNewTokenFromFile(
						super.getProperties().getProperty(VOMS_CERTIFICATE_FILE));
			} catch (IOException e1) {
				LOGGER.error("Error while getting token from file.", e);
			}
		}

		return null;
	}

	protected Token createToken() {
		VomsIdentityPlugin vomsIdentityPlugin = new VomsIdentityPlugin(new Properties());

		HashMap<String, String> credentials = new HashMap<String, String>();
		credentials.put("password", this.password);
		credentials.put("serverName", this.vomsServer);
		LOGGER.debug("Creating token update with serverName=" + this.vomsServer + " and password="
				+ this.password);

		Token token = vomsIdentityPlugin.createToken(credentials);
		LOGGER.debug("VOMS proxy updated. New proxy is " + token.toString());

		return token;
	}

	protected Token createNewTokenFromFile(String certificateFilePath)
			throws FileNotFoundException, IOException {

		String certificate = IOUtils.toString(new FileInputStream(certificateFilePath))
				.replaceAll("\n", "");
		Date date = new Date(System.currentTimeMillis() + (long) Math.pow(10, 9));

		return new Token(certificate, DEFAULT_USER, date, new HashMap<String, String>());
	}

	@Override
	public void validateProperties() throws BlowoutException {
		Properties properties = super.getProperties();

		if (!properties.containsKey(VOMS_CERTIFICATE_FILE)) {
			throw new BlowoutException(
					"Required property " + VOMS_CERTIFICATE_FILE + " was not set");
		}

		if (!properties.containsKey(VOMS_CERTIFICATE_PASSWORD)) {
			throw new BlowoutException(
					"Required property " + VOMS_CERTIFICATE_PASSWORD + " was not set");
		}

		if (!properties.containsKey(VOMS_SERVER)) {
			throw new BlowoutException("Required property " + VOMS_SERVER + " was not set");
		}
	}

}
