/**
 * 
 */
package play.classloading.enhancers;

import java.util.Stack;

/**
 * @author bran
 *
 */
public class ControllersEnhancer {
	 static public class ControllerInstrumentation {

	 	public static ThreadLocal<Stack<String>> currentAction = new ThreadLocal<Stack<String>>();

	 	/**
	 	 * @author Bing Ran (bing.ran@gmail.com)
	 	 */
	 	public static void stopActionCall() {
	 		// TODO Auto-generated method stub
	 		
	 	}

	 	/**
	 	 * @author Bing Ran (bing.ran@gmail.com)
	 	 */
	 	public static void initActionCall() {
	 		// TODO Auto-generated method stub
	 		
	 	}

	 }

}
