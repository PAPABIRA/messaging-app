package com.asso.messaging.controller;

import com.asso.messaging.client.MessagingApp;
import com.asso.messaging.client.ServerConnection;
import com.asso.messaging.model.Role;
import com.asso.messaging.model.UserStatus;
import com.asso.messaging.protocol.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Contrôleur principal : liste des membres en ligne, messagerie, historique.
 */
public class MainController {

    private static final Logger log = LoggerFactory.getLogger(MainController.class);

    // ── FXML ────────────────────────────────────────────────────────────────

    @FXML private Label labelUsername;
    @FXML private Label labelRole;
    @FXML private ListView<UserDTO> listOnlineUsers;
    @FXML private Button btnAllMembers;     // visible ORGANISATEUR seulement
    @FXML private VBox chatBox;
    @FXML private ScrollPane chatScroll;
    @FXML private Label labelChatWith;
    @FXML private TextField inputMessage;
    @FXML private Button btnSend;
    @FXML private Button btnLogout;

    private UserDTO currentUser;
    private String  chattingWith; // username du destinataire courant
    private ServerConnection connection;
    private boolean suppressSelectionEvents;
    private boolean showMembersDialogOnNextResponse;

    // ── Initialisation ───────────────────────────────────────────────────────

    public void initData(UserDTO user) {
        this.currentUser = user;
        this.connection  = MessagingApp.getServerConnection();

        labelUsername.setText(user.getUsername());
        labelRole.setText(user.getRole().name());

        // RG13 : bouton membres uniquement pour ORGANISATEUR
        btnAllMembers.setVisible(user.getRole() == Role.ORGANISATEUR);

        configureUsersListCellFactory();

        // La sélection clavier/souris/programmatique déclenche l'ouverture conversation.
        listOnlineUsers.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldValue, newValue) -> {
                    if (!suppressSelectionEvents) {
                        onUserSelected(newValue != null ? newValue.getUsername() : null);
                    }
                }
        );

        connection.addPacketListener(this::handlePacket);
        connection.send(new Packet(PacketType.GET_MEMBERS));

        // RG10 : callback déconnexion serveur
        connection.setDisconnectCallback(() -> Platform.runLater(this::onServerDisconnected));
    }

    // ── Réception des paquets ─────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void handlePacket(Packet packet) {
        Platform.runLater(() -> {
            switch (packet.getType()) {
                case ONLINE_USERS -> {
                    List<String> users = (List<String>) packet.get("users");
                    refreshOnlineUsers(users);
                }
                case RECEIVE_MESSAGE -> {
                    MessageDTO msg = (MessageDTO) packet.get("message");
                    onMessageReceived(msg);
                }
                case HISTORY_RESPONSE -> {
                    List<MessageDTO> history = (List<MessageDTO>) packet.get("history");
                    displayHistory(history);
                }
                case MEMBERS_RESPONSE -> {
                    List<UserDTO> members = (List<UserDTO>) packet.get("members");
                    refreshMembersList(members);
                    if (showMembersDialogOnNextResponse) {
                        showMembersDialogOnNextResponse = false;
                        showMembersDialog(members);
                    }
                }
                case ERROR -> {
                    String message = packet.getString("message");
                    if (!shouldIgnoreServerError(message)) {
                        showAlert(Alert.AlertType.ERROR, "Erreur", message);
                    }
                }
                default -> {}
            }
        });
    }

    // ── Actions UI ──────────────────────────────────────────────────────────

    @FXML
    private void onSendMessage() {
        if (chattingWith == null) {
            showAlert(Alert.AlertType.WARNING, "Attention", "Sélectionnez un utilisateur à qui écrire.");
            return;
        }

        String text = inputMessage.getText().trim();
        if (text.isBlank()) return;
        if (text.length() > 1000) {
            showAlert(Alert.AlertType.WARNING, "Message trop long", "Maximum 1000 caractères.");
            return;
        }

        Packet packet = new Packet(PacketType.SEND_MESSAGE)
                .put("receiver", chattingWith)
                .put("contenu", text);
        connection.send(packet);

        // Afficher immédiatement le message envoyé (optimistic UI)
        addMessageBubble(currentUser.getUsername(), text, true);
        inputMessage.clear();
    }

    @FXML
    private void onUserSelected() {
        UserDTO selected = listOnlineUsers.getSelectionModel().getSelectedItem();
        onUserSelected(selected != null ? selected.getUsername() : null);
    }

    @FXML
    private void onGetAllMembers() {
        showMembersDialogOnNextResponse = true;
        connection.send(new Packet(PacketType.GET_MEMBERS));
    }

    @FXML
    private void onLogout() {
        connection.disconnect();
        openLoginWindow();
    }

    // ── Réponses serveur ─────────────────────────────────────────────────────

    private void refreshOnlineUsers(List<String> users) {
        String activeConversation = chattingWith;
        String selectedUsername = null;
        UserDTO selected = listOnlineUsers.getSelectionModel().getSelectedItem();
        if (selected != null) {
            selectedUsername = selected.getUsername();
        }

        List<UserDTO> updated = new ArrayList<>(listOnlineUsers.getItems());
        for (String onlineUsername : users) {
            boolean exists = updated.stream().anyMatch(u -> u.getUsername().equals(onlineUsername));
            if (!exists) {
                // Fallback: permet d'afficher au moins les connectés même sans MEMBERS_RESPONSE.
                updated.add(new UserDTO(null, onlineUsername, Role.MEMBRE, UserStatus.ONLINE, null));
            }
        }
        for (UserDTO user : updated) {
            user.setStatus(users.contains(user.getUsername()) ? UserStatus.ONLINE : UserStatus.OFFLINE);
        }
        updated.sort(byStatusThenName());

        suppressSelectionEvents = true;
        try {
            listOnlineUsers.getItems().setAll(updated);
            restoreSelection(activeConversation, selectedUsername);
        } finally {
            suppressSelectionEvents = false;
        }

        if (activeConversation != null && !containsUsername(updated, activeConversation)) {
            clearActiveConversation();
        }
    }

    private void onMessageReceived(MessageDTO msg) {
        boolean incomingForCurrentUser = currentUser.getUsername().equals(msg.getReceiverUsername());

        if (chattingWith == null && incomingForCurrentUser) {
            // Ouvrir automatiquement la conversation à la première réception.
            openConversation(msg.getSenderUsername());
            return;
        }

        // Afficher uniquement si la conversation est active
        if (chattingWith != null &&
            (msg.getSenderUsername().equals(chattingWith) || msg.getReceiverUsername().equals(chattingWith))) {
            boolean isOwn = msg.getSenderUsername().equals(currentUser.getUsername());
            addMessageBubble(msg.getSenderUsername(), msg.getContenu(), isOwn);
        } else {
            // Notification discrète
            log.info("Nouveau message de {}", msg.getSenderUsername());
            listOnlineUsers.refresh(); // peut indiquer une notification
        }
    }

    private void displayHistory(List<MessageDTO> history) {
        chatBox.getChildren().clear();
        for (MessageDTO msg : history) {
            boolean isOwn = msg.getSenderUsername().equals(currentUser.getUsername());
            addMessageBubble(msg.getSenderUsername(), msg.getContenu(), isOwn);
        }
        scrollToBottom();
    }

    private void showMembersDialog(List<UserDTO> members) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Tous les membres inscrits");
        dialog.setHeaderText("Liste complète (" + members.size() + " membres)");

        TableView<UserDTO> table = new TableView<>();

        TableColumn<UserDTO, String> colName = new TableColumn<>("Username");
        colName.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getUsername()));
        colName.setPrefWidth(150);

        TableColumn<UserDTO, String> colRole = new TableColumn<>("Rôle");
        colRole.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getRole().name()));
        colRole.setPrefWidth(120);

        TableColumn<UserDTO, String> colStatus = new TableColumn<>("Statut");
        colStatus.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getStatus().name()));
        colStatus.setPrefWidth(100);

        table.getColumns().addAll(colName, colRole, colStatus);
        table.getItems().addAll(members);
        table.setPrefSize(400, 300);

        dialog.getDialogPane().setContent(table);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    // ── Bulles de messages ────────────────────────────────────────────────────

    private void addMessageBubble(String sender, String text, boolean isOwn) {
        VBox bubble = new VBox(2);
        bubble.setMaxWidth(450);

        Label nameLabel = new Label(isOwn ? "Moi" : sender);
        nameLabel.getStyleClass().add("msg-sender");

        Label textLabel = new Label(text);
        textLabel.setWrapText(true);
        textLabel.getStyleClass().add(isOwn ? "msg-bubble-own" : "msg-bubble-other");

        bubble.getChildren().addAll(nameLabel, textLabel);

        HBox row = new HBox(bubble);
        row.setPadding(new Insets(4, 12, 4, 12));
        row.setAlignment(isOwn ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        chatBox.getChildren().add(row);
        scrollToBottom();
    }

    private void scrollToBottom() {
        Platform.runLater(() -> chatScroll.setVvalue(1.0));
    }

    private void onUserSelected(String selected) {
        if (selected == null) return;
        if (selected.equals(currentUser.getUsername())) {
            listOnlineUsers.getSelectionModel().clearSelection();
            return;
        }
        if (selected.equals(chattingWith)) return;
        openConversation(selected);
    }

    private void openConversation(String username) {
        if (username == null || username.equals(currentUser.getUsername())) return;

        chattingWith = username;
        labelChatWith.setText("Conversation avec " + username);
        chatBox.getChildren().clear();

        suppressSelectionEvents = true;
        try {
            findUserByUsername(username).ifPresent(user -> listOnlineUsers.getSelectionModel().select(user));
        } finally {
            suppressSelectionEvents = false;
        }

        // Demander l'historique (RG8)
        Packet packet = new Packet(PacketType.GET_HISTORY).put("withUser", username);
        connection.send(packet);
    }

    private void refreshMembersList(List<UserDTO> members) {
        String activeConversation = chattingWith;
        String selectedUsername = null;
        UserDTO selected = listOnlineUsers.getSelectionModel().getSelectedItem();
        if (selected != null) {
            selectedUsername = selected.getUsername();
        }

        List<UserDTO> ordered = new ArrayList<>(members);
        ordered.sort(byStatusThenName());

        suppressSelectionEvents = true;
        try {
            listOnlineUsers.getItems().setAll(ordered);
            restoreSelection(activeConversation, selectedUsername);
        } finally {
            suppressSelectionEvents = false;
        }

        if (activeConversation != null && !containsUsername(ordered, activeConversation)) {
            clearActiveConversation();
        }
    }

    private void restoreSelection(String activeConversation, String previousSelection) {
        String candidate = activeConversation != null ? activeConversation : previousSelection;
        if (candidate == null) {
            listOnlineUsers.getSelectionModel().clearSelection();
            return;
        }
        findUserByUsername(candidate)
                .ifPresentOrElse(
                        user -> listOnlineUsers.getSelectionModel().select(user),
                        () -> listOnlineUsers.getSelectionModel().clearSelection()
                );
    }

    private Optional<UserDTO> findUserByUsername(String username) {
        return listOnlineUsers.getItems()
                .stream()
                .filter(u -> u.getUsername().equals(username))
                .findFirst();
    }

    private boolean containsUsername(List<UserDTO> users, String username) {
        return users.stream().anyMatch(u -> u.getUsername().equals(username));
    }

    private Comparator<UserDTO> byStatusThenName() {
        return Comparator
                .comparing((UserDTO u) -> u.getStatus() != UserStatus.ONLINE)
                .thenComparing(UserDTO::getUsername, String.CASE_INSENSITIVE_ORDER);
    }

    private void clearActiveConversation() {
        chattingWith = null;
        labelChatWith.setText("← Sélectionnez un utilisateur");
        chatBox.getChildren().clear();
    }

    private boolean shouldIgnoreServerError(String message) {
        if (message == null) return false;
        return currentUser.getRole() != Role.ORGANISATEUR
                && !showMembersDialogOnNextResponse
                && message.toLowerCase().contains("organisateur");
    }

    private void configureUsersListCellFactory() {
        listOnlineUsers.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(UserDTO user, boolean empty) {
                super.updateItem(user, empty);
                getStyleClass().removeAll("user-online", "user-offline", "user-self");
                if (empty || user == null) {
                    setText(null);
                    return;
                }

                String suffix = user.getUsername().equals(currentUser.getUsername()) ? " (vous)" : "";
                String status = user.getStatus() == UserStatus.ONLINE ? "" : " (hors ligne)";
                setText(user.getUsername() + suffix + status);

                if (user.getUsername().equals(currentUser.getUsername())) {
                    getStyleClass().add("user-self");
                } else if (user.getStatus() == UserStatus.ONLINE) {
                    getStyleClass().add("user-online");
                } else {
                    getStyleClass().add("user-offline");
                }
            }
        });
    }

    // ── Déconnexion serveur (RG10) ────────────────────────────────────────────

    private void onServerDisconnected() {
        showAlert(Alert.AlertType.ERROR, "Connexion perdue",
                "La connexion au serveur a été interrompue. Vous êtes passé hors ligne.");
        openLoginWindow();
    }

    // ── Navigation ───────────────────────────────────────────────────────────

    private void openLoginWindow() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
            Scene scene = new Scene(loader.load(), 460, 620);
            scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());

            Stage stage = (Stage) btnLogout.getScene().getWindow();
            stage.setScene(scene);
            stage.setMinWidth(420);
            stage.setMinHeight(560);
            stage.setResizable(true);
            stage.setTitle("Messagerie G2 — Association");
        } catch (IOException e) {
            log.error("Impossible d'ouvrir la fenêtre de login : {}", e.getMessage());
        }
    }

    // ── Utilitaires ──────────────────────────────────────────────────────────

    private void showAlert(Alert.AlertType type, String title, String msg) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }
}
