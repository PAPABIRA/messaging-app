package com.asso.messaging.repository;

import com.asso.messaging.model.User;
import com.asso.messaging.model.UserStatus;
import com.asso.messaging.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * Repository gérant la persistance des utilisateurs via Hibernate.
 */
public class UserRepository {

    private static final Logger log = LoggerFactory.getLogger(UserRepository.class);

    /**
     * Enregistre un nouvel utilisateur en base.
     */
    public User save(User user) {
        Transaction tx = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            session.persist(user);
            tx.commit();
            log.debug("Utilisateur sauvegardé : {}", user.getUsername());
            return user;
        } catch (Exception e) {
            if (tx != null) tx.rollback();
            log.error("Erreur sauvegarde utilisateur : {}", e.getMessage());
            throw new RuntimeException("Erreur lors de la sauvegarde de l'utilisateur", e);
        }
    }

    /**
     * Recherche un utilisateur par son username (RG1 : unicité).
     */
    public Optional<User> findByUsername(String username) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            User user = session.createQuery(
                            "FROM User u WHERE u.username = :username", User.class)
                    .setParameter("username", username)
                    .uniqueResult();
            return Optional.ofNullable(user);
        } catch (Exception e) {
            log.error("Erreur recherche par username '{}' : {}", username, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Retourne tous les utilisateurs (RG13 : réservé ORGANISATEUR).
     */
    public List<User> findAll() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery("FROM User ORDER BY username", User.class).list();
        } catch (Exception e) {
            log.error("Erreur récupération de tous les utilisateurs : {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * Met à jour le statut d'un utilisateur (ONLINE/OFFLINE).
     * RG4 : appelé à chaque connexion / déconnexion.
     */
    public void updateStatus(Long userId, UserStatus status) {
        Transaction tx = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            User user = session.get(User.class, userId);
            if (user != null) {
                user.setStatus(status);
                session.merge(user);
            }
            tx.commit();
        } catch (Exception e) {
            if (tx != null) tx.rollback();
            log.error("Erreur mise à jour statut utilisateur {} : {}", userId, e.getMessage());
        }
    }

    /**
     * Vérifie si un username est déjà pris (RG1).
     */
    public boolean existsByUsername(String username) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Long count = session.createQuery(
                            "SELECT COUNT(u) FROM User u WHERE u.username = :username", Long.class)
                    .setParameter("username", username)
                    .uniqueResult();
            return count != null && count > 0;
        } catch (Exception e) {
            log.error("Erreur vérification existence username : {}", e.getMessage());
            return false;
        }
    }
}
