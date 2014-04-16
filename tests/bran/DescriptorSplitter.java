/**
 * 
 */
package bran;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author bran
 *
 */
public class DescriptorSplitter {
	public static void main(String[] args) {		
//simple test cases.
//		splitMethodDesc("(IJB)");
		List<String> l = 
				splitMethodDesc("([IFB[[[[[Ljava/lang/String;Ljava/lang/String;[I[S[BBLjava/lang/BLtring;)");
		Consumer<String> action = a -> System.out.print(a + " ");
		l.forEach(action);
		System.out.println();
		l = splitMethodDesc("Ljava/lang/String;BBBBLjava/lang/String;");
		l.forEach(action);
		System.out.println();
		l = splitMethodDesc("(IJB)D");
		l.forEach(action);
		System.out.println();
		l = splitMethodDesc("ZBCSIFDJ[Z[B[C[S[I[F[D[JLZBCSIFDJ;LZBCSIFDJ;[LZBCSIFDJ;LZBCSIFDJ;[LZBCSIFDJ;");
		l.forEach(action);
		System.out.println();
	}
	
	public static List<String> splitMethodDesc(String desc) {
		//\[*L[^;]+;|\[[ZBCSIFDJ]|[ZBCSIFDJ]
		int beginIndex = desc.indexOf('(');
		int endIndex = desc.lastIndexOf(')');
		if((beginIndex == -1 && endIndex != -1) || (beginIndex != -1 && endIndex == -1)) {
			System.err.println(beginIndex);
			System.err.println(endIndex);
			throw new RuntimeException();
		}
		String x0;
		if(beginIndex == -1 && endIndex == -1) {
			x0 = desc;
		}
		else {
			x0 = desc.substring(beginIndex + 1, endIndex);
		}
		Pattern pattern = Pattern.compile("\\[*L[^;]+;|\\[[ZBCSIFDJ]|[ZBCSIFDJ]");
        Matcher matcher = pattern.matcher(x0);
 
        ArrayList<String> listMatches = new ArrayList<String>();
 
        while(matcher.find())
        {
            listMatches.add(matcher.group());
        }
 
        return listMatches;
//        for(String s : listMatches)
//        {
//            System.out.print(s + " ");
//        }
//        System.out.println();
	}
}