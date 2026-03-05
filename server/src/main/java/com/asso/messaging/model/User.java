package com.asso.messaging.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Entité JPA représentant un membre de l'association.
 *
 * RG1  : username unique
 * RG9  : mot de passe stocké haché (BCrypt)
 * RG4  : status géré à la connexion/déconnexion
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String username;

    @Column(nullable = false)
    private String password; // BCrypt hash

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status = UserStatus.OFFLINE;

    @Column(nullable = false)
    private LocalDateTime dateCreation;

    public User() {}

    public User(String username, String password, Role role) {
        this.username = username;
        this.password = password;
        this.role = role;
        this.status = UserStatus.OFFLINE;
        this.dateCreation = LocalDateTime.now();
    }

    // ── Getters / Setters ───────────────────────────────────────────────────

    public Long getId() { return id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) { this.status = status; }

    public LocalDateTime getDateCreation() { return dateCreation; }
    public void setDateCreation(LocalDateTime dateCreation) { this.dateCreation = dateCreation; }

    @Override
    public String toString() {
        return "User{id=" + id + ", username='" + username + "', role=" + role + ", status=" + status + '}';
    }
}
