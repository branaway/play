package play.classloading.enhancers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;

import org.apache.commons.javaflow.bytecode.transformation.asm.AsmClassTransformer;

import play.Play;
import play.classloading.ApplicationClasses.ApplicationClass;

public class ContinuationEnhancer extends Enhancer {

    static final List<String> continuationMethods = new ArrayList<String>();

    static {
        continuationMethods.add("play.mvc.Controller.await(java.lang.String)");
        continuationMethods.add("play.mvc.Controller.await(int)");
        continuationMethods.add("play.mvc.Controller.await(java.util.concurrent.Future)");
        continuationMethods.add("play.mvc.WebSocketController.await(java.lang.String)");
        continuationMethods.add("play.mvc.WebSocketController.await(int)");
        continuationMethods.add("play.mvc.WebSocketController.await(java.util.concurrent.Future)");
    }

    public static boolean isEnhanced(String appClassName) {
        ApplicationClass appClass = Play.classes.getApplicationClass( appClassName);
        if ( appClass == null) {
            return false;
        }

        // All classes enhanced for Continuations are implementing the interface EnhancedForContinuations
        return EnhancedForContinuations.class.isAssignableFrom( appClass.javaClass );
    }

    @Override
    public void enhanceThisClass(ApplicationClass applicationClass) throws Exception {
        if (isScala(applicationClass)) {
            return;
        }
//
        if (!applicationClass.name.startsWith("controllers.")) {
        	return;
        }
//        CtClass ctClass = makeClass(applicationClass);
//
//        if (!ctClass.subtypeOf(classPool.get(ControllersEnhancer.ControllerSupport.class.getName()))) {
//            return ;
//        }
//
//
        boolean needsContinuations = shouldEnhance( applicationClass );

        if (!needsContinuations) {
            return;
        }


        Set<String> set = new HashSet<>();
        set.add("play/classloading/enhancers/EnhancedForContinuations");
        byte[] bytes = InterfaceAdder.visit(applicationClass.enhancedByteCode, set);
        // Apply continuations
        applicationClass.enhancedByteCode = new AsmClassTransformer().transform( bytes);

//        ctClass.defrost();
//        enhancedForContinuationsInterface.defrost();
    }

    private boolean shouldEnhanceJavassist(CtClass ctClass) throws Exception {

        if (ctClass == null || ctClass.getPackageName().startsWith("play.")) {
            // If we have not found any await-usage yet, we return false..
            return false;
        }

        boolean needsContinuations = false;
        final boolean[] _needsContinuations = new boolean[]{false};

        for (CtMethod m : ctClass.getDeclaredMethods()) {
            m.instrument(new ExprEditor() {

                @Override
                public void edit(MethodCall m) throws CannotCompileException {
                    try {
                        if (continuationMethods.contains(m.getMethod().getLongName())) {
                            _needsContinuations[0] = true;
                        }
                    } catch (Exception e) {
                    }
                }
            });

            if (_needsContinuations[0]) {
                break;
            }
        }

        if (!_needsContinuations[0]) {
            // Check parent class
            _needsContinuations[0] = shouldEnhanceJavassist( ctClass.getSuperclass());
        }

        return _needsContinuations[0];

    }

    private boolean shouldEnhance(ApplicationClass ctClass) throws Exception {
    	
    	if (ctClass == null || ctClass.name.startsWith("play.")) {
    		// If we have not found any await-usage yet, we return false..
    		return false;
    	}
    	
    	boolean needsContinuations = false;
    	
    	needsContinuations = ContinuationMethodSearcher.visit(ctClass.enhancedByteCode, continuationMethods);
//    	if (!_needsContinuations[0]) {
//    		// Check parent class
//    		_needsContinuations[0] = shouldEnhance( ctClass.getSuperclass());
//    	}
    	// XXX should consider superclass
    	return needsContinuations;
    	
    }


}
