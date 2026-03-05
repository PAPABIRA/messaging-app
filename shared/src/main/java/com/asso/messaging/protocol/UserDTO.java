package com.asso.messaging.protocol;

import com.asso.messaging.model.Role;
import com.asso.messaging.model.UserStatus;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * DTO (Data Transfer Object) représentant un utilisateur côté réseau.
 * Ne contient jamais le mot de passe.
 */
public class UserDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String username;
    private Role role;
    private UserStatus status;
    private LocalDateTime dateCreation;

    public UserDTO() {}

    public UserDTO(Long id, String username, Role role, UserStatus status, LocalDateTime dateCreation) {
        this.id = id;
        this.username = username;
        this.role = role;
        this.status = status;
        this.dateCreation = dateCreation;
    }

    // ── Getters / Setters ───────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) { this.status = status; }

    public LocalDateTime getDateCreation() { return dateCreation; }
    public void setDateCreation(LocalDateTime dateCreation) { this.dateCreation = dateCreation; }

    @Override
    public String toString() {
        return username + " [" + role + " | " + status + "]";
    }
}
