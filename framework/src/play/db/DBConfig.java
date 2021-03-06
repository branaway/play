package play.db;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.sql.DataSource;
import javax.sql.RowSet;
import javax.sql.rowset.CachedRowSet;

import org.apache.commons.lang.StringUtils;
import org.hibernate.internal.SessionImpl;

import com.mchange.v2.c3p0.ComboPooledDataSource;
import com.mchange.v2.c3p0.ConnectionCustomizer;
import com.sun.rowset.CachedRowSetImpl;

import jregex.Matcher;
import play.Logger;
import play.Play;
import play.db.jpa.JPA;
import play.db.jpa.JPAConfig;
import play.db.jpa.JPAContext;
import play.db.jpa.JPAPlugin;
import play.exceptions.DatabaseException;

public class DBConfig {

    /**
     * This is the name of the default db- and jpa-config name
     */
    public static final String defaultDbConfigName = "play";

    /**
     * Name of the db-config - should match jpaConfig-name if present
     */
    private final String dbConfigName;

    private String url = "";

    /**
     * The loaded datasource.
     */
    private DataSource datasource = null;

    /**
     * The method used to destroy the datasource
     */
    private String destroyMethod = "";

    private ThreadLocal<Connection> localConnection = new ThreadLocal<Connection>();

    protected DBConfig(String dbConfigName) {
        this.dbConfigName = dbConfigName;
    }

    public String getDBConfigName() {
        return dbConfigName;
    }

    /**
     * Close the connection opened for the current thread.
     */
    public void close() {
        if (localConnection.get() != null) {
            try {
                Connection connection = localConnection.get();
                localConnection.set(null);
                connection.close();
            } catch (Exception e) {
                throw new DatabaseException("It's possible that the connection was not properly closed !", e);
            }
        }
    }

    /**
     * Open a connection for the current thread.
     * 
     * @return A valid SQL connection
     */
    public Connection getConnection() {
        try {
            // do we have a present JPAContext for this db-config in current
            // thread?
            JPAConfig jpaConfig = JPA.getJPAConfig(dbConfigName, true);
            if (jpaConfig != null) {
                JPAContext jpaContext = jpaConfig.getJPAContext();
                return ((SessionImpl) ((org.hibernate.ejb.EntityManagerImpl) jpaContext.em()).getSession()).connection();
            }

            // do we have a current raw connection bound to thread?
            if (localConnection.get() != null) {
                return localConnection.get();
            }

            // must create connection
            Connection connection = datasource.getConnection();
            localConnection.set(connection);
            return connection;
        } catch (SQLException ex) {
            throw new DatabaseException("Cannot obtain a new connection (" + ex.getMessage() + ")", ex);
        } catch (NullPointerException e) {
            if (datasource == null) {
                throw new DatabaseException("No database found. Check the configuration of your application.", e);
            }
            throw e;
        }
    }

    /**
     * Execute an SQL update
     * 
     * @param SQL
     * @return false if update failed
     */
    public boolean execute(String SQL) {
        Statement statement = null;
        try {
            statement = getConnection().createStatement();
            if (statement != null) {
                return statement.execute(SQL);
            }
        } catch (SQLException ex) {
            throw new DatabaseException(ex.getMessage(), ex);
        } finally {
            safeCloseStatement(statement);
        }
        return false;
    }

    /**
     * Execute an SQL query
     * 
     * @param SQL
     * @return The rowSet of the query
     */
    public RowSet executeQuery(String SQL) {
        Statement statement = null;
        ResultSet rs = null;
        try {
            statement = getConnection().createStatement();
            if (statement != null) {
                rs = statement.executeQuery(SQL);
            }

            // Need to use a CachedRowSet that caches its rows in memory, which
            // makes it possible to operate without always being connected to
            // its data source
            CachedRowSet rowset = new CachedRowSetImpl();
            rowset.populate(rs);
            return rowset;
        } catch (SQLException ex) {
            throw new DatabaseException(ex.getMessage(), ex);
        } finally {
            safeCloseResultSet(rs);
            safeCloseStatement(statement);
        }
    }

    public static void safeCloseResultSet(ResultSet resultSet) {
        if (resultSet != null) {
            try {
                resultSet.close();
            } catch (SQLException ex) {
                throw new DatabaseException(ex.getMessage(), ex);
            }
        }
    }

    public static void safeCloseStatement(Statement statement) {
        if (statement != null) {
            try {
                statement.close();
            } catch (SQLException ex) {
                throw new DatabaseException(ex.getMessage(), ex);
            }
        }
    }

