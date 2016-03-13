/**
 * 
 */
package bran;

import static org.junit.Assert.*;

import java.lang.reflect.Method;

import org.junit.Test;

/**
 * @author bran
 *
 */
public class Misc {

	@Test
	public void test1() throws NoSuchMethodException, SecurityException {
		Method declaredMethod = Misc.class.getDeclaredMethod("testMethGeneric", String.class);
		System.out.println(declaredMethod.toGenericString());
	}

	@Deprecated
	public void testMethGeneric(String hello) {
	}
	
	
}
