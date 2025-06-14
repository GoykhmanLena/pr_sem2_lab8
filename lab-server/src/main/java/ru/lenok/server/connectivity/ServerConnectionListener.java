package ru.lenok.server.connectivity;

import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.lenok.common.util.SerializationUtils;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.Arrays;
import java.util.concurrent.ForkJoinPool;

@Data
public class ServerConnectionListener{
    private static final int THREAD_COUNT = Runtime.getRuntime().availableProcessors();
    ;
    private static final Logger logger = LoggerFactory.getLogger(ServerConnectionListener.class);
    private final int port;
    private final DatagramSocket socket;
    private final ForkJoinPool pool = new ForkJoinPool();
    private ForkJoinPool forkJoinPool;


    public ServerConnectionListener(int port) throws Exception {
        this.port = port;
        this.socket = new DatagramSocket(port);
        logger.info("UDP сервер запущен на порту " + port);
    }


    public IncomingMessage listenAndReceiveMessage() {
        try {
            byte[] buffer = new byte[SerializationUtils.BUFFER_SIZE];
            DatagramPacket packetFromClient = new DatagramPacket(buffer, buffer.length);

            synchronized (socket) {
                logger.info("Жду сообщения");
                socket.receive(packetFromClient);
            }
            byte[] actualData = Arrays.copyOfRange(buffer, 0, packetFromClient.getLength());
            Object dataFromClient = SerializationUtils.INSTANCE.deserialize(actualData);
            logger.info("Получено: " + dataFromClient);

            IncomingMessage incomingMessage = new IncomingMessage(dataFromClient, packetFromClient.getAddress(), packetFromClient.getPort());
            return incomingMessage;
        } catch (IOException e) {
            logger.error("Ошибка: " + e);
        }
        return null;
    }
}
