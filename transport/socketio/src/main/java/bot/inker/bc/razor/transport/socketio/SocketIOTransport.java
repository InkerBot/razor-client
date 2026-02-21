package bot.inker.bc.razor.transport.socketio;

import bot.inker.bc.razor.transport.SocketTransport;
import bot.inker.bc.razor.transport.SocketTransportListener;
import io.socket.client.IO;
import io.socket.client.Manager;
import io.socket.client.Socket;
import okhttp3.OkHttpClient;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SocketIOTransport implements SocketTransport {
    private volatile Socket socket;

    @Override
    public void connect(String url, Map<String, String> properties, SocketTransportListener listener) {
        disconnect();

        try {
            IO.Options options = applyProperties(new IO.Options(), properties);

            Socket socket = IO.socket(URI.create(url), options);

            socket.on(Socket.EVENT_CONNECT, args -> listener.onConnect());

            socket.on(Socket.EVENT_DISCONNECT, args -> {
                String reason = args.length > 0 && args[0] != null ? args[0].toString() : null;
                listener.onDisconnect(reason);
            });

            socket.io().on(Manager.EVENT_RECONNECT_ATTEMPT, args -> {
                int attempt = args.length > 0 ? ((Number) args[0]).intValue() : 0;
                listener.onReconnecting(attempt);
            });

            socket.onAnyIncoming(args -> {
                if (args.length < 1) return;
                String event = args[0].toString();
                String payload = args.length > 1 ? serializePayload(args[1]) : "null";
                listener.onEvent(event, payload);
            });

            this.socket = socket;
            socket.connect();
        } catch (Exception e) {
            throw new RuntimeException("Failed to connect to " + url, e);
        }
    }

    @Override
    public void disconnect() {
        Socket socket = this.socket;
        if (socket != null) {
            socket.off();
            socket.disconnect();
            this.socket = null;
        }
    }

    @Override
    public void emit(String event, String payload) {
        Socket socket = this.socket;
        if (socket == null) return;

        try {
            Object data = new JSONTokener(payload).nextValue();
            socket.emit(event, data);
        } catch (Exception e) {
            socket.emit(event, payload);
        }
    }

    @Override
    public boolean isConnected() {
        Socket socket = this.socket;
        return socket != null && socket.connected();
    }

    private static String serializePayload(Object data) {
        if (data instanceof JSONObject || data instanceof JSONArray) {
            return data.toString();
        }
        if (data instanceof String) {
            return JSONObject.quote((String) data);
        }
        return String.valueOf(data);
    }

    private static IO.Options applyProperties(IO.Options options, Map<String, String> properties) {
        options.reconnection = getBool(properties, "reconnection", true);
        options.forceNew = getBool(properties, "forceNew", true);

        String reconnectionAttempts = properties.get("reconnectionAttempts");
        if (reconnectionAttempts != null) {
            options.reconnectionAttempts = Integer.parseInt(reconnectionAttempts);
        }

        String reconnectionDelay = properties.get("reconnectionDelay");
        if (reconnectionDelay != null) {
            options.reconnectionDelay = Long.parseLong(reconnectionDelay);
        }

        String reconnectionDelayMax = properties.get("reconnectionDelayMax");
        if (reconnectionDelayMax != null) {
            options.reconnectionDelayMax = Long.parseLong(reconnectionDelayMax);
        }

        String timeout = properties.get("timeout");
        if (timeout != null) {
            options.timeout = Long.parseLong(timeout);
        }

        String path = properties.get("path");
        if (path != null) {
            options.path = path;
        }

        String query = properties.get("query");
        if (query != null) {
            options.query = query;
        }

        applyHeaders(options, properties);
        applyProxy(options, properties);

        return options;
    }

    private static void applyHeaders(IO.Options options, Map<String, String> properties) {
        Map<String, List<String>> headers = new HashMap<>();
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            if (entry.getKey().startsWith("header.")) {
                String headerName = entry.getKey().substring("header.".length());
                headers.computeIfAbsent(headerName, k -> new ArrayList<>()).add(entry.getValue());
            }
        }
        if (!headers.isEmpty()) {
            options.extraHeaders = headers;
        }
    }

    private static void applyProxy(IO.Options options, Map<String, String> properties) {
        String proxyHost = properties.get("proxy.host");
        String proxyPort = properties.get("proxy.port");
        if (proxyHost == null || proxyPort == null) return;

        Proxy proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(proxyHost, Integer.parseInt(proxyPort)));
        OkHttpClient.Builder builder = new OkHttpClient.Builder().proxy(proxy);

        String username = properties.get("proxy.username");
        String password = properties.get("proxy.password");
        if (username != null && password != null) {
            Authenticator.setDefault(new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    if (getRequestingHost().equalsIgnoreCase(proxyHost)) {
                        return new PasswordAuthentication(username, password.toCharArray());
                    }
                    return null;
                }
            });
        }

        OkHttpClient client = builder.build();
        options.callFactory = client;
        options.webSocketFactory = client;
    }

    private static boolean getBool(Map<String, String> properties, String key, boolean defaultValue) {
        String value = properties.get(key);
        return value != null ? Boolean.parseBoolean(value) : defaultValue;
    }
}