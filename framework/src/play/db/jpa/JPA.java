package play.db.jpa;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.persistence.EntityManager;

import org.hibernate.ejb.Ejb3Configuration;

import play.Logger;
import play.db.DBConfig;
import play.db.jpa.GenericModel.JPAQuery;
import play.exceptions.JPAException;
import play.mvc.Scope.Params;


/**
 * JPA Support
 *
 * This class holds reference to all JPA configurations.
 * Each configuration has its own instance of JPAConfig.
 *
 * dbConfigName corresponds to properties-names in application.conf.
 *
 * The default DBConfig is the one configured using 'db.' in application.conf
 *
 * dbConfigName = 'other' is configured like this:
 *
 * db_other = mem
 * db_other.user = batman
 *
 * This class also preserves backward compatibility by
 * directing static methods to the default JPAConfig-instance
 *
 * A particular JPAConfig-instance uses the DBConfig with the same configName
 */
public class JPA {

    /**
     * Holds ref to the default jpa config named defaultJPAConfigName.
     * Don't use this property directly in the code.
     * use getDefaultJPAConfig() - which does some checking
     */
    private static JPAConfig _defaultJPAConfig = null;
    private final static Map<String, JPAConfig> jpaConfigs = new HashMap<String, JPAConfig>(1);

    protected static void addConfiguration(String configName, Ejb3Configuration cfg) {
    	JPAConfig put = jpaConfigs.get(configName);
    	if (put != null) {
    		jpaConfigs.remove(configName);
    		put.close();
    	}
    	
        JPAConfig jpaConfig = new JPAConfig(cfg, configName);// bran: this is where the schemas are checked
        jpaConfigs.put(configName, jpaConfig);
        
        if( DBConfig.defaultDbConfigName.equals(configName)) {
            _defaultJPAConfig = jpaConfig;
            JPQL.createSingleton();
        }
    }

    public static JPAConfig getJPAConfig(String jpaConfigName) {
        return getJPAConfig(jpaConfigName, false);
    }

    public static JPAConfig getJPAConfig(String jpaConfigName, boolean ignoreError) {
        JPAConfig jpaConfig = jpaConfigs.get(jpaConfigName);
        if (jpaConfig==null && !ignoreError) {
            if (!isEnabled()) {
                // Show simpler error message if JPA is not enabled
                throw new JPAException("The JPA context is not initialized. JPA Entity Manager automatically start " +
                        "when one or more classes annotated with the @javax.persistence.Entity annotation " +
                        "are found in the application.");
            } else {
                throw new JPAException("No JPAConfig is found with the name " + jpaConfigName);
            }
        }
        return jpaConfig;
    }

    protected static void close() {
        for( JPAConfig jpaConfig : jpaConfigs.values()) {
            // do our best to close the JPA config
            try {
                jpaConfig.close();
            } catch (Exception e) {
                Logger.error(e, "Error closing JPA config "+jpaConfig.getConfigName());
            }
        }
        jpaConfigs.clear();
        _defaultJPAConfig = null;
    }

    /**
     * clear current JPA context and transaction
     * @param rollback shall current transaction be committed (false) or cancelled (true)
     */
    public static void closeTx(boolean rollback) {
        boolean error = false;
        for (JPAConfig jpaConfig : jpaConfigs.values()) {
            if (jpaConfig.threadHasJPAContext()) {
                // do our best to take care of this transaction
                try {
                    jpaConfig.getJPAContext().closeTx(rollback);
                } catch (Exception e) {
                    Logger.error(e, "Error closing transaction "+jpaConfig.getConfigName());
                    error=true;
                }
            }
        }

        if (error) {
            throw new JPAException("Error closing one or more transactions");
        }
    }

    private static JPAConfig getDefaultJPAConfig() {
        if (_defaultJPAConfig==null) {
            throw new JPAException("The JPA context is not initialized. JPA Entity Manager automatically start " +
                    "when one or more classes annotated with the @javax.persistence.Entity annotation " +
                    "are found in the application.");
        }
        return _defaultJPAConfig;
    }


    
    /*
     * Retrieve the current entityManager
     */ 
    public static EntityManager em() {
        return getDefaultJPAConfig().getJPAContext().em();
    }

