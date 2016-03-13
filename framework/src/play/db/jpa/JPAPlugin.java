package play.db.jpa;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceException;
import javax.persistence.Query;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Level;
import org.hibernate.AnnotationException;
import org.hibernate.ejb.Ejb3Configuration;

import play.Logger;
import play.Play;
import play.PlayPlugin;
import play.classloading.ApplicationClasses.ApplicationClass;
import play.data.binding.Binder;
import play.data.binding.ParamNode;
import play.data.binding.RootParamNode;
import play.db.DB;
import play.db.DBConfig;
import play.db.Model;
import play.exceptions.JPAException;
import play.exceptions.UnexpectedException;
import play.utils.Utils;

/**
 * JPA Plugin
 */
public class JPAPlugin extends PlayPlugin {

    /**
	 * 
	 */
	private static final String DIALECT = "hibernate.dialect";
	/**
	 * 
	 */
	private static final String HBM2DDL = "hibernate.hbm2ddl.auto";
	public static boolean autoTxs = true;
	private boolean enabled = true;
	private String _entityConfigs = "";
	String _entitiesChecksum = "";
	private String _mappingFile = "";
	int _startCount = 0;

    @Override
    public Object bind(RootParamNode rootParamNode, String name, @SuppressWarnings("rawtypes") Class clazz, java.lang.reflect.Type type, Annotation[] annotations) {
        // TODO need to be more generic in order to work with JPASupport
        if (JPABase.class.isAssignableFrom(clazz)) {

            ParamNode paramNode = rootParamNode.getChild(name, true);

            String[] keyNames = new JPAModelLoader(clazz).keyNames();
            ParamNode[] ids = new ParamNode[keyNames.length];
            // Collect the matching ids
            int i = 0;
            for (String keyName : keyNames) {
                ids[i++] = paramNode.getChild(keyName, true);
            }
            if (ids != null && ids.length > 0) {
                try {
                    EntityManager em = JPABase.getJPAConfig(clazz).getJPAContext().em();
                    StringBuilder q = new StringBuilder().append("from ").append(clazz.getName()).append(" o where");
                    int keyIdx = 1;
                    for (String keyName : keyNames) {
                            q.append(" o.").append(keyName).append(" = ?").append(keyIdx++).append(" and ");
                    }
                    if (q.length() > 4) {
                        q = q.delete(q.length() - 4, q.length());
                    }
                    Query query = em.createQuery(q.toString());
                    // The primary key can be a composite.
                    Class[] pk = new JPAModelLoader(clazz).keyTypes();
                    int j = 0;
                    for (ParamNode id : ids) {
                        if (id.getValues() == null || id.getValues().length == 0 || id.getFirstValue(null)== null || id.getFirstValue(null).trim().length() <= 0 ) {
                             // We have no ids, it is a new entity
                            return GenericModel.create(rootParamNode, name, clazz, annotations);
                        }
                        query.setParameter(j + 1, Binder.directBind(id.getOriginalKey(), annotations, id.getValues()[0], pk[j++], null));

                    }
                    JPABase o = (JPABase) query.getSingleResult();
                    return GenericModel.edit(rootParamNode, name, o, annotations);
                } catch (NoResultException e) {
                    // ok
                } catch (Exception e) {
                    throw new UnexpectedException(e);
                }
            }
            return GenericModel.create(rootParamNode, name, clazz, annotations);
        }
        return null;
    }

    @Override
    public Object bindBean(RootParamNode rootParamNode, String name, Object bean) {
        if (bean instanceof JPABase) {
            return GenericModel.edit(rootParamNode, name, (JPABase) bean, null);
        }
        return null;
    }

    @Override
    public void enhance(ApplicationClass applicationClass) throws Exception {
        new JPAEnhancer().enhanceThisClass(applicationClass);
    }


    /**
     * returns empty string if default config.
     * returns descriptive string about config name if not default config
     */
    protected String getConfigInfoString(String configName) {
        if (DBConfig.defaultDbConfigName.equals(configName)) {
            return "";
        } else {
            return " (jpa config name: " + configName + ")";
        }
    }


