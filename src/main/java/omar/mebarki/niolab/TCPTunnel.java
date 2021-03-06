package omar.mebarki.niolab;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

public class TCPTunnel {
    private int localPort;
    private InetSocketAddress destAddr;

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            usage();
            System.exit(1);
        }

        TCPTunnel tcpTunnel = new TCPTunnel(Integer.parseInt(args[0]), new InetSocketAddress(args[1], Integer.parseInt(args[2])));
        tcpTunnel.start();
    }

    private static void usage() {
        System.out.println("TCPTunnel localPort destAddr destPort");
    }

    private TCPTunnel(int localPort, InetSocketAddress destAddr) {
        this.localPort = localPort;
        this.destAddr = destAddr;
    }

    private void start() throws Exception {
        ServerSocketChannel serverSocket = openServerSocket();

        Selector selector = Selector.open();
        serverSocket.register(selector, SelectionKey.OP_ACCEPT);

        while (true) {
            selector.select();
            Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();
            while (keyIterator.hasNext()) {
                SelectionKey key = keyIterator.next();
                if (key.isAcceptable()) {
                    handleNewIncomingClient(key, serverSocket);
                } else if (key.isReadable()) {
                    handleIncomingData(key);
                }
                keyIterator.remove();
            }
        }

    }

    private void handleIncomingData(SelectionKey key) throws Exception {
        SocketChannel inputChannel = (SocketChannel) key.channel();
        SocketChannel outputChannel = (SocketChannel) ((SelectionKey) key.attachment()).channel();
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        int redBytes = 0;
        while ((redBytes = inputChannel.read(buffer)) > 0) {
            buffer.flip();
            while (buffer.hasRemaining()) {
                outputChannel.write(buffer);
            }

        }
        if (redBytes < 0) {
            inputChannel.close();
            key.cancel();
            outputChannel.close();
            ((SelectionKey) key.attachment()).cancel();
        }
    }

    private void handleNewIncomingClient(SelectionKey key, ServerSocketChannel serverSocket) throws IOException {
        SocketChannel clientSocket = serverSocket.accept();
        clientSocket.configureBlocking(false);
        SelectionKey clientSelectionKey = clientSocket.register(key.selector(), SelectionKey.OP_READ);
        System.out.println("New Client " + clientSocket.toString());

        SocketChannel otherSideSocket = SocketChannel.open(destAddr);
        otherSideSocket.configureBlocking(false);
        SelectionKey otherSideSelectionKey = otherSideSocket.register(key.selector(), SelectionKey.OP_READ);


        clientSelectionKey.attach(otherSideSelectionKey);
        otherSideSelectionKey.attach(clientSelectionKey);

    }

    private ServerSocketChannel openServerSocket() throws Exception {
        ServerSocketChannel serverSocket = ServerSocketChannel.open();
        InetSocketAddress hostAddress = new InetSocketAddress(this.localPort);
        serverSocket.bind(hostAddress);
        serverSocket.configureBlocking(false);
        return serverSocket;
    }

}
