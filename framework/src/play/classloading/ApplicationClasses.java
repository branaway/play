package play.classloading;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javassist.ClassPool;
import javassist.CtClass;
import play.Logger;
import play.Play;
import play.PlayPlugin;
import play.classloading.enhancers.Enhancer;
import play.exceptions.UnexpectedException;
import play.vfs.VirtualFile;
import bran.model.dependency.DependencyVisitor;

/**
 * Application classes container.
 */
public class ApplicationClasses {
	
	

    /**
     * Reference to the eclipse compiler.
     */
    ApplicationCompiler compiler = new ApplicationCompiler(this);
    /**
     * Cache of all compiled classes
     */
    Map<String, ApplicationClass> classes = new HashMap<>();

    /**
     * Clear the classes cache
     */
    public void clear() {
        classes = new HashMap<>();
    }

    /**
     * Get a class by name
     * @param name The fully qualified class name
     * @return The ApplicationClass or null
     */
    public ApplicationClass getApplicationClass(String name) {
        if (!classes.containsKey(name)) {
            VirtualFile javaFile = getJava(name);
            if (javaFile != null) {
                classes.put(name, new ApplicationClass(name, javaFile));
            }
        }
        return classes.get(name);
    }

    /**
     * Retrieve all application classes assignable to this class.
     * @param clazz The superclass, or the interface.
     * @return A list of application classes.
     */
    public List<ApplicationClass> getAssignableClasses(Class<?> clazz) {
        List<ApplicationClass> results = new ArrayList<ApplicationClass>();
        if (clazz != null) {
            for (ApplicationClass applicationClass : new ArrayList<ApplicationClass>(classes.values())) {
                if (!applicationClass.isClass()) {
                    continue;
                }
                try {
                    Play.classloader.loadClass(applicationClass.name);
                } catch (ClassNotFoundException ex) {
                    throw new UnexpectedException(ex);
                }
                try {
                    if (clazz.isAssignableFrom(applicationClass.javaClass) && !applicationClass.javaClass.getName().equals(clazz.getName())) {
                        results.add(applicationClass);
                    }
                } catch (Exception e) {
                }
            }
        }
        return results;
    }

    /**
     * Retrieve all application classes with a specific annotation.
     * @param clazz The annotation class.
     * @return A list of application classes.
     */
    public List<ApplicationClass> getAnnotatedClasses(Class<? extends Annotation> clazz) {
        List<ApplicationClass> results = new ArrayList<ApplicationClass>();
        for (ApplicationClass applicationClass : classes.values()) {
            if (!applicationClass.isClass()) {
                continue;
            }
            try {
                Play.classloader.loadClass(applicationClass.name);
            } catch (ClassNotFoundException ex) {
                throw new UnexpectedException(ex);
            }
            if (applicationClass.javaClass != null && applicationClass.javaClass.isAnnotationPresent(clazz)) {
                results.add(applicationClass);
            }
        }
        return results;
    }

    /**
     * All loaded classes.
     * @return All loaded classes
     */
    public List<ApplicationClass> all() {
        return new ArrayList<ApplicationClass>(classes.values());
    }

    /**
     * Put a new class to the cache.
     */
    public void add(ApplicationClass applicationClass) {
        classes.put(applicationClass.name, applicationClass);
    }

    /**
     * Remove a class from cache
     */
    public void remove(ApplicationClass applicationClass) {
        classes.remove(applicationClass.name);
    }

    public void remove(String applicationClass) {
        classes.remove(applicationClass);
    }

    /**
     * Does this class is already loaded ?
     * @param name The fully qualified class name
     */
    public boolean hasClass(String name) {
        return classes.containsKey(name);
    }

    /**
     * Represent a application class
     */
    public static class ApplicationClass {

        /**
         * The fully qualified class name
         */
        public String name;
        /**
         * A reference to the java source file
         */
        public VirtualFile javaFile;
        /**
         * The Java source
         */
        public String javaSource;
        /**
         * The compiled byteCode
         */
        public byte[] javaByteCode;
        /**
         * The enhanced byteCode
         */
        public byte[] enhancedByteCode;
        /**
         * The in JVM loaded class
         */
        public Class<?> javaClass;
        /**
         * The in JVM loaded package
         */
        public Package javaPackage;
        /**
         * Last time than this class was compiled
         */
        public Long timestamp = 0L;
        /**
         * Is this class compiled
         */
        boolean compiled;
        /**
         * Signatures checksum
         */
        public int sigChecksum;
		public String sigChecksumString;
        
        public int getSigChecksum() {
        	return sigChecksum;
        }
        
        public String getSigChecksumString() {
        	return sigChecksumString;
        }
        
        // bran: the direct application class this class is depending on, 
        Set<String> immediateDependencies = new HashSet<>();
        
        public ApplicationClass() {
        }

        public ApplicationClass(String name) {
            this(name, getJava(name));
        }

        public ApplicationClass(String name, VirtualFile javaFile) {
            this.name = name;
            this.javaFile = getJava(name);
            this.reset();
        }

        /**
         * Need to refresh this class ! bran: renamed from refresh()
         */
        public void reset() {
            if (this.javaFile != null) {
                this.javaSource = this.javaFile.contentAsString();
            }
            this.javaByteCode = null;
            this.enhancedByteCode = null;
            this.compiled = false;
            this.timestamp = 0L;
        }
        
