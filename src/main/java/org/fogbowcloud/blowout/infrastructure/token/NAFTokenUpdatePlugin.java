package org.fogbowcloud.blowout.infrastructure.token;

import com.google.gson.JsonObject;
import org.apache.log4j.Logger;
import org.bouncycastle.util.encoders.Base64;
import org.fogbowcloud.blowout.core.exception.BlowoutException;
import org.fogbowcloud.blowout.core.util.AppPropertiesConstants;
import org.fogbowcloud.manager.core.plugins.identity.naf.NAFIdentityPlugin;
import org.fogbowcloud.manager.core.plugins.identity.naf.RSAUtils;
import org.fogbowcloud.manager.occi.model.Token;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Date;
import java.util.Properties;

public class NAFTokenUpdatePlugin extends AbstractTokenUpdatePlugin {

    private static final Logger LOGGER = Logger.getLogger(NAFTokenUpdatePlugin.class);

    private static final String TAG_SEPARETOR_DASHBOARD_NAF_AUTH = "!#!";
    private static final String DEFAULT_NAME = "fogbow_user_naf";

    private static final String SAML_ATTRIBUTES_KEY_JSON = "saml_attributes";
    private static final String TOKEN_ETIME_KEY_JSON = "token_etime";
    private static final String NAME_KEY_JSON = "name";

    private static final String INFRA_AUTH_PREFIX = AppPropertiesConstants.INFRA_AUTH_TOKEN_PREFIX;
    protected static final String MAN_NAF_IDENTITY_PUBLIC_KEY = "naf_identity_public_key";
    protected static final String MAN_NAF_IDENTITY_PRIVATE_KEY = "naf_identity_private_key";
    private static final String NAF_USERNAME = "naf_username";
    public static final String NAF_IDENTITY_PUBLIC_KEY = INFRA_AUTH_PREFIX + MAN_NAF_IDENTITY_PUBLIC_KEY;
    public static final String NAF_IDENTITY_PRIVATE_KEY = INFRA_AUTH_PREFIX + MAN_NAF_IDENTITY_PRIVATE_KEY;
    public static final String TOKEN_PLUGIN_USERNAME = INFRA_AUTH_PREFIX + NAF_USERNAME;


    public NAFTokenUpdatePlugin(Properties properties) throws BlowoutException {
        super(properties);
        validateProperties();
    }

    @Override
    public Token generateToken() {
        LOGGER.debug("Creating NAF Token.");
        Token token = null;
        try {
            Properties properties = new Properties();
            properties.put(MAN_NAF_IDENTITY_PUBLIC_KEY, getProperties().getProperty(NAF_IDENTITY_PUBLIC_KEY));
            NAFIdentityPlugin nafIdentityPlugin = new NAFIdentityPlugin(properties);
            String accessIdMessage = createAccessId();

            String privateKeyPath = getProperties().getProperty(NAF_IDENTITY_PRIVATE_KEY);
            RSAPrivateKey privateKey = getPrivateKey(privateKeyPath);
            String accessIdMessageSigned = RSAUtils.sign(privateKey, accessIdMessage);

            String accessId = accessIdMessage + TAG_SEPARETOR_DASHBOARD_NAF_AUTH + accessIdMessageSigned;
            String accessIdEnconded = new String(Base64.encode(accessId.getBytes()));
            token = nafIdentityPlugin.getToken(accessIdEnconded);
            LOGGER.info("Token created. " + token.toString());
        } catch (Exception e) {
            LOGGER.warn("Error while creating token.", e);
        }
        return token;
    }

    private static String getKey(String filename) throws IOException {
        // Read key from file
        StringBuilder strKeyPEM = new StringBuilder();
        BufferedReader br = new BufferedReader(new FileReader(filename));
        String line;
        while ((line = br.readLine()) != null) {
            strKeyPEM.append(line);
        }
        br.close();
        return strKeyPEM.toString();
    }

    private static RSAPrivateKey getPrivateKey(String filename) throws Exception {
        String privateKeyPEM = getKey(filename);

        // Remove the first and last lines
        privateKeyPEM = privateKeyPEM.replace("-----BEGIN PRIVATE KEY-----", "");
        privateKeyPEM = privateKeyPEM.replace("-----END PRIVATE KEY-----", "");

        // Base64 decode data
        byte[] encoded = org.bouncycastle.util.encoders.Base64.decode(privateKeyPEM);

        KeyFactory kf = KeyFactory.getInstance("RSA");
        return (RSAPrivateKey) kf.generatePrivate(new PKCS8EncodedKeySpec(encoded));
    }

    private String createAccessId() {
        JsonObject jsonObject = new JsonObject();
        String name = getProperties().getProperty(TOKEN_PLUGIN_USERNAME);
        jsonObject.addProperty(NAME_KEY_JSON, name != null ? name: DEFAULT_NAME);
        long infinitTime = new Date(Long.MAX_VALUE).getTime();
        jsonObject.addProperty(TOKEN_ETIME_KEY_JSON, String.valueOf(infinitTime));
        jsonObject.add(SAML_ATTRIBUTES_KEY_JSON, new JsonObject());

        return jsonObject.toString();
    }

    @Override
    public void validateProperties() throws BlowoutException {
        if (!getProperties().containsKey(NAF_IDENTITY_PUBLIC_KEY)) {
            throw new BlowoutException("Required property " + NAF_IDENTITY_PUBLIC_KEY + " was not set.");
        }
        if (!getProperties().containsKey(NAF_IDENTITY_PRIVATE_KEY)) {
            throw new BlowoutException("Required property " + NAF_IDENTITY_PRIVATE_KEY + " was not set.");
        }
    }
}
