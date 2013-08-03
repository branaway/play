package play;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import play.cache.Cache;
import play.classloading.ApplicationClasses;
import play.classloading.ApplicationClassloader;
import play.exceptions.CompilationException;
import play.exceptions.PlayException;
import play.exceptions.UnexpectedException;
import play.libs.IO;
import play.mvc.Http;
import play.mvc.Router;
import play.plugins.PluginCollection;
import play.templates.TemplateLoader;
import play.utils.OrderSafeProperties;
import play.vfs.VirtualFile;

/**
 * Main framework class
 * @author bran
 * @author original authors
 */
public class Play {

    /**
     * 2 modes
     */
    public enum Mode {

        /**
         * Enable development-specific features, e.g. view the documentation at the URL {@literal "/@documentation"}.
         */
        DEV,
        /**
         * Disable development-specific features.
         */
        PROD;

        public boolean isDev() {
            return this == DEV;
        }

        public boolean isProd() {
            return this == PROD;
        }
    }
    /**
     * Is the application initialized
     */
    public static boolean initialized = false;

    /**
     * Is the application started
     */
    public static boolean started = false;
    /**
     * True when the one and only shutdown hook is enabled
     */
    private static boolean shutdownHookEnabled = false;
    /**
     * The framework ID
     */
    public static String id;
    /**
     * The application mode
     */
    public static Mode mode;
    /**
     * The application root
     */
    public static File applicationPath = null;
    /**
     * tmp dir
     */
    public static File tmpDir = null;
    /**
     * tmp dir is readOnly
     */
    public static boolean readOnlyTmp = false;
    /**
     * The framework root
     */
    public static File frameworkPath = null;
    /**
     * All loaded application classes
     */
    public static ApplicationClasses classes;
    /**
     * The application classLoader
     */
    public static ApplicationClassloader classloader;
    /**
     * All paths to search for files
     */
    public static List<VirtualFile> roots = new ArrayList<VirtualFile>(16);
    /**
     * All paths to search for Java files
     */
    public static List<VirtualFile> javaPath;
    /**
     * All paths to search for templates files
     */
    public static List<VirtualFile> templatesPath;
    /**
     * Main routes file
     */
    public static VirtualFile routes;
    /**
     * Plugin routes files
     */
    public static Map<String, VirtualFile> modulesRoutes;
    /**
     * The loaded configuration files
     */
    public static Set<VirtualFile> confs = new HashSet<VirtualFile>(1);
    /**
     * The app configuration (already resolved from the framework id)
     */
    public static Properties configuration;
    /**
     * The last time than the application has started
     */
    public static long startedAt;
    /**
     * The list of supported locales
     */
    public static List<String> langs = new ArrayList<String>(16);
    /**
     * The very secret key
     */
    public static String secretKey;
    
  // bran
    public static boolean invokeDirect; 

    /**
     * pluginCollection that holds all loaded plugins and all enabled plugins..
     */
    public static PluginCollection pluginCollection = new PluginCollection();
    /**
     * Readonly list containing currently enabled plugins.
     * This list is updated from pluginCollection when pluginCollection is modified
     * Play plugins
     * @deprecated Use pluginCollection instead.
     */
    @Deprecated
    public static List<PlayPlugin> plugins = pluginCollection.getEnabledPlugins();
    /**
     * Modules
     */
    public static Map<String, VirtualFile> modules = new HashMap<String, VirtualFile>(16);
    /**
     * Framework version
     */
    public static String version = null;
    /**
     * Context path (when several application are deployed on the same host)
     */
    public static String ctxPath = "";
    static boolean firstStart = true;
    public static boolean usePrecompiled = false;
    public static boolean forceProd = false;
    /**
     * Lazy load the templates on demand
     */
    public static boolean lazyLoadTemplates = false;
    /**
     * This is used as default encoding everywhere related to the web: request, response, WS
     */
    public static String defaultWebEncoding = "utf-8";

    /**
     * This flag indicates if the app is running in a standalone Play server or
     * as a WAR in an applicationServer
     */
    public static boolean standalonePlayServer = true;