    @SuppressWarnings("deprecation")
	@Override
    public void onApplicationStart() {
    	_startCount++;
    	
    	// bran: added check for db=null 
    	String dbprop = Play.configuration.getProperty("db");
    	if ("null".equals(dbprop)) {
    		this.enabled  = false;
    		System.out.println("null db configured. JPAPlugin disabled.");
    		return;
    	}
        // must check and configure JPA for each DBConfig
        for (DBConfig dbConfig : DB.getDBConfigs()) {
            // check and enable JPA on this config

            // is JPA already configured?
            String configName = dbConfig.getDBConfigName();

            if (JPA.getJPAConfig(configName, true) == null) {
                //must configure it

                // resolve prefix for hibernate config..
                // should be nothing for default, and db_<name> for others
                String propPrefix = "";
                if (!DBConfig.defaultDbConfigName.equalsIgnoreCase(configName)) {
                    propPrefix = "db_" + configName + ".";
                }
       
                // we're ready to configure this instance of JPA

                Ejb3Configuration cfg = newConfig(dbConfig, propPrefix);
                
                Map<Boolean, List<ApplicationClass>> entityGroups = this.groupEntities(configName);
                List<ApplicationClass> changedEntities = entityGroups.get(CHANGED);
                List<ApplicationClass> unchangedEntities = entityGroups.get(UNCHANGED);
                
                Logger.info("changed entities/unchanged: " + changedEntities.size() + "/" + unchangedEntities.size());
                
				boolean entitiesChanged = changedEntities.size() > 0;

				// find out all dependencies
				Set<ApplicationClass> resolved = new HashSet<>(changedEntities);
				Set<ApplicationClass> unresolved = new HashSet<>(changedEntities);
				
				resolveAllDeps(resolved, unresolved);
				
				
				// now all the dependencies are in the resolved
				// move over from unchanged
				changedEntities = new  ArrayList<>(resolved);
				unchangedEntities.removeAll(resolved);
				
				// put in the changed for the first run
				changedEntities.forEach(ac -> {
					cfg.addAnnotatedClass(ac.javaClass); 
					if (Logger.isDebugEnabled()) Logger.debug("JPA Model to be updated : %s", ac.name);
				});
				
                
                String entityConfigs = Play.configuration.getProperty(propPrefix + "jpa.entities", "");
                if (!_entityConfigs.equals(entityConfigs)) {
                	entitiesChanged = true;
                	_entityConfigs = entityConfigs;
                }
                
				String[] moreEntities = entityConfigs.split(", ");
                
				Stream.of(moreEntities)
					.filter(StringUtils::isNotBlank)
					.forEach(entity -> {
						try {
							cfg.addAnnotatedClass(Play.classloader.loadClass(entity.trim()));
						} catch (Exception e) {
							Logger.warn("JPA -> Entity not found: %s", entity);
						}
					});

				// bran: not sure how to detect package changes.. corner case not dealt with for now
				Play.classes.all().forEach( c -> {
					if (!c.isClass() && c.javaPackage != null ) {
						String pkgName = c.javaPackage.getName();
						cfg.addPackage(pkgName);
						Logger.info("JPA -> Adding package: %s", pkgName);
					}
				});
				
                String mappingFile = Play.configuration.getProperty(propPrefix + "jpa.mapping-file", "");
                if (!_mappingFile.equals(mappingFile)) {
                	entitiesChanged = true;
                	_mappingFile = mappingFile;
                }
                
                if (StringUtils.isNotBlank(mappingFile)) {
                    cfg.addResource(mappingFile);
                }
                
                Logger.info("Initializing JPA" + getConfigInfoString(configName) + "...");

                if (entitiesChanged) {
                	addConfig(configName, cfg);
                }
                else {
                	Logger.info("JPAPlugin: entity models not changed. No DDL update.");
                }
                
                if (unchangedEntities.size() > 0) {
	               	cfg.setProperty(HBM2DDL, "none");
	            	// let's add the unchanged entities
					unchangedEntities.forEach(ac -> {
						cfg.addAnnotatedClass(ac.javaClass); 
	//					if (Logger.isDebugEnabled()) Logger.debug("JPA Model DDL not to be updated : %s", ac.name);
					});

					addConfig(configName, cfg);
				}
            }

        }

        // must look for Entity-objects referring to none-existing JPAConfig
        List<Class> allEntityClasses = Play.classloader.getAnnotatedClasses(Entity.class);
        for (Class clazz : allEntityClasses) {
            String configName = getConfig(clazz);
            if (JPA.getJPAConfig(configName, true) == null) {
                throw new JPAException("Found Entity-class (" + clazz.getName() + ") referring to none-existing JPAConfig" + getConfigInfoString(configName) + ". " +
                        "Is JPA properly configured?");
            }
        }
    }