    /**
     * Tries to destroy the datasource
     */
    public void destroy() {
        try {
            if (datasource != null && destroyMethod != null && !destroyMethod.equals("")) {
                Method close = datasource.getClass().getMethod(destroyMethod, new Class[] {});
                if (close != null) {
                    close.invoke(datasource, new Object[] {});
                    datasource = null;
                    if (Logger.isTraceEnabled()) {
                        Logger.trace("Datasource destroyed for db config " + dbConfigName);
                    }
                }
            }
        } catch (Throwable t) {
            Logger.error("Couldn't destroy the datasource for db config " + dbConfigName, t);
        }
    }

    private void check(Properties p, String mode, String property) {
        if (!StringUtils.isEmpty(p.getProperty(property))) {
            Logger.warn("Ignoring " + property + " because running the in " + mode + " db.");
        }
    }

    /**
     * Detects changes and reconfigures this dbConfig. Returns true if the
     * database was configured (Config info present. An exception is thrown if
     * the config process fails.
     */
    protected boolean configure() {

        // prefix used before all properties when loafing config. default is
        // 'db'
        String propsPrefix;
        if (defaultDbConfigName.equals(dbConfigName)) {
            propsPrefix = "db";
        } else {
            propsPrefix = "db_" + dbConfigName;
        }

        boolean dbConfigured = false;

        if (changed(propsPrefix)) {
            try {

                // We now know that we will either config the db, or fail with
                // exception
                dbConfigured = true;

                Properties p = Play.configuration;

                if (datasource != null) {
                    destroy();
                }

                boolean isJndiDatasource = false;
                String datasourceName = p.getProperty(propsPrefix, "");

                // Identify datasource JNDI lookup name by 'jndi:' or 'java:'
                // prefix
                if (datasourceName.startsWith("jndi:")) {
                    datasourceName = datasourceName.substring("jndi:".length());
                    isJndiDatasource = true;
                }

                if (isJndiDatasource || datasourceName.startsWith("java:")) {

                    Context ctx = new InitialContext();
                    datasource = (DataSource) ctx.lookup(datasourceName);

                } else {

                    // Try the driver
                    String driver = p.getProperty(propsPrefix + ".driver");
                    try {
                        Driver d = (Driver) Class.forName(driver, true, Play.classloader).newInstance();
                        DriverManager.registerDriver(new ProxyDriver(d));
                    } catch (Exception e) {
                        throw new Exception("Driver not found (" + driver + ")");
                    }

                    // Try the connection
                    Connection fake = null;
                    try {
                        if (p.getProperty(propsPrefix + ".user") == null) {
                            fake = DriverManager.getConnection(p.getProperty(propsPrefix + ".url"));
                        } else {
                            fake = DriverManager.getConnection(p.getProperty(propsPrefix + ".url"), p.getProperty(propsPrefix + ".user"),
                                    p.getProperty(propsPrefix + ".pass"));
                        }
                    } finally {
                        if (fake != null) {
                            fake.close();
                        }
                    }

                    ComboPooledDataSource ds = new ComboPooledDataSource();
                    ds.setDriverClass(p.getProperty(propsPrefix + ".driver"));
                    ds.setJdbcUrl(p.getProperty(propsPrefix + ".url"));
                    ds.setUser(p.getProperty(propsPrefix + ".user"));
                    ds.setPassword(p.getProperty(propsPrefix + ".pass"));
                    ds.setAcquireIncrement(Integer.parseInt(p.getProperty(propsPrefix + ".pool.acquireIncrement", "3")));
                    ds.setAcquireRetryAttempts(Integer.parseInt(p.getProperty(propsPrefix + ".pool.acquireRetryAttempts", "10")));
                    ds.setAcquireRetryDelay(Integer.parseInt(p.getProperty(propsPrefix + ".pool.acquireRetryDelay", "1000")));
                    ds.setCheckoutTimeout(Integer.parseInt(p.getProperty(propsPrefix + ".pool.timeout", "5000")));
                    ds.setBreakAfterAcquireFailure(
                            Boolean.parseBoolean(p.getProperty(propsPrefix + ".pool.breakAfterAcquireFailure", "false")));
                    ds.setMaxPoolSize(Integer.parseInt(p.getProperty(propsPrefix + ".pool.maxSize", "30")));
                    ds.setMinPoolSize(Integer.parseInt(p.getProperty(propsPrefix + ".pool.minSize", "1")));
                    ds.setInitialPoolSize(Integer.parseInt(p.getProperty(propsPrefix + ".pool.initialSize", "1")));
                    ds.setMaxIdleTimeExcessConnections(
                            Integer.parseInt(p.getProperty(propsPrefix + ".pool.maxIdleTimeExcessConnections", "0")));
                    ds.setIdleConnectionTestPeriod(
                            Integer.parseInt(p.getProperty(propsPrefix + ".pool.maxIdleConnectionTestPeriod", "10")));
                    ds.setMaxIdleTime(Integer.parseInt(p.getProperty(propsPrefix + ".pool.maxIdleTime", "0")));
                    ds.setTestConnectionOnCheckin(
                            Boolean.parseBoolean(p.getProperty(propsPrefix + ".pool.testConnectionOnCheckin", "true")));
                    ds.setTestConnectionOnCheckout(
                            Boolean.parseBoolean(p.getProperty(propsPrefix + ".pool.testConnectionOnCheckout", "false")));
                    ds.setDebugUnreturnedConnectionStackTraces(
                            Boolean.parseBoolean(p.getProperty(propsPrefix + ".pool.debugUnreturnedConnectionStackTraces", "false")));
                    ds.setLoginTimeout(Integer.parseInt(p.getProperty(propsPrefix + ".pool.loginTimeout", "0")));
                    ds.setMaxAdministrativeTaskTime(Integer.parseInt(p.getProperty(propsPrefix + ".pool.maxAdministrativeTaskTime", "0")));
                    ds.setMaxConnectionAge(Integer.parseInt(p.getProperty(propsPrefix + ".pool.maxConnectionAge", "0")));
                    ds.setMaxStatements(Integer.parseInt(p.getProperty(propsPrefix + ".pool.maxStatements", "0")));
                    ds.setMaxStatementsPerConnection(
                            Integer.parseInt(p.getProperty(propsPrefix + ".pool.maxStatementsPerConnection", "0")));
                    ds.setNumHelperThreads(Integer.parseInt(p.getProperty(propsPrefix + ".pool.numHelperThreads", "3")));
                    ds.setUnreturnedConnectionTimeout(
                            Integer.parseInt(p.getProperty(propsPrefix + ".pool.unreturnedConnectionTimeout", "0")));

                    if (p.getProperty(propsPrefix + ".testquery") != null) {
                        ds.setPreferredTestQuery(p.getProperty(propsPrefix + ".testquery"));
                    } else {
                        String driverClass = JPAPlugin.getDefaultDialect(propsPrefix, ds.getDriverClass());

                        /*
                         * Pulled from
                         * http://dev.mysql.com/doc/refman/5.5/en/connector-j-
                         * usagenotes-j2ee-concepts-connection-pooling.html Yes,
                         * the select 1 also needs to be in there.
                         */
                        if (driverClass.equals("play.db.jpa.MySQLDialect")) {
                            ds.setPreferredTestQuery("/* ping */ SELECT 1");
                        }
                    }

                    // This check is not required, but here to make it clear
                    // that nothing changes for people
                    // that don't set this configuration property. It may be
                    // safely removed.
                    if (p.getProperty("db.isolation") != null) {
                        ds.setConnectionCustomizerClassName(DBConfig.PlayConnectionCustomizer.class.getName());
                    }

                    datasource = ds;
                    url = ds.getJdbcUrl();
                    Connection c = null;
                    try {
                        c = ds.getConnection();
                    } finally {
                        if (c != null) {
                            c.close();
                        }
                    }
                    Logger.info("Connected to %s", ds.getJdbcUrl());

                }

                destroyMethod = p.getProperty(propsPrefix + ".destroyMethod", "");

            } catch (Exception e) {
                datasource = null;
                Logger.error(e, "Cannot connected to the database" + getConfigInfoString() + " : %s", e.getMessage());
                if (e.getCause() instanceof InterruptedException) {
                    throw new DatabaseException("Cannot connected to the database" + getConfigInfoString() + ". Check the configuration.",
                            e);
                }
                throw new DatabaseException("Cannot connected to the database" + getConfigInfoString() + ", " + e.getMessage(), e);
            }
        }

        return dbConfigured;
    }