    /**
     * Init the framework
     *
     * @param root The application path
     * @param id   The framework id to use
     */
    public static void init(File root, String id) {
        // Simple things
        Play.id = id;
        Play.started = false;
        Play.applicationPath = root;

        // load all play.static of exists
        initStaticStuff();

        guessFrameworkPath();

        // Read the configuration file
        readConfiguration();

        Play.classes = new ApplicationClasses();

        // Configure logs
        Logger.init();
        String logLevel = configuration.getProperty("application.log", "INFO");

        //only override log-level if Logger was not configured manually
        if (!Logger.configuredManually) {
            Logger.setUp(logLevel);
        }
        Logger.recordCaller = Boolean.parseBoolean(configuration.getProperty("application.log.recordCaller", "false"));

        Logger.info("Starting %s", root.getAbsolutePath());

        if (configuration.getProperty("play.tmp", "tmp").equals("none")) {
            tmpDir = null;
            Logger.debug("No tmp folder will be used (play.tmp is set to none)");
        } else {
            tmpDir = new File(configuration.getProperty("play.tmp", "tmp"));
            if (!tmpDir.isAbsolute()) {
                tmpDir = new File(applicationPath, tmpDir.getPath());
            }

            if (Logger.isTraceEnabled()) {
                Logger.trace("Using %s as tmp dir", Play.tmpDir);
            }

            if (!tmpDir.exists()) {
                try {
                    if (readOnlyTmp) {
                        throw new Exception("ReadOnly tmp");
                    }
                    tmpDir.mkdirs();
                } catch (Throwable e) {
                    tmpDir = null;
                    Logger.warn("No tmp folder will be used (cannot create the tmp dir)");
                }
            }
        }

        // Mode
        mode = Mode.valueOf(configuration.getProperty("application.mode", "DEV").toUpperCase());
        if (usePrecompiled || forceProd) {
            mode = Mode.PROD;
        }

        // Context path
        ctxPath = configuration.getProperty("http.path", ctxPath);

        // Build basic java source path
        VirtualFile appRoot = VirtualFile.open(applicationPath);
        roots.add(appRoot);
        javaPath = new CopyOnWriteArrayList<VirtualFile>();
        javaPath.add(appRoot.child("app"));
        javaPath.add(appRoot.child("conf"));

        // Build basic templates path
        if (appRoot.child("app/views").exists()) {
            templatesPath = new ArrayList<VirtualFile>(2);
            templatesPath.add(appRoot.child("app/views"));
        } else {
            templatesPath = new ArrayList<VirtualFile>(1);
        }

        // Main route file
        routes = appRoot.child("conf/routes");

        // Plugin route files
        modulesRoutes = new HashMap<String, VirtualFile>(16);

        // Load modules
        loadModules();

        // Load the templates from the framework after the one from the modules
        templatesPath.add(VirtualFile.open(new File(frameworkPath, "framework/templates")));

        // Enable a first classloader
        classloader = new ApplicationClassloader();

        // Fix ctxPath
        if ("/".equals(Play.ctxPath)) {
            Play.ctxPath = "";
        }

        // Default cookie domain
        Http.Cookie.defaultDomain = configuration.getProperty("application.defaultCookieDomain", null);
        if (Http.Cookie.defaultDomain != null) {
            Logger.info("Using default cookie domain: " + Http.Cookie.defaultDomain);
        }

        // Plugins
        pluginCollection.loadPlugins();

        // Done !
        if (mode == Mode.PROD || System.getProperty("precompile") != null) {
            mode = Mode.PROD;
            if (preCompile() && System.getProperty("precompile") == null) {
                start();
            } else {
                return;
            }
        } else {
            Logger.warn("You're running Play! in DEV mode");
        }

        // Plugins
        pluginCollection.onApplicationReady();

        Play.initialized = true;
    }

    public static void guessFrameworkPath() {
        // Guess the framework path
        try {
            URL versionUrl = Play.class.getResource("/play/version");
            // Read the content of the file
            Play.version = new LineNumberReader(new InputStreamReader(versionUrl.openStream())).readLine();

            // This is used only by the embedded server (Mina, Netty, Jetty etc)
            URI uri = new URI(versionUrl.toString().replace(" ", "%20"));
            if (frameworkPath == null || !frameworkPath.exists()) {
                if (uri.getScheme().equals("jar")) {
                    String jarPath = uri.getSchemeSpecificPart().substring(5, uri.getSchemeSpecificPart().lastIndexOf("!"));
                    frameworkPath = new File(jarPath).getParentFile().getParentFile().getAbsoluteFile();
                } else if (uri.getScheme().equals("file")) {
                    frameworkPath = new File(uri).getParentFile().getParentFile().getParentFile().getParentFile();
                } else {
                    throw new UnexpectedException("Cannot find the Play! framework - trying with uri: " + uri + " scheme " + uri.getScheme());
                }
            }
        } catch (Exception e) {
            throw new UnexpectedException("Where is the framework ?", e);
        }
    }