	/**
	 * @author Bing Ran (bing.ran@gmail.com)
	 * @param depends
	 */
	private void resolveAllDeps(Set<ApplicationClass> resolved, Set<ApplicationClass> unresolved) {
		if (unresolved.size() ==0)
			return;
		
		Set<ApplicationClass> newUnresolved =
				unresolved.stream()
					.map(ApplicationClass::getImmediateDependencies)
					.flatMap( Set<String>::stream)
					.filter(s -> s.startsWith("models."))
					.map(s -> Play.classes.getApplicationClass(s))
					.filter(ac1 -> !resolved.contains(ac1))
					.collect(Collectors.toSet());
			
		resolved.addAll(unresolved);
		unresolved.clear();
		
		if (newUnresolved.size() ==0)
			return;
		else
			resolveAllDeps(resolved, newUnresolved);
	}


	@SuppressWarnings("deprecation")
	private void addConfig(String configName, Ejb3Configuration cfg) {
		try {
			JPA.addConfiguration(configName, cfg);
		} catch (AnnotationException e) {
			dealAnnoEx(configName, cfg, e);
		} catch (PersistenceException e) {
			Throwable cause = e.getCause();
			if (cause instanceof AnnotationException) {
				dealAnnoEx(configName, cfg, (AnnotationException) cause);
			}
			else
				throw new JPAException(e.getMessage() + getConfigInfoString(configName), cause != null ? cause : e);
		}
	}

	private void dealAnnoEx(String configName, Ejb3Configuration cfg, AnnotationException e) {
		String msg = e.getMessage();
		if (msg.contains("unknown entity") || msg.contains("unmapped class")) {
			String unknown = msg.substring(msg.lastIndexOf("models.")).trim();
			if (unknown.endsWith("]"))
					unknown =  unknown.substring(0, unknown.length() - 1);
			Optional<Class> cls = getEntityClass(configName, unknown);
			if (cls.isPresent()) {
				cfg.addAnnotatedClass(cls.get());
				Logger.info("added entity class: %s. trying again.", unknown);
				addConfig(configName, cfg); // recursive
			}
			else {
				throw e;
			}
		}
	}

    /**
	 * @author Bing Ran (bing.ran@gmail.com)
	 * @param configName
	 * @return
	 */
	private Optional<Class> getEntityClass(String configName, String cname) {
		Optional<Class> f = Play.classloader.getAnnotatedClasses(Entity.class).stream()
			.filter(clazz -> configName.equals(getConfig(clazz)))
			.filter(c -> c.getName().equals(cname))
			.findFirst();
		return f;
	}

	/**
	 * @author Bing Ran (bing.ran@gmail.com)
	 * @return
	 */
	@SuppressWarnings("deprecation")
	private Ejb3Configuration newConfig(DBConfig dbConfig, String propPrefix) {
		Ejb3Configuration cfg = new Ejb3Configuration();

        if (dbConfig.getDatasource() != null) {
            cfg.setDataSource(dbConfig.getDatasource());
        }

        if (!Play.configuration.getProperty(propPrefix + "jpa.ddl", Play.mode.isDev() ? "update" : "none").equals("none")) {
            cfg.setProperty(HBM2DDL, Play.configuration.getProperty(propPrefix + "jpa.ddl", "update"));
        }

        String driver = null;
        if (StringUtils.isEmpty(propPrefix)) {
            driver = Play.configuration.getProperty("db.driver");
        } else {
            driver = Play.configuration.getProperty(propPrefix + "driver");
        }
        cfg.setProperty(DIALECT, getDefaultDialect(propPrefix, driver));
        cfg.setProperty("javax.persistence.transaction", "RESOURCE_LOCAL");


        cfg.setInterceptor(new HibernateInterceptor());

        // This setting is global for all JPAs - only configure if configuring default JPA
        if (StringUtils.isEmpty(propPrefix)) {
            if (Play.configuration.getProperty(propPrefix + "jpa.debugSQL", "false").equals("true")) {
                org.apache.log4j.Logger.getLogger("org.hibernate.SQL").setLevel(Level.ALL);
            } else {
                org.apache.log4j.Logger.getLogger("org.hibernate.SQL").setLevel(Level.OFF);
            }
        }
        // inject additional  hibernate.* settings declared in Play! configuration
        Properties additionalProperties = (Properties) Utils.Maps.filterMap(Play.configuration, "^" + propPrefix + "hibernate\\..*");
        // We must remove prefix from names
        Properties transformedAdditionalProperties = new Properties();
        for (Map.Entry<Object, Object> entry : additionalProperties.entrySet()) {
            Object key = entry.getKey();
            if (!StringUtils.isEmpty(propPrefix)) {
                key = ((String) key).substring(propPrefix.length()); // chop off the prefix
            }
            transformedAdditionalProperties.put(key, entry.getValue());
        }
        cfg.addProperties(transformedAdditionalProperties);


        try {
            // nice hacking :) I like it..
            Field field = cfg.getClass().getDeclaredField("overridenClassLoader");
            field.setAccessible(true);
            field.set(cfg, Play.classloader);
        } catch (Exception e) {
            Logger.error(e, "Error trying to override the hibernate classLoader (new hibernate version ???)");
        }

        return cfg;
	}

