# 💬 Messagerie G2 — Associations & Événements

Application de messagerie instantanée client-serveur développée en Java 17 avec JavaFX, Hibernate/JPA et MySQL.

---

## 🗂️ Structure du projet

```
messaging-app/
├── shared/        # Modèles et protocole partagés (DTOs, Packet, enums)
├── server/        # Serveur Socket + Hibernate + logique métier
├── client/        # Interface JavaFX
└── database/      # Schéma SQL de référence
```

---

## ⚙️ Technologies

| Technologie | Rôle |
|---|---|
| Java 17 | Langage principal |
| JavaFX 21 | Interface graphique client |
| Sockets Java | Communication temps réel |
| Hibernate 6 / JPA | Persistance ORM |
| PostgreSQL 15+ | Base de données |
| BCrypt | Hachage des mots de passe |
| Logback / SLF4J | Journalisation |
| Maven | Build multi-modules |

---

## 🚀 Installation et lancement

### Prérequis

- Java 17+
- Maven 3.8+
- MySQL 8+

### 1. Base de données

```sql
-- En tant que superuser PostgreSQL :
CREATE DATABASE messaging_db ENCODING 'UTF8';
```

Hibernate crée les tables automatiquement au premier lancement (`hbm2ddl.auto=update`).

### 2. Configuration

Éditez `server/src/main/resources/hibernate.cfg.xml` :

```xml
<property name="hibernate.connection.url">
    jdbc:postgresql://localhost:5432/messaging_db
</property>
<property name="hibernate.connection.username">postgres</property>
<property name="hibernate.connection.password">VOTRE_MOT_DE_PASSE</property>
```

### 3. Build

```bash
mvn clean package -DskipTests
```

### 4. Lancer le serveur

```bash
java -jar server/target/messaging-server-jar-with-dependencies.jar
```

### 5. Lancer le client

```bash
mvn -pl client javafx:run
```

---

## 📐 Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                          CLIENT                              │
│  LoginController ──→ MainController                         │
│  ServerConnection (socket + listener thread)                │
└──────────────────────────┬──────────────────────────────────┘
                           │  Sockets (ObjectStream)
                           │  Protocole : Packet (Serializable)
┌──────────────────────────▼──────────────────────────────────┐
│                          SERVEUR                             │
│  MessagingServer                                             │
│    └─ ClientHandler (1 thread / client — RG11)              │
│         ├─ AuthService    (inscription, login — RG1,RG9)    │
│         ├─ MessageService (envoi, historique — RG5,RG6,RG7) │
│         ├─ UserRepository  (Hibernate)                      │
│         └─ MessageRepository (Hibernate)                    │
└──────────────────────────┬──────────────────────────────────┘
                           │  JDBC
┌──────────────────────────▼──────────────────────────────────┐
│                      MySQL                                   │
│   users ──────────────────── messages                       │
└─────────────────────────────────────────────────────────────┘
```

---

## 📋 Règles de gestion implémentées

| Règle | Description | Où |
|---|---|---|
| RG1 | Username unique | `UserRepository.existsByUsername()` |
| RG2 | Auth requise | `ClientHandler.isAuthenticated()` |
| RG3 | Connexion unique | `connectedClients.containsKey()` |
| RG4 | Statut ONLINE/OFFLINE | `UserRepository.updateStatus()` |
| RG5 | Expéditeur connecté + destinataire existant | `MessageService.sendMessage()` |
| RG6 | Messages hors-ligne conservés | `MessageRepository.findPendingMessages()` |
| RG7 | Contenu non vide, max 1000 chars | `MessageService.sendMessage()` |
| RG8 | Historique chronologique | `ORDER BY dateEnvoi ASC` |
| RG9 | Mot de passe haché BCrypt | `AuthService.register()` |
| RG10 | Erreur perte de connexion | `ServerConnection.setDisconnectCallback()` |
| RG11 | Thread dédié par client | `ExecutorService.submit(handler)` |
| RG12 | Journalisation | Logback — `ClientHandler` |
| RG13 | Liste membres (ORGANISATEUR) | `ClientHandler.handleGetMembers()` |

---

## 📡 Protocole réseau

Les échanges utilisent des objets `Packet` sérialisés via `ObjectOutputStream`.

```
CLIENT                        SERVEUR
  │──── REGISTER ────────────────→│
  │←─── SUCCESS / ERROR ──────────│
  │                               │
  │──── LOGIN ────────────────────→│
  │←─── SUCCESS (UserDTO) ────────│
  │←─── RECEIVE_MESSAGE (msgs att)│  (RG6 : messages hors-ligne)
  │←─── ONLINE_USERS (broadcast)──│
  │                               │
  │──── SEND_MESSAGE ─────────────→│
  │←─── SUCCESS ──────────────────│
  │           (destinataire)←─────│  RECEIVE_MESSAGE
  │                               │
  │──── GET_HISTORY ──────────────→│
  │←─── HISTORY_RESPONSE ─────────│
  │                               │
  │──── GET_MEMBERS (ORGA) ───────→│
  │←─── MEMBERS_RESPONSE ─────────│
  │                               │
  │──── LOGOUT ───────────────────→│
```

---

## 👥 Répartition suggérée binôme

**Membre 1 — Back-end / Serveur**
- `MessagingServer`, `ClientHandler`
- `AuthService`, `MessageService`
- `UserRepository`, `MessageRepository`
- Configuration Hibernate + MySQL

**Membre 2 — Front-end / Client**
- `LoginController`, `MainController`
- `ServerConnection`
- FXML + CSS
- Tests d'intégration

---

## 📁 Ressources

- `database/schema.sql` — Schéma SQL de référence
- `server/src/main/resources/hibernate.cfg.xml` — Config Hibernate
- `server/src/main/resources/logback.xml` — Config logs
