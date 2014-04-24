/**
 * 
 */
package bran;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.stream.Stream;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.ASMifier;
import org.objectweb.asm.util.TraceClassVisitor;

import play.Invoker.Suspend;
import play.classloading.enhancers.ControllersEnhancer.ControllerInstrumentation;
import play.db.jpa.GenericModel;
import play.db.jpa.JPABase;
import static play.db.jpa.JPABase.*;
import play.mvc.Controller;
import play.mvc.results.Redirect;
import play.mvc.results.Result;
import play.utils.FastRuntimeException;

/**
 * @author bran
 *
 */
public class AsmTests extends GenericModel {

	@Test
	public void testAsmifier() throws IOException {
		ClassReader cr = new ClassReader(AsmTests.class.getName());
		cr.accept(new TraceClassVisitor(null, new ASMifier(), new PrintWriter(System.out)), ClassReader.SKIP_DEBUG);
	}

	// bran: used to be inserted to the beginning of an action call
	public static void beforeMethod(/*Method m, */String xx, long a, Object b, int in, double dd ) throws Redirect {
		if (!ControllerInstrumentation.isActionCallAllowed()) {
//			Controller.redirect(m.getDeclaringClass().getName() + "." + m.getName(), xx, a, b);
			Controller.redirect("target method", xx, a, b, in, dd);
		} else {
			ControllerInstrumentation.stopActionCall();
		}
	}

	public static void so1(Object...args) {
	}

	public static void so(String a, long b, boolean c, double d, String ee, boolean bool, short sh, char ch) {

		try {
			// beforeMethod(null, "sss", b, c, d, ee, a, b);
			int s = 11;
			so1 (a, b, c, d, ee, bool, sh, ch);
		} catch (FastRuntimeException e) {
			if (e instanceof Result)
				throw e;
			int ss = 100;
		} catch (RuntimeException e) {
			int ss = 99;
		} catch (Throwable e) {
			if (e instanceof Result || e instanceof Suspend)
				throw e;
			int ss = 101;
		}
	}

	public static long count() {
		return getJPAConfig(AsmTests.class).jpql.count("AsmTests");
	}

	public static long count(String query, Object[] params) {
		return getJPAConfig(AsmTests.class).jpql.count("AsmTests", query, params);
	}

	public static java.util.List findAll() {
		return getJPAConfig(AsmTests.class).jpql.findAll("AsmTests");
	}

	public static play.db.jpa.GenericModel findById(Object id) {
		return getJPAConfig(AsmTests.class).jpql.findById(AsmTests.class, id);
	}

	public static play.db.jpa.GenericModel.JPAQuery find(String query, Object[] params) {
		return getJPAConfig(AsmTests.class).jpql.find("AsmTests", query, params);
	}

	public static play.db.jpa.GenericModel.JPAQuery find() {
		return getJPAConfig(AsmTests.class).jpql.find("AsmTests");
	}

	public static play.db.jpa.GenericModel.JPAQuery all() {
		return getJPAConfig(AsmTests.class).jpql.all("AsmTests");
	};

	// //
	public static int delete(String query, Object... params) {
		return getJPAConfig(AsmTests.class).jpql.delete("AsmTests", query, params);
	}

	public static int deleteAll() {
		return getJPAConfig(AsmTests.class).jpql.deleteAll("AsmTests");
	}

	public static GenericModel findOneBy(String query, Object[] params) {
		return getJPAConfig(AsmTests.class).jpql.findOneBy("AsmTests", query, params);
	}

	public static  AsmTests create(String name, play.mvc.Scope.Params params) {
		AsmTests create = getJPAConfig(AsmTests.class).jpql.create(AsmTests.class, name, params);
		return create;
	}
	
	public void testMethRef() {
		Stream.of("1", "2").forEach(AsmTests::findById);
	}
}
