/**
 * 
 */
package bran;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.ASMifier;
import org.objectweb.asm.util.TraceClassVisitor;

import play.classloading.enhancers.ControllersEnhancer.ControllerInstrumentation;
import play.mvc.Controller;

/**
 * @author bran
 *
 */
public class AsmTests {

	@Test
	public void testAsmifier() throws IOException {
		ClassReader cr = new ClassReader(AsmTests.class.getName());
		cr.accept(new TraceClassVisitor(null, new ASMifier(), new PrintWriter(System.out)), ClassReader.SKIP_DEBUG);
	}

	// bran: used to be inserted to the beginning of an action call
	public static void beforeMethod(Method m, Object... args) {
		if (!ControllerInstrumentation.isActionCallAllowed()) {
			Controller.redirect(m.getDeclaringClass().getName() + "." + m.getName(), args);
		} else {
			ControllerInstrumentation.stopActionCall();
		}
	}

	public static void so(String a, long b, boolean c, double d, String ee) {
		beforeMethod(null, a, b, c, d, ee);
	}
	
}
