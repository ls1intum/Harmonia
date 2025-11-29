package de.tum.cit.aet;

import de.tum.cit.aet.core.dto.ArtemisCredentials;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Helper class to load test credentials from a properties file.
 * Usage:
 * <pre>
 *     TestCredentialsLoader loader = new TestCredentialsLoader();
 *     if (!loader.isAvailable()) {
 *         System.out.println("Skipping test: " + loader.getSkipMessage());
 *         return;
 *     }
 *     ArtemisCredentials credentials = loader.getCredentials(jwtToken);
 * </pre>
 */
public class TestCredentialsLoader {

    private static final String PROPERTIES_FILE = "test-credentials.properties";
    private static final String TEMPLATE_FILE = "test-credentials.properties.template";

    private final Properties properties;
    private final String serverUrl;
    private final String username;
    private final String password;

    public TestCredentialsLoader() {
        this.properties = loadProperties();
        this.serverUrl = properties.getProperty("artemis.test.server-url");
        this.username = properties.getProperty("artemis.test.username");
        this.password = properties.getProperty("artemis.test.password");
    }

    private Properties loadProperties() {
        Properties props = new Properties();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(PROPERTIES_FILE)) {
            if (input != null) {
                props.load(input);
            }
        } catch (IOException e) {
            // File not found or cannot be read - credentials not available
        }
        return props;
    }

    /**
     * Checks if valid credentials are available.
     */
    public boolean isAvailable() {
        return serverUrl != null && !serverUrl.isBlank()
                && username != null && !username.isBlank() && !username.equals("REPLACE_WITH_USERNAME")
                && password != null && !password.isBlank() && !password.equals("REPLACE_WITH_PASSWORD");
    }

    /**
     * Returns a message explaining why credentials are not available.
     */
    public String getSkipMessage() {
        return "Credentials not configured. Copy " + TEMPLATE_FILE + " to " + PROPERTIES_FILE + " and fill in your values.";
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    /**
     * Creates an ArtemisCredentials object with the loaded credentials and provided JWT token.
     *
     * @param jwtToken The JWT token obtained from authentication
     * @return ArtemisCredentials object
     */
    public ArtemisCredentials getCredentials(String jwtToken) {
        return new ArtemisCredentials(serverUrl, jwtToken, username, password);
    }
}
