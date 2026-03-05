package com.asso.messaging.protocol;

/**
 * Types de paquets échangés entre client et serveur.
 * Chaque type correspond à une action du protocole applicatif.
 */
public enum PacketType {
    // Authentification
    REGISTER,           // Client → Serveur : inscription
    LOGIN,              // Client → Serveur : connexion
    LOGOUT,             // Client → Serveur : déconnexion

    // Messagerie
    SEND_MESSAGE,       // Client → Serveur : envoi d'un message
    RECEIVE_MESSAGE,    // Serveur → Client : réception d'un message
    GET_HISTORY,        // Client → Serveur : demande d'historique
    HISTORY_RESPONSE,   // Serveur → Client : envoi de l'historique
    MESSAGE_STATUS_UPDATE, // Serveur → Client : mise à jour statut message

    // Membres
    GET_MEMBERS,        // Client → Serveur : liste des membres (ORGANISATEUR)
    MEMBERS_RESPONSE,   // Serveur → Client : réponse liste membres
    ONLINE_USERS,       // Serveur → Client : broadcast des connectés

    // Réponses génériques
    SUCCESS,            // Serveur → Client : succès d'une opération
    ERROR               // Serveur → Client : erreur avec message
}
