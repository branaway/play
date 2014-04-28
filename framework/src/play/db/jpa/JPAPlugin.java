package play.db.jpa;

import java.beans.PropertyDescriptor;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.persistence.Embeddable;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.MappedSuperclass;
import javax.persistence.NoResultException;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.PersistenceException;
import javax.persistence.Query;
import javax.persistence.Transient;

import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Level;
import org.hibernate.AnnotationException;
import org.hibernate.CallbackException;
import org.hibernate.EmptyInterceptor;
import org.hibernate.collection.spi.PersistentCollection;
import org.hibernate.ejb.Ejb3Configuration;
import org.hibernate.type.Type;

import play.Logger;
import play.Play;
import play.PlayPlugin;
import play.classloading.ApplicationClasses.ApplicationClass;
import play.data.binding.Binder;
import play.data.binding.NoBinding;
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
                    Object o = query.getSingleResult();
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
            return GenericModel.edit(rootParamNode, name, bean, null);
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
// bran: dead code
//                final String hibernateDataSource = Play.configuration.getProperty(propPrefix + "hibernate.connection.datasource");
//                if (StringUtils.isEmpty(hibernateDataSource) && dbConfig == null) {
//                    throw new JPAException("Cannot start a JPA manager without a properly configured database" + getConfigInfoString(configName),
//                            new NullPointerException("No datasource configured"));
//                }

                Ejb3Configuration cfg = newConfig(dbConfig, propPrefix);
                
                Map<Boolean, List<ApplicationClass>> entityGroups = this.groupEntities(configName);
                List<ApplicationClass> changedEntities = entityGroups.get(CHANGED);
                List<ApplicationClass> unchangedEntities = entityGroups.get(UNCHANGED);
                
                Logger.info("changed entities/unchanged: " + changedEntities.size() + "/" + unchangedEntities.size());
                
				boolean entitiesChanged = changedEntities.size() > 0;

				// bran: XXX I need to find out exactly the related group of entities that should go together. 
				// before I can have optimal algorithm, let's do a full refresh 
//				if (entitiesChanged) {
//					changedEntities.addAll(unchangedEntities);
//					unchangedEntities.clear();
//				}
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
				
