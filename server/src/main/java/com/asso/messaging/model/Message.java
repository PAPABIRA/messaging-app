package com.asso.messaging.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Entité JPA représentant un message échangé entre deux utilisateurs de l'application.
 *
 * RG7 : contenu non vide, max 1000 caractères
 * RG6 : messages hors-ligne conservés en base et livrés à la reconnexion
 */
@Entity
@Table(name = "messages")
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "receiver_id", nullable = false)
    private User receiver;

    @Column(nullable = false, length = 1000)
    private String contenu;

    @Column(nullable = false)
    private LocalDateTime dateEnvoi;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageStatus statut = MessageStatus.ENVOYE;

    public Message() {}

    public Message(User sender, User receiver, String contenu) {
        this.sender = sender;
        this.receiver = receiver;
        this.contenu = contenu;
        this.dateEnvoi = LocalDateTime.now();
        this.statut = MessageStatus.ENVOYE;
    }

    // ── Getters / Setters ───────────────────────────────────────────────────

    public Long getId() { return id; }

    public User getSender() { return sender; }
    public void setSender(User sender) { this.sender = sender; }

    public User getReceiver() { return receiver; }
    public void setReceiver(User receiver) { this.receiver = receiver; }

    public String getContenu() { return contenu; }
    public void setContenu(String contenu) { this.contenu = contenu; }

    public LocalDateTime getDateEnvoi() { return dateEnvoi; }
    public void setDateEnvoi(LocalDateTime dateEnvoi) { this.dateEnvoi = dateEnvoi; }

    public MessageStatus getStatut() { return statut; }
    public void setStatut(MessageStatus statut) { this.statut = statut; }

    @Override
    public String toString() {
        return "Message{id=" + id + ", from=" + sender.getUsername()
                + ", to=" + receiver.getUsername()
                + ", statut=" + statut + '}';
    }
}
