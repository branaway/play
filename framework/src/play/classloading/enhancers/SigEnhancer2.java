package play.classloading.enhancers;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Collectors;

import play.classloading.ApplicationClasses.ApplicationClass;

/**
 * Compute a unique hash for the class signature.
 */
abstract class SigEnhancer2{

    public void enhanceThisClass(ApplicationClass applicationClass) throws Exception {

    	Class ctClass = applicationClass.javaClass;
    	
        String sigChecksum = "";

        sigChecksum += "Class->" + ctClass.getName() + ":";
        
        sigChecksum += flatAnno(ctClass.getAnnotations());

		for (Field field : ctClass.getDeclaredFields()) {
            sigChecksum += (" Field->" + field.toGenericString() + ":");
            sigChecksum += flatAnno(field.getAnnotations());
        }

        for (Method method : ctClass.getDeclaredMethods()) {
            sigChecksum += (" Method->" + method.toGenericString() + ":");
            sigChecksum += flatAnno(method.getAnnotations());
            // Signatures names
            // bran
            if (applicationClass.name.startsWith("japidviews.")) {
            	// bran: ignore localvar change for japidviews.
            	continue;
            }
    
//            // bran: how about no check on local vars at all?
//            // this for controllers so that the old rendering engine will match locals to the references in the Groovy views. 
//            // leave them here for now for compatibility. 
//            CodeAttribute codeAttribute = (CodeAttribute) method.getMethodInfo().getAttribute("Code");
//            if (codeAttribute == null || javassist.Modifier.isAbstract(method.getModifiers())) {
//                continue;
//            }
//            LocalVariableAttribute localVariableAttribute = (LocalVariableAttribute) codeAttribute.getAttribute("LocalVariableTable");
//            if (localVariableAttribute != null) {
//                for (int i = 0; i < localVariableAttribute.tableLength(); i++) {
//                    sigChecksum.append(localVariableAttribute.variableName(i) + ",");
//                }
//            }
        }
//
//        if (ctClass.getClassInitializer() != null) {
//            sigChecksum.append("Static Code->");
//            for (CodeIterator i = ctClass.getClassInitializer().getMethodInfo().getCodeAttribute().iterator(); i.hasNext();) {
//                int index = i.next();
//                int op = i.byteAt(index);
//                sigChecksum.append(op);
//                if (op == Opcode.LDC) {
//                    sigChecksum.append("[" + i.get().getConstPool().getLdcValue(i.byteAt(index + 1)) + "]");
//                    ;
//                }
//                sigChecksum.append(".");
//            }
//        }
//
//        if (ctClass.getName().endsWith("$")) {
//            sigChecksum.append("Singletons->");
//            for (CodeIterator i = ctClass.getDeclaredConstructors()[0].getMethodInfo().getCodeAttribute().iterator(); i.hasNext();) {
//                int index = i.next();
//                int op = i.byteAt(index);
//                sigChecksum.append(op);
//                if (op == Opcode.LDC) {
//                    sigChecksum.append("[" + i.get().getConstPool().getLdcValue(i.byteAt(index + 1)) + "]");
//                    ;
//                }
//                sigChecksum.append(".");
//            }
//        }

        // Done.
        applicationClass.sigChecksum = sigChecksum.toString().hashCode();
    }

	private String flatAnno(Annotation[] annotations) {
		return Arrays.stream(annotations).map(Annotation::toString).reduce("", (a, b) -> a + b );
		// or
		//		return Arrays.stream(annotations).map(Annotation::toString).collect(Collectors.joining(","));
	}
}
