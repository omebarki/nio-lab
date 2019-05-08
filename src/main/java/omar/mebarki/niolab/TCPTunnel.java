package omar.mebarki.niolab;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;

public class TCPTunnel {
    private int localPort;
    private InetSocketAddress destAddr;
    BiMap<SelectableChannel, SelectableChannel> conectionsCouples = HashBiMap.create();

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
        SocketChannel outputChannel = (SocketChannel) key.attachment();
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        int redBytes = 0;
        while ((redBytes = inputChannel.read(buffer)) > 0) {
            buffer.flip();
            while (buffer.hasRemaining()) {
                int write = outputChannel.write(buffer);
                System.out.println(write);
            }

        }
        if (redBytes < 0) {
            inputChannel.close();
            key.cancel();
        }
    }

    private void handleNewIncomingClient(SelectionKey key, ServerSocketChannel serverSocket) throws IOException {
        SocketChannel clientSocket = serverSocket.accept();
        clientSocket.configureBlocking(false);
        SelectionKey clientSelectionKey = clientSocket.register(key.selector(), SelectionKey.OP_READ);

        SocketChannel otherSideSocket = SocketChannel.open(destAddr);
        otherSideSocket.configureBlocking(false);
        SelectionKey otherSideSelectionKey = otherSideSocket.register(key.selector(), SelectionKey.OP_READ);


        clientSelectionKey.attach(otherSideSocket);
        otherSideSelectionKey.attach(clientSocket);

    }

    private ServerSocketChannel openServerSocket() throws Exception {
        ServerSocketChannel serverSocket = ServerSocketChannel.open();
        InetSocketAddress hostAddress = new InetSocketAddress(this.localPort);
        serverSocket.bind(hostAddress);
        serverSocket.configureBlocking(false);
        return serverSocket;
    }
}
