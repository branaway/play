/**
 * 
 */
package bran;

/**
 * @author bran
 *
 */
public class MethodDescr {
	public int access; 
	public String name;
	public String desc;
	public String signature;
	public String[] exceptions;
	public MethodDescr(int access, String name, String desc, String signature, String[] exceptions) {
		super();
		this.access = access;
		this.name = name;
		this.desc = desc;
		this.signature = signature;
		this.exceptions = exceptions;
	}
	
	
}