    /**
     * Read application.conf and resolve overriden key using the play id mechanism.
     */
    public static void readConfiguration() {
        confs = new HashSet<VirtualFile>();
        configuration = readOneConfigurationFile("application.conf");
        extractHttpPort();
        // Plugins
        pluginCollection.onConfigurationRead();
     }

    private static void extractHttpPort() {
        final String javaCommand = System.getProperty("sun.java.command", "");
        jregex.Matcher m = new jregex.Pattern(".* --http.port=({port}\\d+)").matcher(javaCommand);
        if (m.matches()) {
            configuration.setProperty("http.port", m.group("port"));
        }
    }


    private static Properties readOneConfigurationFile(String filename) {
        Properties propsFromFile=null;
        VirtualFile appRoot = VirtualFile.open(applicationPath);
        
        VirtualFile conf = appRoot.child("conf/" + filename);
        if (confs.contains(conf)) {
            throw new RuntimeException("Detected recursive @include usage. Have seen the file " + filename + " before");
        }
        
        try {
            propsFromFile = IO.readUtf8Properties(conf.inputstream());
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException) {
                Logger.fatal("Cannot read "+filename);
                fatalServerErrorOccurred();
            }
        }
        confs.add(conf);
        
        // OK, check for instance specifics configuration
        Properties newConfiguration = new OrderSafeProperties();
        Pattern pattern = Pattern.compile("^%([a-zA-Z0-9_\\-]+)\\.(.*)$");
        for (Object key : propsFromFile.keySet()) {
            Matcher matcher = pattern.matcher(key + "");
            if (!matcher.matches()) {
                newConfiguration.put(key, propsFromFile.get(key).toString().trim());
            }
        }
        for (Object key : propsFromFile.keySet()) {
            Matcher matcher = pattern.matcher(key + "");
            if (matcher.matches()) {
                String instance = matcher.group(1);
                if (instance.equals(id)) {
                    newConfiguration.put(matcher.group(2), propsFromFile.get(key).toString().trim());
                }
            }
        }
        propsFromFile = newConfiguration;
        // Resolve ${..}
        pattern = Pattern.compile("\\$\\{([^}]+)}");
        for (Object key : propsFromFile.keySet()) {
            String value = propsFromFile.getProperty(key.toString());
            Matcher matcher = pattern.matcher(value);
            StringBuffer newValue = new StringBuffer(100);
            while (matcher.find()) {
                String jp = matcher.group(1);
                String r;
                if (jp.equals("application.path")) {
                    r = Play.applicationPath.getAbsolutePath();
                } else if (jp.equals("play.path")) {
                    r = Play.frameworkPath.getAbsolutePath();
                } else {
                    r = System.getProperty(jp);
                    if (r == null) {
                        r = System.getenv(jp);
                    }
                    if (r == null) {
                        Logger.warn("Cannot replace %s in configuration (%s=%s)", jp, key, value);
                        continue;
                    }
                }
                matcher.appendReplacement(newValue, r.replaceAll("\\\\", "\\\\\\\\"));
            }
            matcher.appendTail(newValue);
            propsFromFile.setProperty(key.toString(), newValue.toString());
        }
        // Include
        Map<Object, Object> toInclude = new HashMap<Object, Object>(16);
        for (Object key : propsFromFile.keySet()) {
            if (key.toString().startsWith("@include.")) {
                try {
                    String filenameToInclude = propsFromFile.getProperty(key.toString());
                    toInclude.putAll( readOneConfigurationFile(filenameToInclude) );
                } catch (Exception ex) {
                    Logger.warn("Missing include: %s", key);
                }
            }
        }
        propsFromFile.putAll(toInclude);

