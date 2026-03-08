package com.asso.messaging.client;

import com.asso.messaging.protocol.Packet;
import com.asso.messaging.protocol.PacketType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Gère la connexion socket avec le serveur.
 * Écoute les paquets entrants dans un thread dédié et
 * notifie les listeners enregistrés.
 *
 * en cas de perte de connexion.
 */
public class ServerConnection {

    private static final Logger log = LoggerFactory.getLogger(ServerConnection.class);

    private static final String SERVER_HOST = "localhost";
    private static final int    SERVER_PORT = 9090;

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream  in;
    private Thread listenerThread;
    private volatile boolean running = false;

    // Listeners notifiés lors de la réception d'un paquet
    private final CopyOnWriteArrayList<Consumer<Packet>> packetListeners = new CopyOnWriteArrayList<>();

    // Listener notifié en cas de déconnexion (RG10)
    private Runnable disconnectCallback;

    /**
     * Ouvre la connexion au serveur.
     * @throws IOException si la connexion échoue
     */
    public void connect() throws IOException {
        socket = new Socket(SERVER_HOST, SERVER_PORT);
        out = new ObjectOutputStream(socket.getOutputStream());
        in  = new ObjectInputStream(socket.getInputStream());
        running = true;

        listenerThread = new Thread(this::listenLoop, "client-listener");
        listenerThread.setDaemon(true);
        listenerThread.start();

        log.info("Connecté au serveur {}:{}", SERVER_HOST, SERVER_PORT);
    }

    /**
     * Boucle d'écoute dans le thread listener.
     */
    private void listenLoop() {
        try {
            while (running) {
                Packet packet = (Packet) in.readObject();
                log.debug("Paquet reçu : {}", packet.getType());
                notifyListeners(packet);
            }
        } catch (IOException e) {
            if (running) {
                log.warn("Connexion perdue : {}", e.getMessage()); // RG10
                running = false;
                if (disconnectCallback != null) {
                    disconnectCallback.run();
                }
            }
        } catch (ClassNotFoundException e) {
            log.error("Paquet inconnu reçu : {}", e.getMessage());
        }
    }

    /**
     * Envoie un paquet au serveur.
     */
    public synchronized void send(Packet packet) {
        try {
            out.writeObject(packet);
            out.flush();
            out.reset();
        } catch (IOException e) {
            log.error("Erreur d'envoi : {}", e.getMessage());
            if (disconnectCallback != null) disconnectCallback.run();
        }
    }

    /**
     * Ferme proprement la connexion.
     */
    public void disconnect() {
        running = false;
        try {
            send(new Packet(PacketType.LOGOUT));
            if (socket != null) socket.close();
        } catch (Exception ignored) {}
    }

    // ── Gestion des listeners ───────────────────────────────────────────────

    public void addPacketListener(Consumer<Packet> listener) {
        packetListeners.add(listener);
    }

    public void removePacketListener(Consumer<Packet> listener) {
        packetListeners.remove(listener);
    }

    public void setDisconnectCallback(Runnable callback) {
        this.disconnectCallback = callback;
    }

    private void notifyListeners(Packet packet) {
        for (Consumer<Packet> listener : packetListeners) {
            listener.accept(packet);
        }
    }

    public boolean isConnected() {
        return running && socket != null && !socket.isClosed();
    }
}