    /**
     * returns empty string if default config. returns descriptive string about
     * config name if not default config
     */
    protected String getConfigInfoString() {
        if (defaultDbConfigName.equals(dbConfigName)) {
            return "";
        } else {
            return " (db config name: " + dbConfigName + ")";
        }
    }

    protected String getStatus() {
        StringWriter sw = new StringWriter();
        PrintWriter out = new PrintWriter(sw);
        if (datasource == null || !(datasource instanceof ComboPooledDataSource)) {
            out.println("Datasource" + getConfigInfoString() + ":");
            out.println("~~~~~~~~~~~");
            out.println("(not yet connected)");
            return sw.toString();
        }
        ComboPooledDataSource ds = (ComboPooledDataSource) datasource;
        out.println("Datasource" + getConfigInfoString() + ":");
        out.println("~~~~~~~~~~~");
        out.println("Jdbc url: " + ds.getJdbcUrl());
        out.println("Jdbc driver: " + ds.getDriverClass());
        out.println("Jdbc user: " + ds.getUser());
        if (Play.mode.isDev()) {
            out.println("Jdbc password: " + ds.getPassword());
        }
        out.println("Min pool size: " + ds.getMinPoolSize());
        out.println("Max pool size: " + ds.getMaxPoolSize());
        out.println("Initial pool size: " + ds.getInitialPoolSize());
        out.println("Checkout timeout: " + ds.getCheckoutTimeout());
        out.println("Test query : " + ds.getPreferredTestQuery());
        return sw.toString();
    }

