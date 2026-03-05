package com.asso.messaging.util;

import com.asso.messaging.model.Message;
import com.asso.messaging.model.User;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utilitaire Hibernate — fournit la SessionFactory singleton.
 * Configure Hibernate à partir du fichier hibernate.cfg.xml.
 */
public class HibernateUtil {

    private static final Logger log = LoggerFactory.getLogger(HibernateUtil.class);
    private static SessionFactory sessionFactory;

    private HibernateUtil() {}

    public static synchronized SessionFactory getSessionFactory() {
        if (sessionFactory == null) {
            try {
                sessionFactory = new Configuration()
                        .configure("hibernate.cfg.xml")
                        .addAnnotatedClass(User.class)
                        .addAnnotatedClass(Message.class)
                        .buildSessionFactory();
                log.info("SessionFactory Hibernate initialisée avec succès.");
            } catch (Exception e) {
                log.error("Erreur lors de l'initialisation de Hibernate : {}", e.getMessage(), e);
                throw new ExceptionInInitializerError(e);
            }
        }
        return sessionFactory;
    }

    public static void shutdown() {
        if (sessionFactory != null && !sessionFactory.isClosed()) {
            sessionFactory.close();
            log.info("SessionFactory Hibernate fermée.");
        }
    }
}
