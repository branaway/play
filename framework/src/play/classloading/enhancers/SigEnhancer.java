package play.classloading.enhancers;

import java.util.Set;

import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.CodeIterator;
import javassist.bytecode.LocalVariableAttribute;
import javassist.bytecode.Opcode;
import javassist.bytecode.annotation.Annotation;
import play.classloading.ApplicationClasses.ApplicationClass;

/**
 * Compute a unique hash for the class signature.
 * bran: added annotation member values as part signature. fixed field part of signature
 */
public class SigEnhancer extends Enhancer {

	@Override
    public void enhanceThisClass(ApplicationClass applicationClass) throws Exception {
		
        if (isScala(applicationClass)) {
            return;
        }

        final CtClass ctClass = makeClass(applicationClass);
        if (isScalaObject(ctClass)) {
            return;
        }

        StringBuilder sigChecksum = new StringBuilder();

        sigChecksum.append("Class->" + ctClass.getName() + ":");
        for (Annotation annotation : getAnnotations(ctClass).getAnnotations()) {
            sigChecksum.append(annotation + ",");
            addAnnoMembers(sigChecksum, annotation);
        }

        for (CtField field : ctClass.getDeclaredFields()) {
            sigChecksum.append(" Field->" + field.toString() + ":" + field.getModifiers());
            for (Annotation annotation : getAnnotations(field).getAnnotations()) {
                sigChecksum.append(annotation + ",");
                addAnnoMembers(sigChecksum, annotation);
            }
        }

        for (CtMethod method : ctClass.getDeclaredMethods()) {
            sigChecksum.append(" Method->" + method.getModifiers() + method.getName() + method.getSignature() + ":");
            for (Annotation annotation : getAnnotations(method).getAnnotations()) {
                sigChecksum.append(annotation + " ");
                addAnnoMembers(sigChecksum, annotation);
                
                
            }
            if(javassist.Modifier.isAbstract(method.getModifiers()))
            		continue;
            // Signatures names
            if (isController(applicationClass)) {
	            CodeAttribute codeAttribute = (CodeAttribute) method.getMethodInfo().getAttribute("Code");
	            if (codeAttribute != null) {
		            LocalVariableAttribute localVariableAttribute = (LocalVariableAttribute) codeAttribute.getAttribute("LocalVariableTable");
		            if (localVariableAttribute != null) {
		                for (int i = 0; i < localVariableAttribute.tableLength(); i++) {
		                    sigChecksum.append(localVariableAttribute.variableName(i) + ",");
		                }
		            }
	            }
            }
        }

        if (ctClass.getClassInitializer() != null) {
            sigChecksum.append("Static Code->");
            for (CodeIterator i = ctClass.getClassInitializer().getMethodInfo().getCodeAttribute().iterator(); i.hasNext();) {
                int index = i.next();
                int op = i.byteAt(index);
                sigChecksum.append(op);
                if (op == Opcode.LDC) { // constants
                    sigChecksum.append("[" + i.get().getConstPool().getLdcValue(i.byteAt(index + 1)) + "]");
                    ;
                }
                sigChecksum.append(".");
            }
        }

        if (ctClass.getName().endsWith("$")) {
            sigChecksum.append("Singletons->");
            for (CodeIterator i = ctClass.getDeclaredConstructors()[0].getMethodInfo().getCodeAttribute().iterator(); i.hasNext();) {
                int index = i.next();
                int op = i.byteAt(index);
                sigChecksum.append(op);
                if (op == Opcode.LDC) {
                    sigChecksum.append("[" + i.get().getConstPool().getLdcValue(i.byteAt(index + 1)) + "]");
                    ;
                }
                sigChecksum.append(".");
            }
        }

        // Done.
        applicationClass.sigChecksumString = sigChecksum.toString();
        applicationClass.sigChecksum = sigChecksum.toString().hashCode();
    }

	@SuppressWarnings("unchecked")
	private void addAnnoMembers(StringBuilder sigChecksum, Annotation annotation) {
		Set<String> set = (Set<String>) annotation.getMemberNames();
		if (set != null)
			set.forEach(mem -> {
				sigChecksum.append(mem + ":" + annotation.getMemberValue(mem));
			});
	}
}
