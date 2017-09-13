package org.fogbowcloud.blowout.infrastructure.token;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.io.IOUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.fogbowcloud.blowout.core.exception.BlowoutException;
import org.fogbowcloud.blowout.core.util.AppPropertiesConstants;
import org.fogbowcloud.manager.core.plugins.identity.naf.NAFIdentityPlugin;
import org.fogbowcloud.manager.occi.model.Token;

public class NAFTokenUpdatePlugin extends AbstractTokenUpdatePlugin {
	
	private static final String USERNAME_PARAMETER = "username";
	private static final String PASSWORD_PARAMETER = "password";
	public static final String NAF_IDENTITY_PRIVATE_KEY = AppPropertiesConstants.INFRA_AUTH_TOKEN_PREFIX
			+ "naf_identity_private_key";
	public static final String NAF_IDENTITY_PUBLIC_KEY = AppPropertiesConstants.INFRA_AUTH_TOKEN_PREFIX
			+ "naf_identity_private_key";
	public static final String NAF_IDENTITY_TOKEN_USERNAME = AppPropertiesConstants.INFRA_AUTH_TOKEN_PREFIX
			+ "naf_identity_token_username";
	public static final String NAF_IDENTITY_TOKEN_PASSWORD = AppPropertiesConstants.INFRA_AUTH_TOKEN_PREFIX
			+ "naf_identity_token_password";
	public static final String NAF_IDENTITY_TOKEN_GENERATOR_URL = AppPropertiesConstants.INFRA_AUTH_TOKEN_PREFIX
			+ "naf_identity_token_generator_endpoint";
	private static final String HOUR_PARAMETER = "hour";

	private Properties properties;

	public NAFTokenUpdatePlugin(Properties properties) {
		super(properties);
		this.properties = properties;
	}

	@Override
	public Token generateToken() {
		try {
			NAFIdentityPlugin nafIdentityPlugin = new NAFIdentityPlugin(properties);
			String accessId = requestTokenFromGenerator();
			Token token = nafIdentityPlugin.getToken(accessId);
			return token;
		} catch (Exception e) {
			return null;
		}
	}

	private String requestTokenFromGenerator() {
		String tokenGeneratorUrl = properties.getProperty(NAF_IDENTITY_TOKEN_GENERATOR_URL);
		String userName = properties.getProperty(NAF_IDENTITY_TOKEN_USERNAME);
		String password = properties.getProperty(NAF_IDENTITY_TOKEN_PASSWORD);
		int hours = getTokenUpdateTimeInHours();

		if (userName == null || password == null || tokenGeneratorUrl == null) {
			return null;
		}

		try {
			CloseableHttpClient httpClient = HttpClients.createMinimal();
			HttpPost httpPost = new HttpPost(tokenGeneratorUrl);
			List<NameValuePair> parameters = new ArrayList<NameValuePair>();
			parameters.add(new BasicNameValuePair(USERNAME_PARAMETER, userName));
			parameters.add(new BasicNameValuePair(PASSWORD_PARAMETER, password));
			parameters.add(new BasicNameValuePair(HOUR_PARAMETER, String.valueOf(hours)));
			UrlEncodedFormEntity formEntity = new UrlEncodedFormEntity(parameters);
			httpPost.setEntity(formEntity);

			CloseableHttpResponse response = httpClient.execute(httpPost);
			if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
				return IOUtils.toString(response.getEntity().getContent());
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	private int getTokenUpdateTimeInHours() {

		int hourInMinutes = 60;
		int hourInSeconds = hourInMinutes * 60;
		int hourInMiliseconds = hourInSeconds * 1000;

		int updateTime = getUpdateTime();
		TimeUnit updateTimeUnits = getUpdateTimeUnits();
		if (updateTimeUnits.equals(TimeUnit.MINUTES)) {
			updateTime = updateTime / hourInMinutes;
		} else if (updateTimeUnits.equals(TimeUnit.SECONDS)) {
			updateTime = updateTime / hourInSeconds;
		} else if (updateTimeUnits.equals(TimeUnit.MILLISECONDS)) {
			updateTime = updateTime / hourInMiliseconds;
		}

		return Math.max(1, updateTime);
	}


	@Override
	public void validateProperties() throws BlowoutException {
		if (!properties.containsKey(NAF_IDENTITY_PRIVATE_KEY)) {
			throw new BlowoutException(
					"Required property " + NAF_IDENTITY_PRIVATE_KEY + " was not set");
		}

		if (!properties.containsKey(NAF_IDENTITY_PUBLIC_KEY)) {
			throw new BlowoutException(
					"Required property " + NAF_IDENTITY_PUBLIC_KEY + " was not set");
		}

		if (!properties.containsKey(NAF_IDENTITY_TOKEN_GENERATOR_URL)) {
			throw new BlowoutException("Required property " + NAF_IDENTITY_TOKEN_GENERATOR_URL
					+ " was not set");
		}

		if (!properties.containsKey(NAF_IDENTITY_TOKEN_USERNAME)) {
			throw new BlowoutException(
					"Required property " + NAF_IDENTITY_TOKEN_USERNAME + " was not set");
		}

		if (!properties.containsKey(NAF_IDENTITY_TOKEN_PASSWORD)) {
			throw new BlowoutException(
					"Required property " + NAF_IDENTITY_TOKEN_PASSWORD + " was not set");
		}
	}
}
