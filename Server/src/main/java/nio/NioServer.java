package nio;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.*;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Collectors;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;

public class NioServer {

    private final ServerSocketChannel serverChannel = ServerSocketChannel.open();
    private final Selector selector = Selector.open();
    private final ByteBuffer buffer = ByteBuffer.allocate(5);
    private Path serverPath = Paths.get("serverDir");

    public NioServer() throws IOException {
        serverChannel.bind(new InetSocketAddress(8189));
        serverChannel.configureBlocking(false);
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        while (serverChannel.isOpen()) {
            selector.select(); // block
            Set<SelectionKey> keys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = keys.iterator();
            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                iterator.remove();
                if (key.isAcceptable()) {
                    handleAccept(key);
                }
                if (key.isReadable()) {
                    handleRead(key);
                }
            }
        }
    }

    public static void main(String[] args) throws IOException {
        new NioServer();
    }

    private void handleRead(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        int read = 0;
        StringBuilder msg = new StringBuilder();
        while ((read = channel.read(buffer)) > 0) {
            buffer.flip();
            while (buffer.hasRemaining()) {
                msg.append((char) buffer.get());
            }
            buffer.clear();
        }
        String command = msg.toString().replaceAll("[\n|\r]", "");
        System.out.println(command);
        if (command.equals("ls")) {
            String files = Files.list(serverPath)
                    .map(path -> path.getFileName().toString())
                    .collect(Collectors.joining(", "));
            files += System.lineSeparator();
            channel.write(ByteBuffer.wrap(files.getBytes(StandardCharsets.UTF_8)));
        }
        if (command.startsWith("cd")) {
            String[] args = command.split(" ");
            if (args.length != 2) {
                channel.write(ByteBuffer.wrap("Wrong command\n".getBytes(StandardCharsets.UTF_8)));
            } else {
                String targetPath = args[1];
                Path serverDirBefore = serverPath;
                serverPath = serverPath.resolve(targetPath);
                if (!Files.isDirectory(serverPath) && !Files.exists(serverPath)) {
                    channel.write(ByteBuffer.wrap("Wrong arg for cd command\n".getBytes(StandardCharsets.UTF_8)));
                    serverPath = serverDirBefore;
                }
            }
        }
        if(command.startsWith("cat")){
            String[] args = command.split(" ");
            if (args.length != 2) {
                channel.write(ByteBuffer.wrap("Wrong command\n".getBytes(StandardCharsets.UTF_8)));
            } else {
                String targetPath = args[1];
                Path myPath = Paths.get(serverPath.toString()+"\\"+targetPath);
                if (!Files.isDirectory(serverPath) && !Files.exists(serverPath)) {
                    channel.write(ByteBuffer.wrap("Wrong arg for cd command\n".getBytes(StandardCharsets.UTF_8)));
                }
                else{
                    channel.write(ByteBuffer.wrap(Files.lines(myPath)
                            .collect(Collectors.toSet()).toString().getBytes(StandardCharsets.UTF_8)));
                }
            }
        }

        if(command.startsWith("touch")){
            String[] args = command.split(" ");
            if (args.length != 2) {
                channel.write(ByteBuffer.wrap("Wrong command\n".getBytes(StandardCharsets.UTF_8)));
            } else {
                String targetPath = args[1];
                Path myPath = Paths.get(serverPath.toString()+"\\"+targetPath);
                if (!Files.exists(myPath)) {
                    channel.write(ByteBuffer.wrap("Wrong arg for touch command\n".getBytes(StandardCharsets.UTF_8)));
                }
                else {
                    BasicFileAttributeView basicView = Files.getFileAttributeView(myPath, BasicFileAttributeView.class);
                    BasicFileAttributes basicAttribs = basicView.readAttributes();
                    String data = "size  = "+
                            basicAttribs.size()+" byte;  create time"+
                            basicAttribs.creationTime() + System.lineSeparator();
                    channel.write(ByteBuffer.wrap(data.getBytes(StandardCharsets.UTF_8)));
                }
            }
        }

    }

    private void handleAccept(SelectionKey key) throws IOException {
        SocketChannel channel = ((ServerSocketChannel) key.channel()).accept();
        channel.configureBlocking(false);
        //System.out.println("Connect good");
        channel.register(selector, SelectionKey.OP_READ);
    }
}

