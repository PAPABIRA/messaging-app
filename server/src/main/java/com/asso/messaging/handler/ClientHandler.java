package com.asso.messaging.handler;

import com.asso.messaging.model.Role;
import com.asso.messaging.model.User;
import com.asso.messaging.model.UserStatus;
import com.asso.messaging.protocol.*;
import com.asso.messaging.repository.UserRepository;
import com.asso.messaging.service.AuthService;
import com.asso.messaging.service.MessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Gère la connexion réseau d'un seul client dans un thread dédié.
 *
 * RG11 : chaque client = un thread
 * RG12 : journalisation connexions, déconnexions, envois
 */
public class ClientHandler implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(ClientHandler.class);

    private final Socket socket;
    private final AuthService authService;
    private final MessageService messageService;
    private final UserRepository userRepository;

    // Map globale partagée entre tous les handlers : username → handler
    private final ConcurrentHashMap<String, ClientHandler> connectedClients;

    private ObjectInputStream in;
    private ObjectOutputStream out;

    private User currentUser; // null si non authentifié

    public ClientHandler(Socket socket,
                         AuthService authService,
                         MessageService messageService,
                         UserRepository userRepository,
                         ConcurrentHashMap<String, ClientHandler> connectedClients) {
        this.socket = socket;
        this.authService = authService;
        this.messageService = messageService;
        this.userRepository = userRepository;
        this.connectedClients = connectedClients;
    }

    @Override
    public void run() {
        log.info("Nouvelle connexion depuis {}", socket.getRemoteSocketAddress());
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            in  = new ObjectInputStream(socket.getInputStream());

            Packet packet;
            while ((packet = (Packet) in.readObject()) != null) {
                handlePacket(packet);
            }

        } catch (IOException e) {
            log.warn("Connexion perdue pour {} : {}", getUsername(), e.getMessage()); // RG10
        } catch (ClassNotFoundException e) {
            log.error("Paquet invalide reçu : {}", e.getMessage());
        } finally {
            disconnect();
        }
    }

    // ── Routage des paquets ──────────────────────────────────────────────────

    private void handlePacket(Packet packet) {
        log.debug("Paquet reçu [{}] de {}", packet.getType(), getUsername());
        switch (packet.getType()) {
            case REGISTER        -> handleRegister(packet);
            case LOGIN           -> handleLogin(packet);
            case LOGOUT          -> handleLogout();
            case SEND_MESSAGE    -> handleSendMessage(packet);
            case GET_HISTORY     -> handleGetHistory(packet);
            case GET_MEMBERS     -> handleGetMembers();
            default              -> sendError("Type de paquet inconnu : " + packet.getType());
        }
    }

    // ── Handlers métier ──────────────────────────────────────────────────────


    private void handleRegister(Packet packet) {
        String username = packet.getString("username");
        String password = packet.getString("password");
        String roleStr  = packet.getString("role");

        Role role;
        try {
            role = Role.valueOf(roleStr);
        } catch (Exception e) {
            sendError("Rôle invalide : " + roleStr);
            return;
        }

        User user = authService.register(username, password, role);
        if (user == null) {
            sendError("Le nom d'utilisateur '" + username + "' est déjà pris.");
        } else {
            send(new Packet(PacketType.SUCCESS).put("message", "Inscription réussie !"));
        }
    }

    /** RG2, RG3, RG4, RG6 */
    private void handleLogin(Packet packet) {
        String username = packet.getString("username");
        String password = packet.getString("password");

        // RG3 : vérifier qu'il n'est pas déjà connecté
        if (connectedClients.containsKey(username)) {
            sendError("Cet utilisateur est déjà connecté.");
            return;
        }

        Optional<User> opt = authService.login(username, password);
        if (opt.isEmpty()) {
            sendError("Identifiants incorrects.");
            return;
        }

        currentUser = opt.get();
        connectedClients.put(username, this);

        // RG4 : passer ONLINE
        userRepository.updateStatus(currentUser.getId(), UserStatus.ONLINE);
        currentUser.setStatus(UserStatus.ONLINE);

        log.info("CONNEXION : {} ({})", username, currentUser.getRole()); // RG12

        // Construction du DTO de réponse
        UserDTO dto = toUserDTO(currentUser);
        send(new Packet(PacketType.SUCCESS)
                .put("message", "Connexion réussie.")
                .put("user", dto));

        // Initialiser la liste complète des membres côté client dès la connexion.
        sendMembersResponse();

        // RG6 : livrer les messages en attente
        List<MessageDTO> pending = messageService.getPendingMessages(username);
        for (MessageDTO msg : pending) {
            send(new Packet(PacketType.RECEIVE_MESSAGE).put("message", msg));
        }
        if (!pending.isEmpty()) {
            log.info("{} message(s) en attente livré(s) à {}", pending.size(), username);
        }

        // Notifier tout le monde de la liste des connectés
        broadcastOnlineUsers();
    }

    private void handleLogout() {
        log.info("DÉCONNEXION volontaire : {}", getUsername()); // RG12
        disconnect();
    }

    /** RG2, RG5, RG6, RG7 */
    private void handleSendMessage(Packet packet) {
        if (!isAuthenticated()) { sendError("Non authentifié."); return; }

        String receiver = packet.getString("receiver");
        String contenu  = packet.getString("contenu");

        MessageDTO dto = messageService.sendMessage(currentUser.getUsername(), receiver, contenu);

        if (dto == null) {
            sendError("Message invalide (destinataire introuvable ou contenu incorrect).");
            return;
        }

        log.info("MESSAGE : {} → {} (id={})", currentUser.getUsername(), receiver, dto.getId()); // RG12

        // Accuser réception à l'expéditeur
        send(new Packet(PacketType.SUCCESS).put("messageId", dto.getId()));

        // RG5/RG6 : livrer en temps réel si le destinataire est connecté
        ClientHandler receiverHandler = connectedClients.get(receiver);
        if (receiverHandler != null) {
            receiverHandler.send(new Packet(PacketType.RECEIVE_MESSAGE).put("message", dto));
            messageService.markAsRead(dto.getId()); // si on veut marquer RECU immédiatement
        }
        // sinon : le message est déjà en base avec statut ENVOYE, livré à la reconnexion (RG6)
    }

    /** RG8 */
    private void handleGetHistory(Packet packet) {
        if (!isAuthenticated()) { sendError("Non authentifié."); return; }

        String withUser = packet.getString("withUser");
        List<MessageDTO> history = messageService.getHistory(currentUser.getUsername(), withUser);
        send(new Packet(PacketType.HISTORY_RESPONSE).put("history", history));
    }

    /** Retourne la liste de tous les membres inscrits (authentification requise). */
    private void handleGetMembers() {
        if (!isAuthenticated()) { sendError("Non authentifié."); return; }

        sendMembersResponse();
    }

    private void sendMembersResponse() {
        List<UserDTO> members = userRepository.findAll()
                .stream()
                .map(this::toUserDTO)
                .collect(Collectors.toList());

        send(new Packet(PacketType.MEMBERS_RESPONSE).put("members", members));
    }

    // ── Déconnexion propre ───────────────────────────────────────────────────

    private void disconnect() {
        if (currentUser != null) {
            connectedClients.remove(currentUser.getUsername());
            userRepository.updateStatus(currentUser.getId(), UserStatus.OFFLINE); // RG4
            log.info("DÉCONNEXION : {}", currentUser.getUsername()); // RG12
            broadcastOnlineUsers();
            currentUser = null;
        }
        try { socket.close(); } catch (IOException ignored) {}
    }

    // ── Diffusion ────────────────────────────────────────────────────────────

    /**
     * Envoie à tous les clients connectés la liste des utilisateurs en ligne.
     */
    private void broadcastOnlineUsers() {
        List<String> online = List.copyOf(connectedClients.keySet());
        Packet broadcast = new Packet(PacketType.ONLINE_USERS).put("users", online);
        for (ClientHandler handler : connectedClients.values()) {
            handler.send(broadcast);
        }
    }

    // ── Utilitaires ──────────────────────────────────────────────────────────

    public synchronized void send(Packet packet) {
        try {
            out.writeObject(packet);
            out.flush();
            out.reset(); // évite les problèmes de cache d'objet
        } catch (IOException e) {
            log.error("Impossible d'envoyer un paquet à {} : {}", getUsername(), e.getMessage());
        }
    }

    private boolean isAuthenticated() {
        return currentUser != null;
    }

    private String getUsername() {
        return currentUser != null ? currentUser.getUsername() : socket.getRemoteSocketAddress().toString();
    }

    private void sendError(String message) {
        send(new Packet(PacketType.ERROR).put("message", message));
    }

    private UserDTO toUserDTO(User user) {
        return new UserDTO(
                user.getId(),
                user.getUsername(),
                user.getRole(),
                user.getStatus(),
                user.getDateCreation()
        );
    }
}