	private List<Class> findEntityClassesForThisConfig(String configName) {
        //look and see if we have any Entity-objects for this db config
        // filter list on Entities meant for us..
        return Play.classloader.getAnnotatedClasses(Entity.class).stream().filter(clazz -> configName.equals(getConfig(clazz))).collect(Collectors.toList());

// bran: no idea why the condition. no diff at all 
//        if (!Play.configuration.getProperty(propPrefix + "jpa.entities", "").equals("")) {
//            return filteredClasses;
//        }

//         return filteredClasses;
    }
	
	boolean anyEntitiesChanged() {
		List<ApplicationClass> cs = Play.classes.getAnnotatedClasses(Entity.class);
		String newSum = cs.stream().map(ApplicationClass::getSigChecksumString).reduce("", (a, b) -> a + b);
		if (!newSum.equals(_entitiesChecksum)) {
			_entitiesChecksum = newSum;
			return true;
		}
		return false;
	}

	private Map<Boolean, List<ApplicationClass>> groupEntities(String configName) {
		List<ApplicationClass> allEnt = Play.classes.getAnnotatedClasses(Entity.class);
	
		Map<Boolean, List<ApplicationClass>> groups = 
			allEnt
				.stream()
				.filter(ac -> configName.equals(getConfig(ac.javaClass)))
				.collect(Collectors.groupingBy(ac -> {
						if (_startCount <= 1 /*|| true*/) {
							// first time, treat all as changed
							return CHANGED;
						}
						else
							return ac.getSigChecksumString() == null? 
									UNCHANGED : 
										ac.getSigChecksumString().equals(entityChecksums.get(ac.name));
					})
				);

		if (!groups.containsKey(CHANGED))
			groups.put(CHANGED, Collections.<ApplicationClass> emptyList());
		if (!groups.containsKey(UNCHANGED))
			groups.put(UNCHANGED, Collections.<ApplicationClass> emptyList());

		// update the checksums
		groups.get(CHANGED).forEach(ac -> {entityChecksums.put(ac.name, ac.getSigChecksumString());});

		return groups;
	}

	
	Map<String, String> entityChecksums = new HashMap<>();
	private static final Boolean CHANGED = false;
	private static final Boolean UNCHANGED = true;

	
	private String getConfig(Class clazz) {
		return Entity2JPAConfigResolver.getJPAConfigNameForEntityClass(clazz);
	}


