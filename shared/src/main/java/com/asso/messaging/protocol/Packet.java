package com.asso.messaging.protocol;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Paquet de communication entre client et serveur.
 * Tous les échanges réseau passent par cette classe sérialisable.
 *
 * Structure :
 *  - type    : action demandée ou réponse
 *  - payload : map clé/valeur transportant les données métier
 *  - timestamp : horodatage de création du paquet
 */
public class Packet implements Serializable {

    private static final long serialVersionUID = 1L;

    private final PacketType type;
    private final Map<String, Object> payload;
    private final LocalDateTime timestamp;

    public Packet(PacketType type) {
        this.type = type;
        this.payload = new HashMap<>();
        this.timestamp = LocalDateTime.now();
    }

    // ── Accès au payload ────────────────────────────────────────────────────

    public Packet put(String key, Object value) {
        payload.put(key, value);
        return this;
    }

    public Object get(String key) {
        return payload.get(key);
    }

    public String getString(String key) {
        Object v = payload.get(key);
        return v != null ? v.toString() : null;
    }

    // ── Getters ─────────────────────────────────────────────────────────────

    public PacketType getType() {
        return type;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "Packet{type=" + type + ", payload=" + payload + '}';
    }
}
