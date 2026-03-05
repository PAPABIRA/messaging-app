package com.asso.messaging.protocol;

import com.asso.messaging.model.MessageStatus;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * DTO représentant un message échangé entre deux utilisateurs.
 */
public class MessageDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String senderUsername;
    private String receiverUsername;
    private String contenu;
    private LocalDateTime dateEnvoi;
    private MessageStatus statut;

    public MessageDTO() {}

    public MessageDTO(Long id, String senderUsername, String receiverUsername,
                      String contenu, LocalDateTime dateEnvoi, MessageStatus statut) {
        this.id = id;
        this.senderUsername = senderUsername;
        this.receiverUsername = receiverUsername;
        this.contenu = contenu;
        this.dateEnvoi = dateEnvoi;
        this.statut = statut;
    }

    // ── Getters / Setters ───────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSenderUsername() { return senderUsername; }
    public void setSenderUsername(String senderUsername) { this.senderUsername = senderUsername; }

    public String getReceiverUsername() { return receiverUsername; }
    public void setReceiverUsername(String receiverUsername) { this.receiverUsername = receiverUsername; }

    public String getContenu() { return contenu; }
    public void setContenu(String contenu) { this.contenu = contenu; }

    public LocalDateTime getDateEnvoi() { return dateEnvoi; }
    public void setDateEnvoi(LocalDateTime dateEnvoi) { this.dateEnvoi = dateEnvoi; }

    public MessageStatus getStatut() { return statut; }
    public void setStatut(MessageStatus statut) { this.statut = statut; }
}
