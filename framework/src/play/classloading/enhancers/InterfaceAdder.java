package play.classloading.enhancers;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

public class InterfaceAdder extends org.objectweb.asm.ClassVisitor implements Opcodes {
	/**
	 * @param api
	 * @param cv
	 */
	public InterfaceAdder(ClassVisitor cv) {
		super(Opcodes.ASM5, cv);
	}

	private Set<String> newInterfaces;

	public InterfaceAdder(Set<String> newInterfaces) {
		this(new ClassWriter(ClassWriter.COMPUTE_MAXS));
		this.newInterfaces = newInterfaces;
	}

	public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
		Set<String> ints = new HashSet<>(newInterfaces);
		ints.addAll(Arrays.asList(interfaces));
		cv.visit(version, access, name, signature, superName, (String[]) ints.toArray());
	}
	
	public static byte[] visit(byte[] code, Set<String> interfaces) {
		try {
			InterfaceAdder adder = new InterfaceAdder(interfaces);
			new ClassReader(new ByteArrayInputStream(code)).accept(adder, 0);
			return adder.output();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	public byte[] output() {
		byte[] byteArray = ((ClassWriter) super.cv).toByteArray();
		return byteArray;
	}

}