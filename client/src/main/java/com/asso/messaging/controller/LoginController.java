package com.asso.messaging.controller;

import com.asso.messaging.client.MessagingApp;
import com.asso.messaging.client.ServerConnection;
import com.asso.messaging.model.Role;
import com.asso.messaging.protocol.Packet;
import com.asso.messaging.protocol.PacketType;
import com.asso.messaging.protocol.UserDTO;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Contrôleur JavaFX pour l'écran de connexion / inscription utilisateur.
 */
public class LoginController {

    private static final Logger log = LoggerFactory.getLogger(LoginController.class);

    // ── FXML ────────────────────────────────────────────────────────────────

    @FXML private TabPane tabPane;

    // Onglet Connexion
    @FXML private TextField loginUsername;
    @FXML private PasswordField loginPassword;
    @FXML private Label loginError;
    @FXML private Button loginButton;

    // Onglet Inscription
    @FXML private TextField registerUsername;
    @FXML private PasswordField registerPassword;
    @FXML private PasswordField registerConfirmPassword;
    @FXML private ComboBox<Role> registerRole;
    @FXML private Label registerError;
    @FXML private Button registerButton;

    private ServerConnection connection;

    @FXML
    public void initialize() {
        connection = MessagingApp.getServerConnection();
        registerRole.getItems().setAll(Role.values());
        registerRole.setValue(Role.MEMBRE);

        // Écouter les réponses serveur
        connection.addPacketListener(this::handlePacket);
    }

    // ── Actions ──────────────────────────────────────────────────────────────

    @FXML
    private void onLoginClicked() {
        String username = loginUsername.getText().trim();
        String password = loginPassword.getText();

        if (username.isBlank() || password.isBlank()) {
            showLoginError("Veuillez remplir tous les champs.");
            return;
        }

        ensureConnected();
        setLoginLoading(true);

        Packet packet = new Packet(PacketType.LOGIN)
                .put("username", username)
                .put("password", password);
        connection.send(packet);
    }

    @FXML
    private void onRegisterClicked() {
        String username = registerUsername.getText().trim();
        String password = registerPassword.getText();
        String confirm  = registerConfirmPassword.getText();
        Role role       = registerRole.getValue();

        if (username.isBlank() || password.isBlank()) {
            showRegisterError("Veuillez remplir tous les champs.");
            return;
        }
        if (!password.equals(confirm)) {
            showRegisterError("Les mots de passe ne correspondent pas.");
            return;
        }

        ensureConnected();
        setRegisterLoading(true);

        Packet packet = new Packet(PacketType.REGISTER)
                .put("username", username)
                .put("password", password)
                .put("role", role.name());
        connection.send(packet);
    }

    // ── Gestion des réponses serveur ─────────────────────────────────────────

    private void handlePacket(Packet packet) {
        Platform.runLater(() -> {
            switch (packet.getType()) {
                case SUCCESS -> handleSuccess(packet);
                case ERROR   -> handleError(packet);
                default      -> {} // autres paquets gérés ailleurs
            }
        });
    }

    private void handleSuccess(Packet packet) {
        setLoginLoading(false);
        setRegisterLoading(false);

        // Si on reçoit un UserDTO, c'est une réponse LOGIN
        if (packet.get("user") instanceof UserDTO user) {
            openMainWindow(user);
        } else {
            // C'est une réponse REGISTER
            showRegisterError("✓ Inscription réussie ! Vous pouvez vous connecter.");
            tabPane.getSelectionModel().select(0);
        }
    }

    private void handleError(Packet packet) {
        setLoginLoading(false);
        setRegisterLoading(false);
        String msg = packet.getString("message");

        // Déterminer quel onglet est actif pour afficher l'erreur au bon endroit
        if (tabPane.getSelectionModel().getSelectedIndex() == 0) {
            showLoginError(msg);
        } else {
            showRegisterError(msg);
        }
    }

    // ── Navigation ───────────────────────────────────────────────────────────

    private void openMainWindow(UserDTO user) {
        try {
            connection.removePacketListener(this::handlePacket); // nettoyer le listener

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main.fxml"));
            Scene scene = new Scene(loader.load(), 900, 600);
            scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());

            MainController controller = loader.getController();
            controller.initData(user);

            Stage stage = (Stage) loginButton.getScene().getWindow();
            stage.setScene(scene);
            stage.setResizable(true);
            stage.setTitle("Messagerie G2 — " + user.getUsername() + " [" + user.getRole() + "]");

        } catch (IOException e) {
            log.error("Impossible d'ouvrir la fenêtre principale : {}", e.getMessage(), e);
        }
    }

    // ── Utilitaires UI ───────────────────────────────────────────────────────

    private void ensureConnected() {
        if (!connection.isConnected()) {
            try {
                connection.connect();
            } catch (IOException e) {
                showLoginError("Impossible de se connecter au serveur.");
                log.error("Connexion au serveur impossible : {}", e.getMessage());
            }
        }
    }

    private void showLoginError(String msg) {
        loginError.setText(msg);
        loginError.setVisible(true);
    }

    private void showRegisterError(String msg) {
        registerError.setText(msg);
        registerError.setVisible(true);
    }

    private void setLoginLoading(boolean loading) {
        loginButton.setDisable(loading);
        loginButton.setText(loading ? "Connexion…" : "Se connecter");
        loginError.setVisible(false);
    }

    private void setRegisterLoading(boolean loading) {
        registerButton.setDisable(loading);
        registerButton.setText(loading ? "Inscription…" : "S'inscrire");
        registerError.setVisible(false);
    }
}
