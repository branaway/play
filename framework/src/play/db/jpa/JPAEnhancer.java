package play.db.jpa;

import java.io.IOException;

import org.objectweb.asm.ClassVisitor;

import bran.ControllerClassVisitor;
import bran.ModelClassVisitor;
import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;
import play.Logger;
import play.classloading.ApplicationClasses.ApplicationClass;
import play.classloading.enhancers.Enhancer;

//import javassist.CtClass;
//import javassist.CtMethod;
//import play.Logger;
//import play.classloading.ApplicationClasses.ApplicationClass;
//import play.classloading.enhancers.Enhancer;

/**
 * Enhance JPABase entities classes
 * 
 * bran: I changed some of the returned type of the added method from JPABase to GenericModel, 
 * to match the change I have made to the GenericModel.
 * 
 */
public class JPAEnhancer extends Enhancer {

    @Override
	public void enhanceThisClass(ApplicationClass applicationClass) throws Exception {
    	if (!applicationClass.name.startsWith("models.")) 
    		return;
    	
//        byte[] bytecode = enhancedWithJavassist(applicationClass);
        byte[] bytecode = enhancedWithASM(applicationClass);

        applicationClass.enhancedByteCode = bytecode;
    }

    /**
     * XXX
	 * @author Bing Ran (bing.ran@gmail.com)
	 * @param applicationClass
	 * @return
	 */
	private byte[] enhancedWithASM(ApplicationClass applicationClass) {
	       return ModelClassVisitor.visitModel(applicationClass.enhancedByteCode);
	}

	// not compatible with JDK8 with old javassist. might be OK with newer version of Javassist 16-3-1
	private byte[] enhancedWithJavassist(ApplicationClass applicationClass) throws IOException, NotFoundException,
			ClassNotFoundException, CannotCompileException {
		CtClass ctClass = makeClass(applicationClass);

        if (!ctClass.subtypeOf(classPool.get("play.db.jpa.JPABase"))) {
            return null;
        }

        // Enhance only JPA entities
        if (!hasAnnotation(ctClass, "javax.persistence.Entity")) {
            return null;
        }

        String entityName = ctClass.getName();
        Logger.info("enhancing " + entityName);

        // count
        CtMethod count = CtMethod.make("public static long count() { return getJPAConfig("+entityName+".class).jpql.count(\"" + entityName + "\"); }", ctClass);
        ctClass.addMethod(count);

        // count2
        CtMethod count2 = CtMethod.make("public static long count(String query, Object[] params) { return  getJPAConfig("+entityName+".class).jpql.count(\"" + entityName + "\", query, params); }", ctClass);
        ctClass.addMethod(count2);

        // findAll
        CtMethod findAll = CtMethod.make("public static java.util.List findAll() { return  getJPAConfig("+entityName+".class).jpql.findAll(\"" + entityName + "\"); }", ctClass);
        ctClass.addMethod(findAll);

        // findById
//        CtMethod findById = CtMethod.make("public static play.db.jpa.JPABase findById(Object id) { return  getJPAConfig("+entityName+".class).jpql.findById(\"" + entityName + "\", id); }", ctClass);
        CtMethod findById = CtMethod.make("public static play.db.jpa.GenericModel findById(Object id) { return  getJPAConfig("+entityName+".class).jpql.findById(\"" + entityName + "\", id); }", ctClass);
        ctClass.addMethod(findById);

        // find
        CtMethod find = CtMethod.make("public static play.db.jpa.GenericModel.JPAQuery find(String query, Object[] params) { return  getJPAConfig("+entityName+".class).jpql.find(\"" + entityName + "\", query, params); }", ctClass);
        ctClass.addMethod(find);

        // find
        CtMethod find2 = CtMethod.make("public static play.db.jpa.GenericModel.JPAQuery find() { return  getJPAConfig("+entityName+".class).jpql.find(\"" + entityName + "\"); }", ctClass);
        ctClass.addMethod(find2);

        // all
        CtMethod all = CtMethod.make("public static play.db.jpa.GenericModel.JPAQuery all() { return  getJPAConfig("+entityName+".class).jpql.all(\"" + entityName + "\"); }", ctClass);
        ctClass.addMethod(all);

        // delete
        CtMethod delete = CtMethod.make("public static int delete(String query, Object[] params) { return  getJPAConfig("+entityName+".class).jpql.delete(\"" + entityName + "\", query, params); }", ctClass);
        ctClass.addMethod(delete);

        // deleteAll
        CtMethod deleteAll = CtMethod.make("public static int deleteAll() { return  getJPAConfig("+entityName+".class).jpql.deleteAll(\"" + entityName + "\"); }", ctClass);
        ctClass.addMethod(deleteAll);

        // findOneBy
//        CtMethod findOneBy = CtMethod.make("public static play.db.jpa.JPABase findOneBy(String query, Object[] params) { return  getJPAConfig("+entityName+".class).jpql.findOneBy(\"" + entityName + "\", query, params); }", ctClass);
        CtMethod findOneBy = CtMethod.make("public static play.db.jpa.GenericModel findOneBy(String query, Object[] params) { return  getJPAConfig("+entityName+".class).jpql.findOneBy(\"" + entityName + "\", query, params); }", ctClass);
        ctClass.addMethod(findOneBy);

        // create
        CtMethod create = CtMethod.make("public static play.db.jpa.JPABase create(String name, play.mvc.Scope.Params params) { return  getJPAConfig("+entityName+".class).jpql.create(\"" + entityName + "\", name, params); }", ctClass);
        ctClass.addMethod(create);

        
        // Done.
        byte[] bytecode = ctClass.toBytecode();
        ctClass.defrost();
		return bytecode;
	}

}
