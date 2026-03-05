package com.asso.messaging.repository;

import com.asso.messaging.model.Message;
import com.asso.messaging.model.MessageStatus;
import com.asso.messaging.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Repository gérant la persistance des messages via Hibernate.
 */
public class MessageRepository {

    private static final Logger log = LoggerFactory.getLogger(MessageRepository.class);

    /**
     * Persiste un nouveau message en base.
     */
    public Message save(Message message) {
        Transaction tx = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            session.persist(message);
            tx.commit();
            log.debug("Message sauvegardé id={}", message.getId());
            return message;
        } catch (Exception e) {
            if (tx != null) tx.rollback();
            log.error("Erreur sauvegarde message : {}", e.getMessage());
            throw new RuntimeException("Erreur lors de la sauvegarde du message", e);
        }
    }

    /**
     * Retourne la conversation entre deux utilisateurs, triée chronologiquement (RG8).
     */
    public List<Message> findConversation(String user1, String user2) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                            "FROM Message m WHERE " +
                            "(m.sender.username = :u1 AND m.receiver.username = :u2) OR " +
                            "(m.sender.username = :u2 AND m.receiver.username = :u1) " +
                            "ORDER BY m.dateEnvoi ASC", Message.class)
                    .setParameter("u1", user1)
                    .setParameter("u2", user2)
                    .list();
        } catch (Exception e) {
            log.error("Erreur récupération conversation {} ↔ {} : {}", user1, user2, e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * Retourne les messages en attente pour un destinataire (statut ENVOYE) — RG6.
     */
    public List<Message> findPendingMessages(String receiverUsername) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                            "FROM Message m WHERE m.receiver.username = :receiver " +
                            "AND m.statut = :statut ORDER BY m.dateEnvoi ASC", Message.class)
                    .setParameter("receiver", receiverUsername)
                    .setParameter("statut", MessageStatus.ENVOYE)
                    .list();
        } catch (Exception e) {
            log.error("Erreur récupération messages en attente pour {} : {}", receiverUsername, e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * Met à jour le statut d'un message (ENVOYE → RECU → LU).
     */
    public void updateStatus(Long messageId, MessageStatus status) {
        Transaction tx = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            Message msg = session.get(Message.class, messageId);
            if (msg != null) {
                msg.setStatut(status);
                session.merge(msg);
            }
            tx.commit();
        } catch (Exception e) {
            if (tx != null) tx.rollback();
            log.error("Erreur mise à jour statut message {} : {}", messageId, e.getMessage());
        }
    }

    /**
     * Met à jour en masse les messages en attente vers RECU (lors de la reconnexion du destinataire).
     */
    public void markAsReceived(String receiverUsername) {
        Transaction tx = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            session.createMutationQuery(
                            "UPDATE Message m SET m.statut = :recu " +
                            "WHERE m.receiver.username = :receiver AND m.statut = :envoye")
                    .setParameter("recu", MessageStatus.RECU)
                    .setParameter("receiver", receiverUsername)
                    .setParameter("envoye", MessageStatus.ENVOYE)
                    .executeUpdate();
            tx.commit();
        } catch (Exception e) {
            if (tx != null) tx.rollback();
            log.error("Erreur markAsReceived pour {} : {}", receiverUsername, e.getMessage());
        }
    }
}
