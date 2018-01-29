package org.fogbowcloud.blowout.infrastructure.token;

import java.util.Properties;

import org.fogbowcloud.blowout.core.exception.BlowoutException;
import org.fogbowcloud.manager.core.plugins.identity.naf.NAFIdentityPlugin;
import org.fogbowcloud.manager.occi.model.Token;
import org.junit.Assert;
import org.junit.Test;

public class TestNAFTokenUpdatePlugin {

    private static final String PRIVATE_KEY_PEM = "src/test/resources/naf_token/private_key.pem";
    private static final String PUBLIC_KEY_PEM = "src/test/resources/naf_token/public_key.pem";

    @Test
    public void testGenerateToken() throws BlowoutException {
        String username = "Fulano";
        Properties properties = new Properties();
        properties.put(NAFTokenUpdatePlugin.TOKEN_PLUGIN_USERNAME, username);
        properties.put(NAFTokenUpdatePlugin.NAF_IDENTITY_PUBLIC_KEY, PUBLIC_KEY_PEM);
        properties.put(NAFTokenUpdatePlugin.NAF_IDENTITY_PRIVATE_KEY, PRIVATE_KEY_PEM);

        NAFTokenUpdatePlugin nafTokenUpdatePlugin = new NAFTokenUpdatePlugin(properties);
        Token token = nafTokenUpdatePlugin.generateToken();

        Properties propertiesNafManager = new Properties();
        propertiesNafManager.put(NAFTokenUpdatePlugin.MAN_NAF_IDENTITY_PUBLIC_KEY, PUBLIC_KEY_PEM);
        propertiesNafManager.put(NAFTokenUpdatePlugin.MAN_NAF_IDENTITY_PRIVATE_KEY, PRIVATE_KEY_PEM);
        NAFIdentityPlugin nafIdentityPlugin = new NAFIdentityPlugin(propertiesNafManager);
        Assert.assertTrue(nafIdentityPlugin.isValid(token.getAccessId()));
        Assert.assertEquals(username, token.getUser().getId());
        Assert.assertEquals(username, token.getUser().getName());
    }

    @Test
    public void testGenerateTokenWrong() throws BlowoutException {
        String username = "Fulano";
        Properties properties = new Properties();
        properties.put(NAFTokenUpdatePlugin.TOKEN_PLUGIN_USERNAME, username);
        properties.put(NAFTokenUpdatePlugin.NAF_IDENTITY_PUBLIC_KEY, "");
        properties.put(NAFTokenUpdatePlugin.NAF_IDENTITY_PRIVATE_KEY, PRIVATE_KEY_PEM);

        NAFTokenUpdatePlugin nafTokenUpdatePlugin = new NAFTokenUpdatePlugin(properties);
        Token token = nafTokenUpdatePlugin.generateToken();

        Assert.assertNull(token);
    }

    @Test
    public void testValidatePropertiesWithouPublicKey() {
        String username = "Fulano";
        Properties properties = new Properties();
        properties.put(NAFTokenUpdatePlugin.TOKEN_PLUGIN_USERNAME, username);
        properties.put(NAFTokenUpdatePlugin.NAF_IDENTITY_PUBLIC_KEY, PUBLIC_KEY_PEM);
        properties.put(NAFTokenUpdatePlugin.NAF_IDENTITY_PRIVATE_KEY, PRIVATE_KEY_PEM);

        try {
            properties.remove(NAFTokenUpdatePlugin.NAF_IDENTITY_PUBLIC_KEY);
            new NAFTokenUpdatePlugin(properties);
        } catch (BlowoutException e) {
            e.printStackTrace();
            return;
        }
        Assert.fail();
    }

    @Test
    public void testValidatePropertiesWithouPrivateKey() {
        String username = "Fulano";
        Properties properties = new Properties();
        properties.put(NAFTokenUpdatePlugin.TOKEN_PLUGIN_USERNAME, username);
        properties.put(NAFTokenUpdatePlugin.NAF_IDENTITY_PUBLIC_KEY, PUBLIC_KEY_PEM);
        properties.put(NAFTokenUpdatePlugin.NAF_IDENTITY_PRIVATE_KEY, PRIVATE_KEY_PEM);

        try {
            properties.remove(NAFTokenUpdatePlugin.NAF_IDENTITY_PRIVATE_KEY);
            new NAFTokenUpdatePlugin(properties);
        } catch (BlowoutException e) {
            e.printStackTrace();
            return;
        }
        Assert.fail();
    }

}
