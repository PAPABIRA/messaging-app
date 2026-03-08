package com.asso.messaging.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Point d'entrée de l'application.
 */
public class MessagingApp extends Application {

    private static final Logger log = LoggerFactory.getLogger(MessagingApp.class);

    // Connexion partagée dans toute l'application
    private static final ServerConnection serverConnection = new ServerConnection();

    @Override
    public void start(Stage primaryStage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
        Scene scene = new Scene(loader.load(), 460, 620);
        scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());

        primaryStage.setTitle("Messagerie G2 — Association");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(420);
        primaryStage.setMinHeight(560);
        primaryStage.setResizable(true);
        primaryStage.show();

        // Déconnexion propre à la fermeture de la fenêtre
        primaryStage.setOnCloseRequest(e -> {
            if (serverConnection.isConnected()) {
                serverConnection.disconnect();
            }
        });

        log.info("Application démarrée.");
    }

    /** Accès global à la connexion serveur. */
    public static ServerConnection getServerConnection() {
        return serverConnection;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
