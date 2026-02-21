package bot.inker.bc.razor.transport;

import java.util.ServiceLoader;

public final class SocketTransports {
    private SocketTransports() {
    }

    public static SocketTransport create(String name) {
        for (SocketTransportProvider provider : ServiceLoader.load(SocketTransportProvider.class)) {
            if (provider.name().equals(name)) {
                return provider.create();
            }
        }
        throw new IllegalArgumentException("No SocketTransport provider found with name: " + name);
    }
}