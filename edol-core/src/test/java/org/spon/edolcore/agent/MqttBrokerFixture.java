package org.spon.edolcore.agent;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/** A fresh single broker using the production policy generator and TLS configuration. */
public final class MqttBrokerFixture implements AutoCloseable {
    public static final String STORE_PASSWORD = UUID.randomUUID().toString();
    private static final String SERVICE_PREFIX = UUID.randomUUID().toString();
    private static final String TRUST_STORE = "trust.p12";
    private static final String BROKER_ALIAS = "broker";
    private final GenericContainer<?> container = new GenericContainer<>(DockerImageName.parse("eclipse-mosquitto:2.0.22"))
            .withExposedPorts(8883);
    private SSLSocketFactory sockets;
    private Path directory;

    public void start() throws Exception {
        start(false, false);
    }

    public void start(boolean wrongHostname, boolean expired) throws Exception {
        var attributes = Path.of(System.getProperty("java.io.tmpdir")).getFileSystem().supportedFileAttributeViews().contains("posix")
                ? new java.nio.file.attribute.FileAttribute<?>[]{java.nio.file.attribute.PosixFilePermissions.asFileAttribute(
                        java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"))}
                : new java.nio.file.attribute.FileAttribute<?>[0];
        // Windows uses the current user temp directory's inherited ACL; POSIX explicitly restricts access.
        directory = Files.createTempDirectory("edol-mqtt-test-", attributes);
        String host = container.getHost();
        String san = host.matches("[0-9.]+") ? "ip:" + host : "dns:" + host;
        if (wrongHostname) san = "dns:wrong.example.invalid";
        keytool("-genkeypair", "-alias", BROKER_ALIAS, "-keyalg", "RSA", "-keysize", "2048",
                "-dname", "CN=disposable-agent-broker", "-ext", "SAN=" + san,
                "-startdate", expired ? "-3d" : "-1d", "-validity", expired ? "1" : "3",
                "-storetype", "PKCS12", "-keystore", directory.resolve(TRUST_STORE).toString(),
                "-storepass", STORE_PASSWORD, "-keypass", STORE_PASSWORD);
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (var input = Files.newInputStream(directory.resolve(TRUST_STORE))) { store.load(input, STORE_PASSWORD.toCharArray()); }
        Files.writeString(directory.resolve("server.pem"), pem("CERTIFICATE", store.getCertificate(BROKER_ALIAS).getEncoded()));
        Files.writeString(directory.resolve("server-key.pem"), pem("PRIVATE KEY", store.getKey(BROKER_ALIAS, STORE_PASSWORD.toCharArray()).getEncoded()));
        TrustManagerFactory trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trust.init(store);
        SSLContext context = SSLContext.getInstance("TLSv1.2");
        context.init(null, trust.getTrustManagers(), null);
        sockets = context.getSocketFactory();
        Path root = Path.of("").toAbsolutePath();
        if (!Files.isDirectory(root.resolve("docker"))) root = root.getParent();
        ProcessBuilder initialize = new ProcessBuilder("py", "-3", root.resolve("docker/mosquitto/initialize_security.py").toString(),
                directory.resolve("dynamic-security.json").toString());
        for (String name : new String[]{"CORE_EVENTS", "HUB", "NOTIFY", "AMS", "INGRESS", "COMMAND", "CONTROL"}) {
            initialize.environment().put("EDOL_MQTT_" + name + "_PASSWORD", servicePassword(name));
        }
        run(initialize);
        container.withCopyFileToContainer(MountableFile.forHostPath(root.resolve("docker/mosquitto/secure.conf")), "/mosquitto/config/mosquitto.conf")
                .withCopyFileToContainer(MountableFile.forHostPath(directory.resolve("server.pem")), "/mosquitto/certs/server.pem")
                .withCopyFileToContainer(MountableFile.forHostPath(directory.resolve("server-key.pem"), 0644), "/mosquitto/certs/server-key.pem")
                .withCopyFileToContainer(MountableFile.forHostPath(directory.resolve("dynamic-security.json")), "/mosquitto/data/dynamic-security.json")
                .withCreateContainerCmdModifier(command -> command.withUser("0:0"))
                .withCommand("sh", "-ec", "chown -R mosquitto:mosquitto /mosquitto/data; exec mosquitto -c /mosquitto/config/mosquitto.conf");
        container.start();
        org.slf4j.LoggerFactory.getLogger(MqttBrokerFixture.class).info("Disposable Mosquitto image digests={}",
                container.getDockerClient().inspectImageCmd(container.getDockerImageName()).exec().getRepoDigests());
    }

    public static String servicePassword(String name) { return SERVICE_PREFIX + name; }
    public String url() {
        // Docker may reassign a random host port on restart; do not use cached inspection data.
        var info = container.getDockerClient().inspectContainerCmd(container.getContainerId()).exec();
        String port = info.getNetworkSettings().getPorts().getBindings()
                .get(com.github.dockerjava.api.model.ExposedPort.tcp(8883))[0].getHostPortSpec();
        return "ssl://" + container.getHost() + ":" + port;
    }
    public SSLSocketFactory sockets() { return sockets; }
    public Path trustStore() { return directory.resolve(TRUST_STORE); }
    public void restart() throws Exception {
        container.getDockerClient().restartContainerCmd(container.getContainerId()).exec();
        org.awaitility.Awaitility.await().atMost(30, java.util.concurrent.TimeUnit.SECONDS).ignoreExceptions().until(() -> {
            try (var socket = new java.net.Socket()) {
                var endpoint = java.net.URI.create(url());
                socket.connect(new java.net.InetSocketAddress(endpoint.getHost(), endpoint.getPort()), 500);
                return true;
            }
        });
    }

    public MqttClient connect(String user, String password, String id) throws Exception {
        MqttClient client = new MqttClient(url(), id, new MemoryPersistence()) {
            @Override public void close() throws org.eclipse.paho.client.mqttv3.MqttException {
                if (isConnected()) disconnect();
                super.close();
            }
        };
        client.setTimeToWait(5000);
        MqttConnectOptions options = options(user, password);
        client.connect(options);
        return client;
    }

    public MqttConnectOptions options(String user, String password) {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setUserName(user);
        options.setPassword(password.toCharArray());
        options.setSocketFactory(sockets);
        options.setHttpsHostnameVerificationEnabled(true);
        options.setConnectionTimeout(5);
        options.setKeepAliveInterval(5);
        return options;
    }

    private static String pem(String kind, byte[] bytes) {
        return "-----BEGIN " + kind + "-----\n" + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(bytes)
                + "\n-----END " + kind + "-----\n";
    }

    private static void keytool(String... args) throws java.io.IOException, InterruptedException {
        var command = new java.util.ArrayList<String>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "keytool.exe").toString());
        command.addAll(java.util.List.of(args));
        command.add("-noprompt");
        run(new ProcessBuilder(command));
    }

    private static void run(ProcessBuilder builder) throws java.io.IOException, InterruptedException {
        Process process = builder.redirectErrorStream(true).start();
        process.getInputStream().readAllBytes();
        if (!process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS) || process.exitValue() != 0) {
            throw new IllegalStateException("Disposable MQTT fixture preparation failed");
        }
    }

    @Override public void close() { container.stop(); }
}
