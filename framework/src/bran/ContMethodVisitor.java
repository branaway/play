package bran;

import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 *  search for any method calls related requiring continuation
 */

public class ContMethodVisitor extends MethodVisitor implements Opcodes {
	/**
	 * 
	 */

	List<String> interestingMethods;
	
	public ContMethodVisitor(MethodVisitor mv, List<String> interestingMethods) {
		super(ControllerClassVisitor.ASM5, mv);
		this.interestingMethods = interestingMethods;
	}

	boolean found = false;

	@Override
	public void visitMethodInsn(int opcode, String owner, String name, String desc) {
		String mname = owner.replace('/',  '.') + "." + name;
		found = found ? found : interestingMethods.stream().anyMatch( 
				a -> a.contains(mname) 
				);
	}
	
	public boolean gotit() {
		return found;
	}
	
}