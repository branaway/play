/**
 * 
 */
package play.mvc;

import java.util.List;
import java.util.Stack;

import play.mvc.Http.Request;

/**
 * @author bran
 *
 */
public class ControllerInstrumentation {

	public static ThreadLocal<Stack<String>> currentAction;

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
