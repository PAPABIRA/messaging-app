package com.asso.messaging.service;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.asso.messaging.model.Role;
import com.asso.messaging.model.User;
import com.asso.messaging.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Service gérant l'inscription et la connexion des utilisateurs de l'application.
 *
 * RG1  : unicité du username
 * RG9  : mot de passe haché BCrypt
 */
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private final UserRepository userRepository;

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Inscrit un nouvel utilisateur.
     *
     * @return L'utilisateur créé, ou null si le username est déjà pris (RG1)
     */
    public User register(String username, String rawPassword, Role role) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Le username ne peut pas être vide.");
        }
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("Le mot de passe ne peut pas être vide.");
        }

        // RG1 : vérifier l'unicité
        if (userRepository.existsByUsername(username)) {
            log.warn("Tentative d'inscription avec un username existant : {}", username);
            return null;
        }

        // RG9 : hachage BCrypt
        String hashedPassword = BCrypt.withDefaults().hashToString(12, rawPassword.toCharArray());

        User user = new User(username, hashedPassword, role);
        User saved = userRepository.save(user);
        log.info("Nouvel utilisateur inscrit : {} ({})", username, role);
        return saved;
    }

    /**
     * Authentifie un utilisateur.
     *
     * @return L'utilisateur si les credentials sont corrects, ou empty sinon
     */
    public Optional<User> login(String username, String rawPassword) {
        Optional<User> opt = userRepository.findByUsername(username);
        if (opt.isEmpty()) {
            log.warn("Tentative de connexion avec username inconnu : {}", username);
            return Optional.empty();
        }

        User user = opt.get();
        BCrypt.Result result = BCrypt.verifyer().verify(rawPassword.toCharArray(), user.getPassword());

        if (!result.verified) {
            log.warn("Mot de passe incorrect pour l'utilisateur : {}", username);
            return Optional.empty();
        }

        log.info("Authentification réussie : {}", username);
        return Optional.of(user);
    }
}