//                findEntityClassesForThisConfig(configName).forEach(clazz -> { 
//                });
//                
//                boolean entitiesChanged = _startCount == 1 ? true : anyEntitiesChanged();
                
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
                
               	cfg.setProperty("hibernate.hbm2ddl.auto", "none");
            	// let's add the unchanged entities
				unchangedEntities.forEach(ac -> {
					cfg.addAnnotatedClass(ac.javaClass); 
					if (Logger.isDebugEnabled()) Logger.debug("JPA Model DDL not to be updated : %s", ac.name);
				});

				if (unchangedEntities.size() > 0)
					addConfig(configName, cfg);
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
            cfg.setProperty("hibernate.hbm2ddl.auto", Play.configuration.getProperty(propPrefix + "jpa.ddl", "update"));
        }

        String driver = null;
        if (StringUtils.isEmpty(propPrefix)) {
            driver = Play.configuration.getProperty("db.driver");
        } else {
            driver = Play.configuration.getProperty(propPrefix + "driver");
        }
        cfg.setProperty("hibernate.dialect", getDefaultDialect(propPrefix, driver));
        cfg.setProperty("javax.persistence.transaction", "RESOURCE_LOCAL");


        cfg.setInterceptor(new PlayInterceptor());

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

    public static class JPAModelLoader implements Model.Factory {

        private final Class<? extends Model> clazz;
        private final String jpaConfigName;
        private JPAConfig _jpaConfig;
        private Map<String, Model.Property> properties;


        public JPAModelLoader(Class<? extends Model> clazz) {
            this.clazz = clazz;

            // must detect correct JPAConfig for this model
            this.jpaConfigName = Entity2JPAConfigResolver.getJPAConfigNameForEntityClass(clazz);
        }

        protected JPAContext getJPAContext() {
            if (_jpaConfig == null) {
                _jpaConfig = JPA.getJPAConfig(jpaConfigName);
            }
            return _jpaConfig.getJPAContext();
        }

        public Model findById(Object id) {
        	if (id == null) {
        		return null;
        	}
            try {
                return getJPAContext().em().find(clazz, id);
            } catch (Exception e) {
                // Key is invalid, thus nothing was found
                return null;
            }
        }

        @Override
		@SuppressWarnings("unchecked")
        public List<Model> fetch(int offset, int size, String orderBy, String order, List<String> searchFields, String keywords, String where) {
            String q = "from " + clazz.getName();
            if (keywords != null && !keywords.equals("")) {
                String searchQuery = getSearchQuery(searchFields);
                if (!searchQuery.equals("")) {
                    q += " where (" + searchQuery + ")";
                }
                q += (where != null ? " and " + where : "");
            } else {
                q += (where != null ? " where " + where : "");
            }
            if (orderBy == null && order == null) {
                orderBy = "id";
                order = "ASC";
            }
            if (orderBy == null && order != null) {
                orderBy = "id";
            }
            if (order == null || (!order.equals("ASC") && !order.equals("DESC"))) {
                order = "ASC";
            }
            q += " order by " + orderBy + " " + order;
            Query query = getJPAContext().em().createQuery(q);
            if (keywords != null && !keywords.equals("") && q.indexOf("?1") != -1) {
                query.setParameter(1, "%" + keywords.toLowerCase() + "%");
            }
            query.setFirstResult(offset);
            query.setMaxResults(size);
            return query.getResultList();
        }

        public Long count(List<String> searchFields, String keywords, String where) {
            String q = "select count(*) from " + clazz.getName() + " e";
            if (keywords != null && !keywords.equals("")) {
                String searchQuery = getSearchQuery(searchFields);
                if (!searchQuery.equals("")) {
                    q += " where (" + searchQuery + ")";
                }
                q += (where != null ? " and " + where : "");
            } else {
                q += (where != null ? " where " + where : "");
            }
            Query query = getJPAContext().em().createQuery(q);
            if (keywords != null && !keywords.equals("") && q.indexOf("?1") != -1) {
                query.setParameter(1, "%" + keywords.toLowerCase() + "%");
            }
            return Long.decode(query.getSingleResult().toString());
        }

        @Override
		public void deleteAll() {
            getJPAContext().em().createQuery("delete from " + clazz.getName()).executeUpdate();
        }

        @Override
		public List<Model.Property> listProperties() {
            List<Model.Property> properties = new ArrayList<Model.Property>();
            Set<Field> fields = new LinkedHashSet<Field>();
            Class<?> tclazz = clazz;
            while (!tclazz.equals(Object.class)) {
                Collections.addAll(fields, tclazz.getDeclaredFields());
                tclazz = tclazz.getSuperclass();
            }
            for (Field f : fields) {
                if (Modifier.isTransient(f.getModifiers())) {
                    continue;
                }
                if (f.isAnnotationPresent(Transient.class)) {
                    continue;
                }
                if (f.isAnnotationPresent(NoBinding.class)) {
                    NoBinding a = f.getAnnotation(NoBinding.class);
                    List<String> values = Arrays.asList(a.value());
                    if (values.contains("*")) {
                        continue;
                    }
                }
                Model.Property mp = buildProperty(f);
                if (mp != null) {
                    properties.add(mp);
                }
            }
            return properties;
        }

        @Override
		public String keyName() {
            return keyField().getName();
        }

        @Override
		public Class<?> keyType() {
            return keyField().getType();
        }

        public Class<?>[] keyTypes() {
            Field[] fields = keyFields();
            Class<?>[] types = new Class<?>[fields.length];
            int i = 0;
            for (Field field : fields) {
                types[i++] = field.getType();
            }
            return types;
        }

        public String[] keyNames() {
            Field[] fields = keyFields();
            String[] names = new String[fields.length];
            int i = 0;
            for (Field field : fields) {
                names[i++] = field.getName();
            }
            return names;
        }

        private Class<?> getCompositeKeyClass() {
            Class<?> tclazz = clazz;
            while (!tclazz.equals(Object.class)) {
                // Only consider mapped types
                if (tclazz.isAnnotationPresent(Entity.class)
                        || tclazz.isAnnotationPresent(MappedSuperclass.class)) {
                    IdClass idClass = tclazz.getAnnotation(IdClass.class);
                    if (idClass != null)
                        return idClass.value();
                }
                tclazz = tclazz.getSuperclass();
            }
            throw new UnexpectedException("Invalid mapping for class " + clazz + ": multiple IDs with no @IdClass annotation");
        }


        private void initProperties() {
            synchronized (this) {
                if (properties != null)
                    return;
                properties = new HashMap<String, Model.Property>();
                Set<Field> fields = getModelFields(clazz);
                for (Field f : fields) {
                    if (Modifier.isTransient(f.getModifiers())) {
                        continue;
                    }
                    if (f.isAnnotationPresent(Transient.class)) {
                        continue;
                    }
                    Model.Property mp = buildProperty(f);
                    if (mp != null) {
                        properties.put(mp.name, mp);
                    }
                }
            }
        }

        private Object makeCompositeKey(Model model) throws Exception {
            initProperties();
            Class<?> idClass = getCompositeKeyClass();
            Object id = idClass.newInstance();
            PropertyDescriptor[] idProperties = PropertyUtils.getPropertyDescriptors(idClass);
            if (idProperties == null || idProperties.length == 0)
                throw new UnexpectedException("Composite id has no properties: " + idClass.getName());
            for (PropertyDescriptor idProperty : idProperties) {
                // do we have a field for this?
                String idPropertyName = idProperty.getName();
                // skip the "class" property...
                if (idPropertyName.equals("class"))
                    continue;
                Model.Property modelProperty = this.properties.get(idPropertyName);
                if (modelProperty == null)
                    throw new UnexpectedException("Composite id property missing: " + clazz.getName() + "." + idPropertyName
                            + " (defined in IdClass " + idClass.getName() + ")");
                // sanity check
                Object value = modelProperty.field.get(model);

                if (modelProperty.isMultiple)
                    throw new UnexpectedException("Composite id property cannot be multiple: " + clazz.getName() + "." + idPropertyName);
                // now is this property a relation? if yes then we must use its ID in the key (as per specs)
                if (modelProperty.isRelation) {
                    // get its id
                    if (!Model.class.isAssignableFrom(modelProperty.type))
                        throw new UnexpectedException("Composite id property entity has to be a subclass of Model: "
                                + clazz.getName() + "." + idPropertyName);
                    // we already checked that cast above
                    @SuppressWarnings("unchecked")
                    Model.Factory factory = Model.Manager.factoryFor((Class<? extends Model>) modelProperty.type);
                    if (factory == null)
                        throw new UnexpectedException("Failed to find factory for Composite id property entity: "
                                + clazz.getName() + "." + idPropertyName);
                    // we already checked that cast above
                    if (value != null)
                        value = factory.keyValue((Model) value);
                }
                // now affect the composite id with this id
                PropertyUtils.setSimpleProperty(id, idPropertyName, value);
            }
            return id;
        }


        public Object keyValue(Model m) {
            try {
                if (m == null) {
                    return null;
                }

                // Do we have a @IdClass or @Embeddable?
                if (m.getClass().isAnnotationPresent(IdClass.class)) {
                    return makeCompositeKey(m);
                }

                // Is it a composite key? If yes we need to return the matching PK
                final Field[] fields = keyFields();
                final Object[] values = new Object[fields.length];
                int i = 0;
                for (Field f : fields) {
                    final Object o = f.get(m);
                    if (o != null) {
                        values[i++] = o;
                    }
                }

                // If we have only one id return it
                if (values.length == 1) {
                    return values[0];
                }

                return values;
            } catch (Exception ex) {
                throw new UnexpectedException(ex);
            }
        }

        public static Set<Field> getModelFields(Class<?> clazz) {
            Set<Field> fields = new LinkedHashSet<Field>();
            Class<?> tclazz = clazz;
            while (!tclazz.equals(Object.class)) {
                // Only add fields for mapped types
                if (tclazz.isAnnotationPresent(Entity.class)
                        || tclazz.isAnnotationPresent(MappedSuperclass.class))
                    Collections.addAll(fields, tclazz.getDeclaredFields());
                tclazz = tclazz.getSuperclass();
            }
            return fields;
        }

        //
        Field keyField() {
            Class c = clazz;
            try {
                while (!c.equals(Object.class)) {
                    for (Field field : c.getDeclaredFields()) {
                        if (field.isAnnotationPresent(Id.class) || field.isAnnotationPresent(EmbeddedId.class)) {
                            field.setAccessible(true);
                            return field;
                        }
                    }
                    c = c.getSuperclass();
                }
            } catch (Exception e) {
                throw new UnexpectedException("Error while determining the object @Id for an object of type " + clazz);
            }
            throw new UnexpectedException("Cannot get the object @Id for an object of type " + clazz);
        }

        Field[] keyFields() {
            Class c = clazz;
            try {
                List<Field> fields = new ArrayList<Field>();
                while (!c.equals(Object.class)) {
                    for (Field field : c.getDeclaredFields()) {
                        if (field.isAnnotationPresent(Id.class) || field.isAnnotationPresent(EmbeddedId.class)) {
                            field.setAccessible(true);
                            fields.add(field);
                        }
                    }
                    c = c.getSuperclass();
                }
                final Field[] f = fields.toArray(new Field[fields.size()]);
                if (f.length == 0) {
                    throw new UnexpectedException("Cannot get the object @Id for an object of type " + clazz);
                }
                return f;
            } catch (Exception e) {
                throw new UnexpectedException("Error while determining the object @Id for an object of type " + clazz);
            }
        }

        String getSearchQuery(List<String> searchFields) {
            String q = "";
            for (Model.Property property : listProperties()) {
                if (property.isSearchable && (searchFields == null || searchFields.isEmpty() ? true : searchFields.contains(property.name))) {
                    if (!q.equals("")) {
                        q += " or ";
                    }
                    q += "lower(" + property.name + ") like ?1";
                }
            }
            return q;
        }

        Model.Property buildProperty(final Field field) {
            Model.Property modelProperty = new Model.Property();
            modelProperty.type = field.getType();
            modelProperty.field = field;
            if (Model.class.isAssignableFrom(field.getType())) {
                if (field.isAnnotationPresent(OneToOne.class)) {
                    if (field.getAnnotation(OneToOne.class).mappedBy().equals("")) {
                        modelProperty.isRelation = true;
                        modelProperty.relationType = field.getType();
                        modelProperty.choices = new Model.Choices() {

                            @SuppressWarnings("unchecked")
                            public List<Object> list() {
                                return getJPAContext().em().createQuery("from " + field.getType().getName()).getResultList();
                            }
                        };
                    }
                }
                if (field.isAnnotationPresent(ManyToOne.class)) {
                    modelProperty.isRelation = true;
                    modelProperty.relationType = field.getType();
                    modelProperty.choices = new Model.Choices() {

                        @SuppressWarnings("unchecked")
                        public List<Object> list() {
                            return getJPAContext().em().createQuery("from " + field.getType().getName()).getResultList();
                        }
                    };
                }
            }
            if (Collection.class.isAssignableFrom(field.getType())) {
                final Class<?> fieldType = (Class<?>) ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
                if (field.isAnnotationPresent(OneToMany.class)) {
                    if (field.getAnnotation(OneToMany.class).mappedBy().equals("")) {
                        modelProperty.isRelation = true;
                        modelProperty.isMultiple = true;
                        modelProperty.relationType = fieldType;
                        modelProperty.choices = new Model.Choices() {

                            @SuppressWarnings("unchecked")
                            public List<Object> list() {
                                return getJPAContext().em().createQuery("from " + fieldType.getName()).getResultList();
                            }
                        };
                    }
                }
                if (field.isAnnotationPresent(ManyToMany.class)) {
                    if (field.getAnnotation(ManyToMany.class).mappedBy().equals("")) {
                        modelProperty.isRelation = true;
                        modelProperty.isMultiple = true;
                        modelProperty.relationType = fieldType;
                        modelProperty.choices = new Model.Choices() {

                            @SuppressWarnings("unchecked")
                            public List<Object> list() {
                                return getJPAContext().em().createQuery("from " + fieldType.getName()).getResultList();
                            }
                        };
                    }
                }
            }
            if (field.getType().isEnum()) {
                modelProperty.choices = new Model.Choices() {

                    @SuppressWarnings("unchecked")
                    public List<Object> list() {
                        return (List<Object>) Arrays.asList(field.getType().getEnumConstants());
                    }
                };
            }
            modelProperty.name = field.getName();
            if (field.getType().equals(String.class)) {
                modelProperty.isSearchable = true;
            }
            if (field.isAnnotationPresent(GeneratedValue.class)) {
                modelProperty.isGenerated = true;
            }
            if (field.isAnnotationPresent(Id.class) || field.isAnnotationPresent(EmbeddedId.class)) {
                // Look if the target is an embeddable class
                if (field.getType().isAnnotationPresent(Embeddable.class) || field.getType().isAnnotationPresent(IdClass.class)) {
                    modelProperty.isRelation = true;
                    modelProperty.relationType = field.getType();
                }
            }
            return modelProperty;
        }
    }

    // Explicit SAVE for JPABase is implemented here
    // ~~~~~~
    // We've hacked the org.hibernate.event.def.AbstractFlushingEventListener line 271, to flush collection update,remove,recreation
    // only if the owner will be saved or if the targeted entity will be saved (avoid the org.hibernate.HibernateException: Found two representations of same collection)
    // As is:
    // if (session.getInterceptor().onCollectionUpdate(coll, ce.getLoadedKey())) {
    //      actionQueue.addAction(...);
    // }
    //
    // This is really hacky. We should move to something better than Hibernate like EBEAN
    private static class PlayInterceptor extends EmptyInterceptor {

		   @Override
	       public int[] findDirty(Object o, Serializable id, Object[] arg2, Object[] arg3, String[] arg4, Type[] arg5) {
	       		if (o instanceof JPABase && !((JPABase) o).willBeSaved) {
	                return new int[0];
	            }
	            return null;
	       }

		   @Override
           public boolean onCollectionUpdate(Object collection, Serializable key) throws CallbackException {
                if (collection instanceof PersistentCollection) {
                    Object o = ((PersistentCollection) collection).getOwner();
                   	if (o instanceof JPABase) {
						if (entities.get() != null) {
                           	return ((JPABase) o).willBeSaved || ((JPABase) entities.get()).willBeSaved;
						} else {
							return ((JPABase) o).willBeSaved;
						}
                    }
                } else {
                    System.out.println("HOO: Case not handled !!!");
                }
          		return super.onCollectionUpdate(collection, key);
          }

          @Override
          public boolean onCollectionRecreate(Object collection, Serializable key) throws CallbackException {
              if (collection instanceof PersistentCollection) {
                  Object o = ((PersistentCollection) collection).getOwner();
          	 	if (o instanceof JPABase) {
				if (entities.get() != null) {
                    return ((JPABase) o).willBeSaved || ((JPABase) entities.get()).willBeSaved;
				} else {
					return ((JPABase) o).willBeSaved;
				}
                   } 
			} else {
           		System.out.println("HOO: Case not handled !!!");
        	}
            return super.onCollectionRecreate(collection, key);
        }

		@Override
        public boolean onCollectionRemove(Object collection, Serializable key) throws CallbackException {
		 	if (collection instanceof PersistentCollection) {
                Object o = ((PersistentCollection) collection).getOwner();
	            if (o instanceof JPABase) {
					if (entities.get() != null) {
                   		return ((JPABase) o).willBeSaved || ((JPABase) entities.get()).willBeSaved;
					} else {
						return ((JPABase) o).willBeSaved;
					}
                }
			} else {
	          	System.out.println("HOO: Case not handled !!!");
	        }
	        return super.onCollectionRemove(collection, key);
		}

		protected ThreadLocal<Object> entities = new ThreadLocal<Object>();
		
		@Override
	 	public boolean onSave(Object entity, Serializable id, Object[] state, String[] propertyNames, Type[] types)  {
			entities.set(entity);
			return super.onSave(entity, id, state, propertyNames, types);
		}
				
		@Override
		public void afterTransactionCompletion(org.hibernate.Transaction tx) {
			entities.remove();
		}
    
    }
}