    public static String getDefaultDialect(String propPrefix, String driver) {
        String dialect = Play.configuration.getProperty(propPrefix + "jpa.dialect");
        if (dialect != null) {
            return dialect;
        } else if ("org.h2.Driver".equals(driver)) {
            return "org.hibernate.dialect.H2Dialect";
        } else if ("org.hsqldb.jdbcDriver".equals(driver)) {
            return "org.hibernate.dialect.HSQLDialect";
        } else if ("com.mysql.jdbc.Driver".equals(driver)) {
            return "play.db.jpa.MySQLDialect";
        } else if ("org.postgresql.Driver".equals(driver)) {
            return "org.hibernate.dialect.PostgreSQLDialect";
        } else if ("com.ibm.db2.jdbc.app.DB2Driver".equals(driver)) {
            return "org.hibernate.dialect.DB2Dialect";
        } else if ("com.ibm.as400.access.AS400JDBCDriver".equals(driver)) {
            return "org.hibernate.dialect.DB2400Dialect";
        } else if ("com.ibm.as400.access.AS390JDBCDriver".equals(driver)) {
            return "org.hibernate.dialect.DB2390Dialect";
        } else if ("oracle.jdbc.OracleDriver".equals(driver)) {
            return "org.hibernate.dialect.Oracle10gDialect";
        } else if ("com.sybase.jdbc2.jdbc.SybDriver".equals(driver)) {
            return "org.hibernate.dialect.SybaseAnywhereDialect";
        } else if ("com.microsoft.jdbc.sqlserver.SQLServerDriver".equals(driver)) {
            return "org.hibernate.dialect.SQLServerDialect";
        } else if ("com.sap.dbtech.jdbc.DriverSapDB".equals(driver)) {
            return "org.hibernate.dialect.SAPDBDialect";
        } else if ("com.informix.jdbc.IfxDriver".equals(driver)) {
            return "org.hibernate.dialect.InformixDialect";
        } else if ("com.ingres.jdbc.IngresDriver".equals(driver)) {
            return "org.hibernate.dialect.IngresDialect";
        } else if ("progress.sql.jdbc.JdbcProgressDriver".equals(driver)) {
            return "org.hibernate.dialect.ProgressDialect";
        } else if ("com.mckoi.JDBCDriver".equals(driver)) {
            return "org.hibernate.dialect.MckoiDialect";
        } else if ("InterBase.interclient.Driver".equals(driver)) {
            return "org.hibernate.dialect.InterbaseDialect";
        } else if ("com.pointbase.jdbc.jdbcUniversalDriver".equals(driver)) {
            return "org.hibernate.dialect.PointbaseDialect";
        } else if ("com.frontbase.jdbc.FBJDriver".equals(driver)) {
            return "org.hibernate.dialect.FrontbaseDialect";
        } else if ("org.firebirdsql.jdbc.FBDriver".equals(driver)) {
            return "org.hibernate.dialect.FirebirdDialect";
        } else {
            throw new UnsupportedOperationException("I do not know which hibernate dialect to use with "
                    + driver + " and I cannot guess it, use the property jpa.dialect in config file");
        }
    }

    @Override
    public void onApplicationStop() {
        JPA.close();
    }

    @Override
    public void beforeInvocation() {
    	if (!enabled)
    		return;
        // just to be safe we must clear all possible previous
        // JPAContexts in this thread
        JPA.clearJPAContext();
    }

    @Override
    public void afterInvocation() {
        closeTx(false);
    }

    @Override
    public void onInvocationException(Throwable e) {
        closeTx(true);
    }

    @Override
    public void invocationFinally() {
        closeTx(true);
    }

    /**
     * initialize the JPA context and starts a JPA transaction
     * if not already started.
     * <p/>
     * This method is not needed since transaction is created
     * automatically on first use.
     * <p/>
     * It is better to specify readonly like this: @Transactional(readOnly=true)
     *
     * @param readonly true for a readonly transaction
     * @deprecated use @Transactional with readOnly-property instead
     */
    @Deprecated
    public static void startTx(boolean readonly) {
        // Create new transaction by getting the JPAContext
        JPA.getJPAConfig(DBConfig.defaultDbConfigName).getJPAContext(readonly);
    }

    /**
     * clear current JPA context and transaction if JPAPlugin.autoTxs is true
     * When using multiple databases in the same request this method
     * tries to commit/rollback as many transactions as possible,
     * but there is not guaranteed that all transactions are committed.
     *
     * @param rollback shall current transaction be committed (false) or cancelled (true)
     */
    protected static void closeTx(boolean rollback) {
        if (autoTxs) {
            JPA.closeTx(rollback);
        }
    }

    @Override
    public Model.Factory modelFactory(Class<? extends Model> modelClass) {
        if (modelClass.isAnnotationPresent(Entity.class)) {
            return new JPAModelLoader(modelClass);
        }
        return null;
    }

    @Override
    public void afterFixtureLoad() {
        JPA.clear();
    }
  }