    /**
     * Returns true if config has changed. This method does also set additional
     * properties resolved from other settings.
     */
    private boolean changed(String propsPrefix) {
        Properties p = Play.configuration;

        if ("mem".equals(p.getProperty(propsPrefix)) && p.getProperty(propsPrefix + ".url") == null) {
            p.put(propsPrefix + ".driver", "org.h2.Driver");
            p.put(propsPrefix + ".url", "jdbc:h2:mem:" + dbConfigName + ";MODE=MYSQL;DB_CLOSE_ON_EXIT=FALSE");
            p.put(propsPrefix + ".user", "sa");
            p.put(propsPrefix + ".pass", "");
        }

        if ("fs".equals(p.getProperty(propsPrefix)) && p.getProperty(propsPrefix + ".url") == null) {
            p.put(propsPrefix + ".driver", "org.h2.Driver");
            p.put(propsPrefix + ".url", "jdbc:h2:" + (new File(Play.applicationPath, "db/h2/" + dbConfigName).getAbsolutePath())
                    + ";MODE=MYSQL;DB_CLOSE_ON_EXIT=FALSE");
            p.put(propsPrefix + ".user", "sa");
            p.put(propsPrefix + ".pass", "");
        }

        if (p.getProperty(propsPrefix, "").startsWith("java:") && p.getProperty(propsPrefix + ".url") == null) {
            if (datasource == null) {
                return true;
            }
        } else {
            // Internal pool is c3p0, we should call the close() method to
            // destroy it.
            check(p, "internal pool", propsPrefix + ".destroyMethod");

            p.put(propsPrefix + ".destroyMethod", "close");
        }

        Matcher m = new jregex.Pattern("^mysql:(({user}[\\w]+)(:({pwd}[^@]+))?@)?({name}[a-zA-Z0-9_]+)(\\?)?({parameters}[^\\s]+)?$")
                .matcher(p.getProperty(propsPrefix, ""));
        if (m.matches()) {
            String user = m.group("user");
            String password = m.group("pwd");
            String name = m.group("name");
            String parameters = m.group("parameters");

            Map<String, String> paramMap = new HashMap<String, String>();
            paramMap.put("useUnicode", "yes");
            paramMap.put("characterEncoding", "UTF-8");
            paramMap.put("connectionCollation", "utf8_general_ci");
            addParameters(paramMap, parameters);

            p.put(propsPrefix + ".driver", "com.mysql.jdbc.Driver");
            p.put(propsPrefix + ".url", "jdbc:mysql://localhost/" + name + "?" + toQueryString(paramMap));
            if (user != null) {
                p.put(propsPrefix + ".user", user);
            }
            if (password != null) {
                p.put(propsPrefix + ".pass", password);
            }
        }

        if (p.getProperty(propsPrefix + ".url") != null && p.getProperty(propsPrefix + ".url").startsWith("jdbc:h2:mem:")) {
            p.put(propsPrefix + ".driver", "org.h2.Driver");
            p.put(propsPrefix + ".user", "sa");
            p.put(propsPrefix + ".pass", "");
        }

        if ((p.getProperty(propsPrefix + ".driver") == null) || (p.getProperty(propsPrefix + ".url") == null)) {
            return false;
        }
        if (datasource == null) {
            return true;
        } else {
            ComboPooledDataSource ds = (ComboPooledDataSource) datasource;
            if (!p.getProperty(propsPrefix + ".driver").equals(ds.getDriverClass())) {
                return true;
            }
            if (!p.getProperty(propsPrefix + ".url").equals(ds.getJdbcUrl())) {
                return true;
            }
            if (!p.getProperty(propsPrefix + ".user", "").equals(ds.getUser())) {
                return true;
            }
            if (!p.getProperty(propsPrefix + ".pass", "").equals(ds.getPassword())) {
                return true;
            }
        }

        if (!p.getProperty(propsPrefix + ".destroyMethod", "").equals(destroyMethod)) {
            return true;
        }

        return false;
    }

