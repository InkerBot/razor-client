package bot.inker.bc.razor.transport;

public interface SocketTransportListener {
    void onConnect();

    void onDisconnect(String reason);

    void onReconnecting(int attemptNumber);

    void onEvent(String event, String payload);
}
