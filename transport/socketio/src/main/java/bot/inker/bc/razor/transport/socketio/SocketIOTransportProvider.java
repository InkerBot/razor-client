package bot.inker.bc.razor.transport.socketio;

import bot.inker.bc.razor.transport.SocketTransport;
import bot.inker.bc.razor.transport.SocketTransportProvider;

public class SocketIOTransportProvider implements SocketTransportProvider {
    @Override
    public String name() {
        return "socketio";
    }

    @Override
    public SocketTransport create() {
        return new SocketIOTransport();
    }
}