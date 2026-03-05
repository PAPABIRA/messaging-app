package com.asso.messaging.service;

import com.asso.messaging.model.Message;
import com.asso.messaging.model.MessageStatus;
import com.asso.messaging.model.User;
import com.asso.messaging.protocol.MessageDTO;
import com.asso.messaging.repository.MessageRepository;
import com.asso.messaging.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service métier pour la gestion des messages.
 *
 * RG5 : expéditeur connecté, destinataire existant
 * RG6 : messages hors-ligne conservés
 * RG7 : validation contenu
 * RG8 : historique chronologique
 */
public class MessageService {

    private static final Logger log = LoggerFactory.getLogger(MessageService.class);
    private static final int MAX_CONTENT_LENGTH = 1000;

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;

    public MessageService(MessageRepository messageRepository, UserRepository userRepository) {
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
    }

    /**
     * Envoie un message d'un expéditeur vers un destinataire.
     * RG7 : validation du contenu.
     * RG5 : vérification de l'existence du destinataire.
     *
     * @return Le MessageDTO persisté, ou null si la validation échoue
     */
    public MessageDTO sendMessage(String senderUsername, String receiverUsername, String contenu) {
        // RG7 : contenu non vide, max 1000 caractères
        if (contenu == null || contenu.isBlank()) {
            log.warn("Message vide refusé de {} vers {}", senderUsername, receiverUsername);
            return null;
        }
        if (contenu.length() > MAX_CONTENT_LENGTH) {
            log.warn("Message trop long ({} chars) de {} vers {}", contenu.length(), senderUsername, receiverUsername);
            return null;
        }

        // RG5 : vérifier l'existence des utilisateurs
        Optional<User> senderOpt = userRepository.findByUsername(senderUsername);
        Optional<User> receiverOpt = userRepository.findByUsername(receiverUsername);

        if (senderOpt.isEmpty()) {
            log.error("Expéditeur introuvable : {}", senderUsername);
            return null;
        }
        if (receiverOpt.isEmpty()) {
            log.warn("Destinataire introuvable : {}", receiverUsername);
            return null;
        }

        Message message = new Message(senderOpt.get(), receiverOpt.get(), contenu);
        message = messageRepository.save(message);
        log.info("Message {} → {} sauvegardé (id={})", senderUsername, receiverUsername, message.getId());

        return toDTO(message);
    }

    /**
     * Retourne l'historique de conversation entre deux utilisateurs (RG8).
     */
    public List<MessageDTO> getHistory(String user1, String user2) {
        return messageRepository.findConversation(user1, user2)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Récupère les messages en attente pour un utilisateur qui vient de se connecter (RG6).
     */
    public List<MessageDTO> getPendingMessages(String username) {
        List<Message> pending = messageRepository.findPendingMessages(username);
        messageRepository.markAsReceived(username);
        return pending.stream().map(this::toDTO).collect(Collectors.toList());
    }

    /**
     * Marque un message comme LU.
     */
    public void markAsRead(Long messageId) {
        messageRepository.updateStatus(messageId, MessageStatus.LU);
    }

    // ── Conversion entité → DTO ──────────────────────────────────────────────

    private MessageDTO toDTO(Message m) {
        return new MessageDTO(
                m.getId(),
                m.getSender().getUsername(),
                m.getReceiver().getUsername(),
                m.getContenu(),
                m.getDateEnvoi(),
                m.getStatut()
        );
    }
}
