package com.asso.messaging.server;

import com.asso.messaging.handler.ClientHandler;
import com.asso.messaging.repository.MessageRepository;
import com.asso.messaging.repository.UserRepository;
import com.asso.messaging.service.AuthService;
import com.asso.messaging.service.MessageService;
import com.asso.messaging.util.HibernateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


 // Le Point d'entrée du serveur de messagerie.

public class MessagingServer {

    private static final Logger log = LoggerFactory.getLogger(MessagingServer.class);
    private static final int PORT = 9090;

    private final UserRepository    userRepository    = new UserRepository();
    private final MessageRepository messageRepository = new MessageRepository();
    private final AuthService       authService       = new AuthService(userRepository);
    private final MessageService    messageService    = new MessageService(messageRepository, userRepository);

    // Map partagée : username → ClientHandler (RG3)
    private final ConcurrentHashMap<String, ClientHandler> connectedClients = new ConcurrentHashMap<>();

    // Pool de threads pour gérer les clients (RG11)
    private final ExecutorService threadPool = Executors.newCachedThreadPool();

    public void start() {
        log.info("═══════════════════════════════════════════");
        log.info(" Démarrage du serveur de messagerie G2");
        log.info(" Port : {}", PORT);
        log.info("═══════════════════════════════════════════");

        // Initialiser Hibernate au démarrage
        HibernateUtil.getSessionFactory();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            log.info("Serveur en écoute sur le port {}…", PORT);

            // Hook d'arrêt propre
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Arrêt du serveur…");
                threadPool.shutdown();
                HibernateUtil.shutdown();
            }));

            while (!serverSocket.isClosed()) {
                Socket clientSocket = serverSocket.accept(); // bloquant
                log.info("Client accepté : {}", clientSocket.getRemoteSocketAddress()); // RG12

                ClientHandler handler = new ClientHandler(
                        clientSocket,
                        authService,
                        messageService,
                        userRepository,
                        connectedClients
                );
                threadPool.submit(handler); // RG11 : thread dédié
            }

        } catch (IOException e) {
            log.error("Erreur fatale du serveur : {}", e.getMessage(), e);
        }
    }

    public static void main(String[] args) {
        new MessagingServer().start();
    }
}
