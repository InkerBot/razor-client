package bot.inker.bc.razor.transport;

import java.util.Map;

public interface SocketTransport {
    void connect(String url, Map<String, String> properties, SocketTransportListener listener);
    void disconnect();
    void emit(String event, String payload);
    boolean isConnected();
}
