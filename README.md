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