    private static void addParameters(Map<String, String> paramsMap, String urlQuery) {
        if (!StringUtils.isBlank(urlQuery)) {
            String[] params = urlQuery.split("[\\&]");
            for (String param : params) {
                String[] parts = param.split("[=]");
                if (parts.length > 0 && !StringUtils.isBlank(parts[0])) {
                    paramsMap.put(parts[0], parts.length > 1 ? StringUtils.stripToNull(parts[1]) : null);
                }
            }
        }
    }

    private static String toQueryString(Map<String, String> paramMap) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : paramMap.entrySet()) {
            if (builder.length() > 0)
                builder.append("&");
            builder.append(entry.getKey()).append("=").append(entry.getValue() != null ? entry.getValue() : "");
        }
        return builder.toString();
    }

    public DataSource getDatasource() {
        return datasource;
    }

    public String getUrl() {
        return url;
    }

    /**
     * Needed because DriverManager will not load a driver ouside of the system
     * classloader
     */
    public static class ProxyDriver implements Driver {

        private Driver driver;

        ProxyDriver(Driver d) {
            this.driver = d;
        }

        /*
         * JDK 7 compatibility
         */
        @Override
        public java.util.logging.Logger getParentLogger() {
            return null;
        }

        @Override
        public boolean acceptsURL(String u) throws SQLException {
            return this.driver.acceptsURL(u);
        }

        @Override
        public Connection connect(String u, Properties p) throws SQLException {
            return this.driver.connect(u, p);
        }

        @Override
        public int getMajorVersion() {
            return this.driver.getMajorVersion();
        }

        @Override
        public int getMinorVersion() {
            return this.driver.getMinorVersion();
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String u, Properties p) throws SQLException {
            return this.driver.getPropertyInfo(u, p);
        }

        @Override
        public boolean jdbcCompliant() {
            return this.driver.jdbcCompliant();
        }
    }

    public static class PlayConnectionCustomizer implements ConnectionCustomizer {

        public static Map<String, Integer> isolationLevels;

        static {
            isolationLevels = new HashMap<String, Integer>();
            isolationLevels.put("NONE", Connection.TRANSACTION_NONE);
            isolationLevels.put("READ_UNCOMMITTED", Connection.TRANSACTION_READ_UNCOMMITTED);
            isolationLevels.put("READ_COMMITTED", Connection.TRANSACTION_READ_COMMITTED);
            isolationLevels.put("REPEATABLE_READ", Connection.TRANSACTION_REPEATABLE_READ);
            isolationLevels.put("SERIALIZABLE", Connection.TRANSACTION_SERIALIZABLE);
        }

        @Override
        public void onAcquire(Connection c, String parentDataSourceIdentityToken) {
            Integer isolation = getIsolationLevel();
            if (isolation != null) {
                try {
                    Logger.trace("Setting connection isolation level to %s", isolation);
                    c.setTransactionIsolation(isolation);
                } catch (SQLException e) {
                    throw new DatabaseException("Failed to set isolation level to " + isolation, e);
                }
            }
        }

        @Override
        public void onDestroy(Connection c, String parentDataSourceIdentityToken) {
        }

        @Override
        public void onCheckOut(Connection c, String parentDataSourceIdentityToken) {
        }

        @Override
        public void onCheckIn(Connection c, String parentDataSourceIdentityToken) {
        }

        /**
         * Get the isolation level from either the isolationLevels map, or by
         * parsing into an int.
         */
        private Integer getIsolationLevel() {
            String isolation = Play.configuration.getProperty("db.isolation");
            if (isolation == null) {
                return null;
            }
            Integer level = isolationLevels.get(isolation);
            if (level != null) {
                return level;
            }

            try {
                return Integer.valueOf(isolation);
            } catch (NumberFormatException e) {
                throw new DatabaseException("Invalid isolation level configuration" + isolation, e);
            }
        }
    }
}
