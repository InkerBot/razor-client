package bot.inker.bc.razor.transport;

public interface SocketTransportProvider {
    String name();
    SocketTransport create();
}