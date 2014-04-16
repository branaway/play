/**
 * 
 */
package bran;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author bran
 *
 */
public class MethodDescr {
	public int access;
	public String name;
	public String desc; // e.g.: (Ljava/lang/Long;)V
	public String signature;
	public String[] exceptions;
	public List<Type> paramTypes = new ArrayList<>();
	public List<Integer> paramSlots = new ArrayList<>();
	

	public MethodDescr(int access, String name, String desc, String signature, String[] exceptions) {
		super();
		this.access = access;
		this.name = name;
		this.desc = desc;
		this.signature = signature;
		this.exceptions = exceptions;
		parseDescriptor();
	}

	private void parseDescriptor() {
		paramTypes = splitMethodDesc(desc).stream().map(mapper).collect(Collectors.toList());
		paramSlots = paramTypes.stream().map(t -> (t != double.class && t != long.class)? 1 : 2).collect(Collectors.toList());
	}

	static Function<String, Type> mapper = c -> {
		if (c == "I")
			return (int.class);
		else if (c == "Z")
			return (boolean.class);
		else if (c == "B")
			return (byte.class);
		else if (c == "C")
			return (char.class);
		else if (c == "S")
			return (short.class);
		else if (c == "D")
			return (double.class);
		else if (c == "F")
			return (float.class);
		else if (c == "S")
			return (short.class);
		else if (c == "J")
			return (long.class);
		else if (c.startsWith("L"))
			return Object.class;
		else if (c.startsWith("["))
			return Object.class;
		else
			throw new RuntimeException("bad decriptor type: " + c);
		
	};

	public static List<String> splitMethodDesc(String desc) {
		// \[*L[^;]+;|\[[ZBCSIFDJ]|[ZBCSIFDJ]
		int beginIndex = desc.indexOf('(');
		int endIndex = desc.lastIndexOf(')');
		if ((beginIndex == -1 && endIndex != -1) || (beginIndex != -1 && endIndex == -1)) {
			System.err.println(beginIndex);
			System.err.println(endIndex);
			throw new RuntimeException();
		}
		String x0;
		if (beginIndex == -1 && endIndex == -1) {
			x0 = desc;
		} else {
			x0 = desc.substring(beginIndex + 1, endIndex);
		}
		Pattern pattern = Pattern.compile("\\[*L[^;]+;|\\[[ZBCSIFDJ]|[ZBCSIFDJ]");
		Matcher matcher = pattern.matcher(x0);

		ArrayList<String> listMatches = new ArrayList<String>();

		while (matcher.find()) {
			listMatches.add(matcher.group());
		}

		return listMatches;
		// for(String s : listMatches)
		// {
		// System.out.print(s + " ");
		// }
		// System.out.println();
	}
	
}