    	static final ClassPool enhanceChecker_classPool = Enhancer.newClassPool();
        static final CtClass ctPlayPluginClass = enhanceChecker_classPool.makeClass(PlayPlugin.class.getName());


        /**
         * Enhance this class
         * @return the enhanced byteCode
         */
        public byte[] enhance() {
            this.enhancedByteCode = this.javaByteCode;
         
            if(this.name.startsWith("japidviews"))
            	return this.enhancedByteCode;

           // bran experimental: no enhancement 
            if (!this.name.endsWith("package-info")) {

                // before we can start enhancing this class we must make sure it is not a PlayPlugin.
                // PlayPlugins can be included as regular java files in a Play-application.
                // If a PlayPlugin is present in the application, it is loaded when other plugins are loaded.
                // All plugins must be loaded before we can start enhancing.
                // This is a problem when loading PlayPlugins bundled as regular app-class since it uses the same classloader
                // as the other (soon to be) enhanced play-app-classes.
                boolean shouldEnhance = true;
                try {
                    CtClass ctClass = enhanceChecker_classPool.makeClass(new ByteArrayInputStream(this.enhancedByteCode));
                    if (ctClass.subclassOf(ctPlayPluginClass)) {
                        shouldEnhance = false;
                    }
                } catch( Exception e) {
                    // nop
                }

                if (shouldEnhance) {
                    Play.pluginCollection.enhance(this);
                }
            }
            if (System.getProperty("precompile") != null) {
                try {
                    // emit bytecode to standard class layout as well
                    File f = Play.getFile("precompiled/java/" + name.replace(".", "/") + ".class");
                    f.getParentFile().mkdirs();
                    FileOutputStream fos = new FileOutputStream(f);
                    try {
                        fos.write(this.enhancedByteCode);
                    }
                    finally {
                        fos.close();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            calcDependencies();
            return this.enhancedByteCode;
            
        }

        /**
		 * @author Bing Ran (bing.ran@gmail.com)
		 */
		private void calcDependencies() {
//			DependencyVisitor dv = new DependencyVisitor("models/", "controllers/");
//			new ClassReader(this.enhancedByteCode).accept(dv, 0);
			this.immediateDependencies = DependencyVisitor.getDependencies(this.enhancedByteCode, "models/");
			// bran: the above dependency hard work seems only for use in JPA model dependency resolving added a long while back
			// I disable the controllers for now, since lamda in controller causes ASM issues such as  String index overflow.
			//			this.immediateDependencies = DependencyVisitor.getDependencies(this.enhancedByteCode, "models/", "controllers/");
		}

		/**
         * Is this class already compiled but not defined ?
         * @return if the class is compiled but not defined
         */
        public boolean isDefinable() {
            return compiled && javaClass != null;
        }

        public boolean isClass() {
            return isClass(this.name);
        }

	public static boolean isClass(String name) {
            return !name.endsWith("package-info");
	}

        public String getPackage() {
            int dot = name.lastIndexOf('.');
            return dot > -1 ? name.substring(0, dot) : "";
        }

        /**
         * Compile the class from Java source
         * @return the bytes that comprise the class file
         */
        public byte[] compile() {
            long start = System.currentTimeMillis();
            Play.classes.compiler.compile(new String[]{this.name});

            if (Logger.isTraceEnabled()) {
                Logger.trace("%sms to compile class %s", System.currentTimeMillis() - start, name);
            }

            return this.javaByteCode;
        }

        /**
         * Unload the class
         */
        public void uncompile() {
            this.javaClass = null;
        }

        /**
         * Call back when a class is compiled.
         * @param code The bytecode.
         */
        public void compiled(byte[] code) {
            javaByteCode = code;
            enhancedByteCode = code;
            compiled = true;
            this.timestamp = this.javaFile.lastModified();
        }

        @Override
        public String toString() {
            return name + " (compiled:" + compiled + ")";
        }

		/**
		 * @return the immediateDependencies
		 */
		public Set<String> getImmediateDependencies() {
			return immediateDependencies;
		}
    }

    // ~~ Utils
    /**
     * Retrieve the corresponding source file for a given class name.
     * It handles innerClass too !
     * @param name The fully qualified class name 
     * @return The virtualFile if found
     */
    public static VirtualFile getJava(String name) {
        String fileName = name;
        if (fileName.contains("$")) {
            fileName = fileName.substring(0, fileName.indexOf("$"));
        }
        // the local variable fileOrDir is important!
        String fileOrDir = fileName.replace(".", "/");
        fileName = fileOrDir + ".java";
        for (VirtualFile path : Play.javaPath) {
            // 1. check if there is a folder (without extension)
            VirtualFile javaFile = path.child(fileOrDir);
                  
            if (javaFile.exists() && javaFile.isDirectory() && javaFile.matchName(fileOrDir)) {
                // we found a directory (package)
                return null;
            }
            // 2. check if there is a file
            javaFile = path.child(fileName);
            if (javaFile.exists() && javaFile.matchName(fileName)) {
                return javaFile;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return classes.toString();
    }
}