        return propsFromFile;
    }

    /**
     * Start the application.
     * Recall to restart !
     */
    public static synchronized void start() {
        try {

            if (started) {
                stop();
            }

            if( standalonePlayServer) {
                // Can only register shutdown-hook if running as standalone server
                if (!shutdownHookEnabled) {
                    //registers shutdown hook - Now there's a good chance that we can notify
                    //our plugins that we're going down when some calls ctrl+c or just kills our process..
                    shutdownHookEnabled = true;
                    Runtime.getRuntime().addShutdownHook(new Thread() {
                        public void run() {
                            Play.stop();
                        }
                    });
                }
            }

            if (mode == Mode.DEV) {
                // Need a new classloader
                classloader = new ApplicationClassloader();
                // Reload plugins
                pluginCollection.reloadApplicationPlugins();

            }

            // Reload configuration
            readConfiguration();

            // Configure logs
            String logLevel = configuration.getProperty("application.log", "INFO");
            //only override log-level if Logger was not configured manually
            if (!Logger.configuredManually) {
                Logger.setUp(logLevel);
            }
            Logger.recordCaller = Boolean.parseBoolean(configuration.getProperty("application.log.recordCaller", "false"));

            // Locales
            langs = new ArrayList<String>(Arrays.asList(configuration.getProperty("application.langs", "").split(",")));
            if (langs.size() == 1 && langs.get(0).trim().length() == 0) {
                langs = new ArrayList<String>(16);
            }

            // Clean templates
            TemplateLoader.cleanCompiledCache();

            // SecretKey
            secretKey = configuration.getProperty("application.secret", "").trim();
            if (secretKey.length() == 0) {
                Logger.warn("No secret key defined. Sessions will not be encrypted");
            }

            // Default web encoding
            String _defaultWebEncoding = configuration.getProperty("application.web_encoding");
            if (_defaultWebEncoding != null) {
                Logger.info("Using custom default web encoding: " + _defaultWebEncoding);
                defaultWebEncoding = _defaultWebEncoding;
                // Must update current response also, since the request/response triggering
                // this configuration-loading in dev-mode have already been
                // set up with the previous encoding
                if (Http.Response.current() != null) {
                    Http.Response.current().encoding = _defaultWebEncoding;
                }
            }


            // Try to load all classes
            Play.classloader.getAllClasses();

            // Routes
            Router.detectChanges(ctxPath);

            // Cache
            Cache.init();

            // Plugins
            try {
                pluginCollection.onApplicationStart();
            } catch (Exception e) {
                if (Play.mode.isProd()) {
                    Logger.error(e, "Can't start in PROD mode with errors");
                }
                if (e instanceof RuntimeException) {
                    throw (RuntimeException) e;
                }
                throw new UnexpectedException(e);
            }

            if (firstStart) {
                Logger.info("Application '%s' is now started !", configuration.getProperty("application.name", ""));
                firstStart = false;
            }

          // bran added option to invoke the invocation directly
	      	String invokeDirectConfig = Play.configuration.getProperty("action.invocation.direct", "false");
   		  if (invokeDirectConfig.equals("false") || invokeDirectConfig.equals("no")) {
	 	  	invokeDirect = false;
	 	  }
	 	  else {
	 	  	invokeDirect = true;
		  }
		  
   		  if (invokeDirect)
   			  Logger.info("Skip the executor in invoking actions");

            // We made it
            started = true;
            startedAt = System.currentTimeMillis();

            // Plugins
            pluginCollection.afterApplicationStart();

        } catch (PlayException e) {
            started = false;
            try { Cache.stop(); } catch(Exception ignored) {}
            throw e;
        } catch (Exception e) {
            started = false;
            try { Cache.stop(); } catch(Exception ignored) {}
            throw new UnexpectedException(e);
        }
    }

    /**
     * Stop the application
     */
    public static synchronized void stop() {
        if (started) {
            Logger.trace("Stopping the play application");
            pluginCollection.onApplicationStop();
            started = false;
            Cache.stop();
            Router.lastLoading = 0L;
        }
    }

    /**
     * Force all java source and template compilation.
     *
     * @return success ?
     */
    static boolean preCompile() {
        if (usePrecompiled) {
            if (Play.getFile("precompiled").exists()) {
                classloader.getAllClasses();
                Logger.info("Application is precompiled");
                return true;
            }
            Logger.error("Precompiled classes are missing!!");
            fatalServerErrorOccurred();
            return false;
        }
        try {
            Logger.info("Precompiling ...");
            Thread.currentThread().setContextClassLoader(Play.classloader);
            long start = System.currentTimeMillis();
            classloader.getAllClasses();

            if (Logger.isTraceEnabled()) {
                Logger.trace("%sms to precompile the Java stuff", System.currentTimeMillis() - start);
            }

            if (!lazyLoadTemplates) {
                start = System.currentTimeMillis();
                TemplateLoader.getAllTemplate();

                if (Logger.isTraceEnabled()) {
                    Logger.trace("%sms to precompile the templates", System.currentTimeMillis() - start);
                }
            }
            return true;
        } catch (Throwable e) {
            Logger.error(e, "Cannot start in PROD mode with errors");
            fatalServerErrorOccurred();
            return false;
        }
    }

	// bran: probably not worth Atomic since dev mode is single-threaded
	private static AtomicLong lastTimeChecked = new AtomicLong(0);
    /**
     * Detect sources modifications
     */
    public static synchronized void detectChanges() {
        if (mode == Mode.PROD) {
            return;
        }
        
		// bran: give a delay
		if (System.currentTimeMillis() - lastTimeChecked.get() < Long.parseLong(configuration.getProperty(
				"bran.play.dev.detect.change.interval", "3000")))
			return;

        try {
            pluginCollection.beforeDetectingChanges();
            if(!pluginCollection.detectClassesChange()) {
                classloader.detectChanges();
            }
            Router.detectChanges(ctxPath);
            for(VirtualFile conf : confs) {
	            if (conf.lastModified() > startedAt) {
	            	Logger.info("Play.detectChanges: restart due to conf file change: " + conf.getName());
	                start();
	                return;
	            }
            }
            pluginCollection.detectChange();
            if (!Play.started) {
                throw new RuntimeException("Not started");
            }
            lastTimeChecked.set(System.currentTimeMillis());
        } catch (CompilationException ce) {
			// bran: check if this from Java code derived from japid view
			ce = mapJapidJavCodeError(ce);
			throw ce;
        } catch (PlayException e) {
            throw e;
        } catch (Exception e) {
            // We have to do a clean refresh
        	String message = e.getMessage();
        	if (message == null || e instanceof NullPointerException){
        		e.printStackTrace();
        	}
			Logger.info("Play.detectChanges: restart due to: " + message);
            start();
        }
    }

    /**
     * bran: error mapper for better error reporting
     * 
     * @param e
     * @return
     */
	public static CompilationException mapJapidJavCodeError(
			CompilationException e) {
		if (!e.isSourceAvailable())
			return e;

		// now map java error to japidview source code
		
		String srcFilePath = e.getSourceFile();
		if (!srcFilePath.startsWith("/app/japidviews/")) {
			return e;
		}

		String viewSourceFilePath = mapJavaToSrc(srcFilePath);
//		File file = new File(viewSourceFilePath);
		VirtualFile vf = VirtualFile.fromRelativePath(viewSourceFilePath);

		int oriLineNumber = mapJavaErrorLineToSrcLine(vf.contentAsString(), e.getLineNumber());
		// get line start and end
		// well not much sense. commented out
//		String viewSource = vf.contentAsString();
//		String[] viewLines = viewSource.split("\n");
//		String problemLineInView = viewLines[oriLineNumber - 1];
//		int start = 0;
//		for (int i = 0; i < oriLineNumber - 1; i++) {
//			start += viewLines[i].length() + 1;
//		}
//		int end = start -1 + problemLineInView.length();

//		e = new CompilationException(vf, "Java code error --> \"" + e.getMessage() + "\"", oriLineNumber, start, end);
		e = new CompilationException(vf, "Java  error --> \"" + e.getMessage() + "\"", oriLineNumber, 0, 0);
		return e;
	}

	static int mapJavaErrorLineToSrcLine(String sourceCode, int lineNum) {
		String[] codeLines = sourceCode.split("\n");
		String line = codeLines[lineNum - 1];
	
		int lineMarker = line.lastIndexOf("// line ");
		if (lineMarker < 1) {
			return 0;
		}
		int oriLineNumber = Integer.parseInt(line.substring(lineMarker + 8)
				.trim());
		return oriLineNumber;
	}
    /**
     * copied from Japid DirUtils
     * @param clazz
     * @return
     */
	public static String mapJavaToSrc(String k) {
		if (k.endsWith(".java"))
			k = k.substring(0, k.lastIndexOf(".java"));
		
		if (k.endsWith("_txt")) {
			return k.substring(0, k.lastIndexOf("_txt")) + ".txt";
		}
		else if (k.endsWith("_xml")) {
			return k.substring(0, k.lastIndexOf("_xml")) + ".xml";
		}
		else if (k.endsWith("_json")) {
			return k.substring(0, k.lastIndexOf("_json")) + ".json";
		}
		else if (k.endsWith("_css")) {
			return k.substring(0, k.lastIndexOf("_css")) + ".css";
		}
		else if (k.endsWith("_js")) {
			return k.substring(0, k.lastIndexOf("_js")) + ".js";
		}
		else { // including html
			return  k + ".html";
		}
	}
    
    @SuppressWarnings("unchecked")
    public static <T> T plugin(Class<T> clazz) {
        return (T) pluginCollection.getPluginInstance((Class<? extends PlayPlugin>) clazz);
    }



    /**
     * Allow some code to run very early in Play - Use with caution !
     */
    public static void initStaticStuff() {
        // Play! plugings
        Enumeration<URL> urls = null;
        try {
            urls = Play.class.getClassLoader().getResources("play.static");
        } catch (Exception e) {
        }
        while (urls != null && urls.hasMoreElements()) {
            URL url = urls.nextElement();
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream(), "utf-8"));
                String line = null;
                while ((line = reader.readLine()) != null) {
                    try {
                        Class.forName(line);
                    } catch (Exception e) {
                        Logger.warn("! Cannot init static: " + line);
                    }
                }
            } catch (Exception ex) {
                Logger.error(ex, "Cannot load %s", url);
            }
        }
    }

    /**
     * Load all modules.
     * You can even specify the list using the MODULES environement variable.
     */
    public static void loadModules() {
        if (System.getenv("MODULES") != null) {
            // Modules path is prepended with a env property
            if (System.getenv("MODULES") != null && System.getenv("MODULES").trim().length() > 0) {
                for (String m : System.getenv("MODULES").split(System.getProperty("os.name").startsWith("Windows") ? ";" : ":")) {
                    File modulePath = new File(m);
                    if (!modulePath.exists() || !modulePath.isDirectory()) {
                        Logger.error("Module %s will not be loaded because %s does not exist", modulePath.getName(), modulePath.getAbsolutePath());
                    } else {
                        final String modulePathName = modulePath.getName();
                        final String moduleName = modulePathName.contains("-") ?
                                modulePathName.substring(0, modulePathName.lastIndexOf("-")) :
                                modulePathName;
                        addModule(moduleName, modulePath);
                    }
                }
            }
        }
        for (Object key : configuration.keySet()) {
            String pName = key.toString();
            if (pName.startsWith("module.")) {
                Logger.warn("Declaring modules in application.conf is deprecated. Use dependencies.yml instead (%s)", pName);
                String moduleName = pName.substring(7);
                File modulePath = new File(configuration.getProperty(pName));
                if (!modulePath.isAbsolute()) {
                    modulePath = new File(applicationPath, configuration.getProperty(pName));
                }
                if (!modulePath.exists() || !modulePath.isDirectory()) {
                    Logger.error("Module %s will not be loaded because %s does not exist", moduleName, modulePath.getAbsolutePath());
                } else {
                    addModule(moduleName, modulePath);
                }
            }
        }

        // Load modules from modules/ directory
        File localModules = Play.getFile("modules");
        if (localModules.exists() && localModules.isDirectory()) {
            for (File module : localModules.listFiles()) {
                String moduleName = module.getName();
		if (moduleName.startsWith(".")) {
			Logger.info("Module %s is ignored, name starts with a dot", moduleName);
			continue;
		}
                if (moduleName.contains("-")) {
                    moduleName = moduleName.substring(0, moduleName.indexOf("-"));
                }
                if (module.isDirectory()) {
                    addModule(moduleName, module);
                } else {
                    File modulePath = new File(IO.readContentAsString(module).trim());
                    if (!modulePath.exists() || !modulePath.isDirectory()) {
                        Logger.error("Module %s will not be loaded because %s does not exist", moduleName, modulePath.getAbsolutePath());
                    } else {
                        addModule(moduleName, modulePath);
                    }

                }
            }
        }
        // Auto add special modules
        if (Play.runningInTestMode()) {
            addModule("_testrunner", new File(Play.frameworkPath, "modules/testrunner"));
        }
        if (Play.mode == Mode.DEV) {
            addModule("_docviewer", new File(Play.frameworkPath, "modules/docviewer"));
        }
    }

    /**
     * Add a play application (as plugin)
     *
     * @param path The application path
     */
    public static void addModule(String name, File path) {
        VirtualFile root = VirtualFile.open(path);
        modules.put(name, root);
        if (root.child("app").exists()) {
            javaPath.add(root.child("app"));
        }
        if (root.child("app/views").exists()) {
            templatesPath.add(root.child("app/views"));
        }
        if (root.child("conf/routes").exists()) {
            modulesRoutes.put(name, root.child("conf/routes"));
        }
        roots.add(root);
        if (!name.startsWith("_")) {
            Logger.info("Module %s is available (%s)", name, path.getAbsolutePath());
        }
    }

    /**
     * Search a VirtualFile in all loaded applications and plugins
     *
     * @param path Relative path from the applications root
     * @return The virtualFile or null
     */
    public static VirtualFile getVirtualFile(String path) {
        return VirtualFile.search(roots, path);
    }

    /**
     * Search a File in the current application
     *
     * @param path Relative path from the application root
     * @return The file even if it doesn't exist
     */
    public static File getFile(String path) {
        return new File(applicationPath, path);
    }

    
	/**
	 * get the page content for the error code
	 * An application must register an ErrorPager at bootstrap to serve server error code.
	 * 
	 * @param i
	 * @return
	 * @author bran
	 * @param string
	 */
	public static String getErrorPage(int i, PageFormat format, Map<String, Object> params) {
		if (errorPager == null) {
			switch (i) {
			case 404:
				return "URL not found";
			case 500:
				Object e = params.get("exception");
				return "System error 500: " + e;
			default:
				return "";
			}
		} else
			return errorPager.getErrorPage(i, format, params);
	}

	static ErrorPager errorPager;

	public static void setErrorPager(ErrorPager pager) {
		errorPager = pager;
	}


	public enum PageFormat {
		HTML,
		XML,
		JSON,
		TXT;

		public static PageFormat from(String format) {
			format = format.toLowerCase();
			if ("html".equals(format)) {
				return HTML;
			} else if ("xml".equals(format)) {
				return HTML;
			} else if ("json".equals(format)) {
				return JSON;
			} else if ("txt".equals(format)) {
				return TXT;
			} else {
				return HTML;
			}
		}
	}

	/**
	 * a bridge for apps to provide error message pages without bounding to any
	 * rendering engine
	 * 
	 * @author Bing Ran<bing_ran@hotmail.com>
	 * 
	 */
	public interface ErrorPager {

		/**
		 * 
		 * @param code
		 *            http error code: 404, 500 etc
		 * @param params
		 * @return
		 */
		String getErrorPage(int code, PageFormat format, Map<String, Object> params);
	}

    
    /**
     * Returns true if application is running in test-mode.
     * Test-mode is resolved from the framework id.
     *
     * Your app is running in test-mode if the framwork id (Play.id)
     * is 'test' or 'test-?.*'
     * @return true if testmode
     */
    public static boolean runningInTestMode() {
        return id.matches("test|test-?.*");
    }

    /**
     * bran: to make the old Cobertura module happy with the naming of the method
     * @return
     */
    public static boolean runingInTestMode() {
    	return runningInTestMode();
    }

    /**
     * Call this method when there has been a fatal error that Play cannot recover from
     */
    public static void fatalServerErrorOccurred() {
        if (standalonePlayServer) {
            // Just quit the process
            System.exit(-1);
        } else {
            // Cannot quit the process while running inside an applicationServer
            String msg = "A fatal server error occurred";
            Logger.error(msg);
            throw new Error(msg);
        }
    }
}