    /*
    * Tell to JPA do not commit the current transaction
    */
    public static void setRollbackOnly() {
        getDefaultJPAConfig().getJPAContext().em().getTransaction().setRollbackOnly();
    }

    /**
     * @return true if an entityManagerFactory has started
     */
    public static boolean isEnabled() {
        return !jpaConfigs.isEmpty();
    }

    /**
     * Execute a JPQL query
     */
    public static int execute(String query) {
        return getDefaultJPAConfig().getJPAContext().em().createQuery(query).executeUpdate();
    }

    /*
     * Build a new entityManager.
     * (In most case you want to use the local entityManager with em)
     */ 
    public static EntityManager newEntityManager() {
        return getDefaultJPAConfig().newEntityManager();
    }

    /**
     * @return true if current thread is running inside a transaction
     */
    public static boolean isInsideTransaction() {
        return getDefaultJPAConfig().isInsideTransaction();
    }

    protected static void clear() {
        for (JPAConfig jpaConfig : jpaConfigs.values()) {
            if (jpaConfig.threadHasJPAContext()) {
                jpaConfig.getJPAContext().em().clear();
            }
        }
    }

    protected static void clearJPAContext() {
        for (JPAConfig jpaConfig : jpaConfigs.values()) {
            jpaConfig.clearJPAContext();
        }
    }

	public static long count(Class<? extends GenericModel> modelClass, String query, Object... params) {
		EntityWithDB en = EntityWithDB.from(modelClass); 
		return JPA.getJPQL(en).count(en.entity, query, params);
	}

	@SuppressWarnings("unchecked")
	public static <T extends GenericModel> List<T>  findAll(Class<T> modelClass) {
		EntityWithDB en = EntityWithDB.from(modelClass); 
		return (List<T>) JPA.getJPQL(en).findAll(en.entity);
	}

	public static <T extends GenericModel> T findById(Class<T> modelClass, Object id) {
		EntityWithDB en = EntityWithDB.from(modelClass); 
		return (T) JPA.getJPQL(en).findById(modelClass, id);
	}

	@SuppressWarnings("unchecked")
	public static <T extends GenericModel> List <T> findBy(Class<T> modelClass, String query, Object... params) {
		EntityWithDB en = EntityWithDB.from(modelClass); 
		return (List<T>) JPA.getJPQL(en).findBy(en.entity, query, params);
	}

	public static JPAQuery find(Class<? extends GenericModel> modelClass, String query, Object... params) {
		EntityWithDB en = EntityWithDB.from(modelClass); 
		return JPA.getJPQL(en).find(en.entity, query, params);
	}

	public static JPAQuery find(Class<? extends GenericModel> modelClass) {
		EntityWithDB en = EntityWithDB.from(modelClass); 
		return JPA.getJPQL(en).find(en.entity);
	}

	public static JPAQuery all(Class<? extends GenericModel> modelClass) {
		EntityWithDB en = EntityWithDB.from(modelClass); 
		return JPA.getJPQL(en).all(en.entity);
	}

	public static int delete(Class<? extends GenericModel> modelClass, String query, Object... params) {
		EntityWithDB en = EntityWithDB.from(modelClass); 
		return JPA.getJPQL(en).delete(en.entity, query, params);
	}

	public static int deleteAll(Class<? extends GenericModel> modelClass) {
		EntityWithDB en = EntityWithDB.from(modelClass); 
		return JPA.getJPQL(en).deleteAll(en.entity);
	}

	public static <T extends GenericModel> T create(Class<T> modelClass, String name, Params params) throws Exception {
		EntityWithDB en = EntityWithDB.from(modelClass); 
		return JPA.getJPQL(en).create(modelClass, name, params);
	}

	@SuppressWarnings("unchecked")
	public static <T extends GenericModel> T findOneBy(Class<T> modelClass, String query, Object... params) {
		EntityWithDB en = EntityWithDB.from(modelClass); 
		return (T) JPA.getJPQL(en).findOneBy(en.entity, query, params);
	}

	public static long count(Class<? extends GenericModel> modelClass) {
		EntityWithDB en = EntityWithDB.from(modelClass); 
		return JPA.getJPQL(en).count(en.entity);
	}

	static JPQL getJPQL(EntityWithDB en) {
		return getJPAConfig(en.persistenceUnit).jpql;
	}
}
