import java.security.Principal;
import java.security.PrivilegedAction;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.security.auth.Subject;
import javax.security.auth.kerberos.KerberosPrincipal;
import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.Configuration;
import javax.security.auth.login.LoginContext;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthSchemeProvider;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.config.Lookup;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

/**
 *
 * Based on Spring's implementation of KerberosRestTemplate, simplified version
 * that only allows making a GET request using a HttpClient that uses SPNEGO to communicate
 * with a kerberized server and the response is read as a String.
 *
 * If a keytab file is provided it will be used to log into the Auth Server, otherwise a ticket cache
 * will be used.
 *
 * Ensure the hostname/domain of the server (url passed in) is part of the Kerberos realm / Domain controller
 *
 * @author davidfernandez
 *
 */
public class KerberosHttpClient {

    private static final Log LOG = LogFactory.getLog(KerberosHttpClient.class);

    @SuppressWarnings("synthetic-access")
    private static final Credentials credentials = new NullCredentials();

    private String keyTabLocation;
    private String userPrincipal;
    private String servicePrincipal;
    private HttpClient httpClient;
    private Map<String, Object> loginOptions;
    private ServiceNameType serviceNameType;

    public KerberosHttpClient(String keytabLocation, String userPrincipal, ServiceNameType serviceNameType) {
        this(keytabLocation, userPrincipal, null, serviceNameType);
    }

    public KerberosHttpClient(String keytabLocation, String userPrincipal, String servicePrincipal, ServiceNameType serviceNameType) {
        this.keyTabLocation = keytabLocation;
        this.userPrincipal = userPrincipal;
        this.serviceNameType = serviceNameType;
        this.servicePrincipal = servicePrincipal;
        this.httpClient = buildHttpClient();
    }

    /**
     * Builds the default instance of {@link HttpClient} having Kerberos/SPNEGO
     * support.
     *
     * It puts the flag useCanonicalHostname to false in the SpnegoSchemeFactory
     * to make the login to auth server work by doing a 'shallow' inspect of the server hostname
     * (without lookups) so this can be used with hosts that use aliases of localhost and still be
     * recognized as part of the Kerberos realm
     *
     * @return the http client with spnego auth scheme
     */
    private HttpClient buildHttpClient() {
        HttpClientBuilder builder = HttpClientBuilder.create();
        Lookup<AuthSchemeProvider> authSchemeRegistry = RegistryBuilder.<AuthSchemeProvider> create()
                .register(AuthSchemes.SPNEGO, new CustomSPNegoSchemeFactory(serviceNameType, userPrincipal, servicePrincipal, true, false)).build();
        builder.setDefaultAuthSchemeRegistry(authSchemeRegistry);
        BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(new AuthScope(null, -1, null), credentials);
        builder.setDefaultCredentialsProvider(credentialsProvider);
        CloseableHttpClient builtHttpClient = builder.build();
        return builtHttpClient;
    }

    public String executeGet(final String url) {

        try {
            ClientLoginConfig loginConfig = new ClientLoginConfig(keyTabLocation, userPrincipal, loginOptions);
            Set<Principal> princ = new HashSet<Principal>(1);
            princ.add(new KerberosPrincipal(userPrincipal));
            Subject sub = new Subject(false, princ, new HashSet<Object>(), new HashSet<Object>());
            LoginContext lc = new LoginContext("", sub, null, loginConfig);
            lc.login();
            Subject serviceSubject = lc.getSubject();
            return Subject.doAs(serviceSubject, new PrivilegedAction<String>() {
                @SuppressWarnings("synthetic-access")
                @Override
                public String run() {
                    return executeRequestGet(url);
                }
            });
        } catch (Exception e) {
            throw new RuntimeException("Error running call", e);
        }
    }
    public String executePost(final HttpPost post) {

        try {
            ClientLoginConfig loginConfig = new ClientLoginConfig(keyTabLocation, userPrincipal, loginOptions);
            Set<Principal> princ = new HashSet<Principal>(1);
            princ.add(new KerberosPrincipal(userPrincipal));
            Subject sub = new Subject(false, princ, new HashSet<Object>(), new HashSet<Object>());
            LoginContext lc = new LoginContext("", sub, null, loginConfig);
            lc.login();
            Subject serviceSubject = lc.getSubject();
            return Subject.doAs(serviceSubject, new PrivilegedAction<String>() {
                @SuppressWarnings("synthetic-access")
                @Override
                public String run() {
                    return executeRequestPost(post);
                }
            });
        } catch (Exception e) {
            throw new RuntimeException("Error running call", e);
        }
    }
    private String executeRequestGet(String url) {

        HttpGet httpGet = new HttpGet(url);

        try {

            HttpResponse response = httpClient.execute(httpGet);

            if (response.getStatusLine().getStatusCode() != 200) {
                String msg = "Error in request to " + url + ", status is " + response.getStatusLine().getStatusCode() + ", reason " + response.getStatusLine().getReasonPhrase();
                LOG.error(msg);
                throw new RuntimeException(msg);
            }

            return IOUtils.toString(response.getEntity().getContent());
        } catch (Exception e) {
            LOG.error("Error executing call to " + url, e);
            throw new RuntimeException(e);
        }
    }

    private String executeRequestPost(HttpPost post) {
        try {

            HttpResponse response = httpClient.execute(post);

            if (response.getStatusLine().getStatusCode()/100 != 2) {
                String msg = "Error in request to " + post.getURI() + ", status is " + response.getStatusLine().getStatusCode() + ", reason " + response.getStatusLine().getReasonPhrase();
                LOG.error(msg);
                throw new RuntimeException(msg);
            }

            return EntityUtils.toString(response.getEntity(),"UTF-8");
        } catch (Exception e) {
            LOG.error("Error executing call to " + post.getURI(), e);
            throw new RuntimeException(e);
        }
    }

    private static class NullCredentials implements Credentials {

        @Override
        public Principal getUserPrincipal() {
            return null;
        }

        @Override
        public String getPassword() {
            return null;
        }

    }

    private static class ClientLoginConfig extends Configuration {

        private final String keyTabLocation;
        private final String userPrincipal;
        private final Map<String, Object> loginOptions;

        public ClientLoginConfig(String keyTabLocation, String userPrincipal, Map<String, Object> loginOptions) {
            super();
            this.keyTabLocation = keyTabLocation;
            this.userPrincipal = userPrincipal;
            this.loginOptions = loginOptions;
        }

        @Override
        public AppConfigurationEntry[] getAppConfigurationEntry(String name) {

            Map<String, Object> options = new HashMap<String, Object>();

            // if we don't have keytab or principal only option is to rely on
            // credentials cache.
            if (keyTabLocation ==null || userPrincipal== null) {
                // cache
                options.put("useTicketCache", "true");
            } else {
                // keytab
                options.put("useKeyTab", "true");
                options.put("keyTab", this.keyTabLocation);
                options.put("principal", this.userPrincipal);
                options.put("storeKey", "true");
            }
            options.put("doNotPrompt", "true");
            options.put("isInitiator", "true");

            if (loginOptions != null) {
                options.putAll(loginOptions);
            }

            return new AppConfigurationEntry[] { new AppConfigurationEntry(
                    "com.sun.security.auth.module.Krb5LoginModule",
                    AppConfigurationEntry.LoginModuleControlFlag.REQUIRED, options) };
        }
    }
}