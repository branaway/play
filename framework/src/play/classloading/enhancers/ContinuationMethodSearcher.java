package play.classloading.enhancers;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import bran.ContMethodVisitor;
import bran.ControllerActionMethodVisitor;
import bran.ControllerClassVisitor;
import bran.MethodDescr;

public class ContinuationMethodSearcher extends ClassVisitor implements Opcodes {
	private List<String> interestingMethods;

	/**
	 * @param api
	 * @param cv
	 */
	public ContinuationMethodSearcher() {
		super(Opcodes.ASM5);
	}

	/**
	 * @param interestingMethods
	 */
	public ContinuationMethodSearcher(List<String> interestingMethods) {
		this();
		this.interestingMethods = interestingMethods;
	}

	boolean found = false;
	String superName;
	
	public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
		this.superName = superName.replace('/', '.');
	}
	
	// XXX should go into method code to find out any continuation-related method
	// 
	public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
		MethodVisitor mv = cv.visitMethod(access, name, desc, signature, exceptions);
//
//		found = found ? found: (new ContMethodVisitor(mv, interestingMethods).gotit());
		return mv;
	}

	
	public static boolean visit(byte[] code, List<String> interestingMethods) {
		try {
			ContinuationMethodSearcher searcher = new ContinuationMethodSearcher(interestingMethods);
			new ClassReader(new ByteArrayInputStream(code)).accept(searcher, 0);
			return searcher.found;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	@Override
	public void visitEnd() {
	}

}