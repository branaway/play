package bran;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.util.CheckClassAdapter;

/**
 * 
 */

/**
 * @author bran
 *
 */
public class ControllerClassVisitor extends ClassVisitor implements Opcodes {
	String controllerName;
	private boolean isInterface;

	public ControllerClassVisitor(ClassWriter cv) {
		super(ASM5, cv);
	}

	/**
	 * 
	 */
	public ControllerClassVisitor() {
		this(new ClassWriter(ClassWriter.COMPUTE_MAXS));
	}

	@Override
	public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
		cv.visit(version, access, name, signature, superName, interfaces);
		controllerName = name;
		isInterface = (access & ACC_INTERFACE) != 0;
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
		MethodVisitor mv = cv.visitMethod(access, name, desc, signature, exceptions);

		MethodDescr md = new MethodDescr(access, name, desc, signature, exceptions);
		if (!isInterface && mv != null && !name.equals("<init>") && !name.contains("$")) {
			mv = new ControllerActionMethodVisitor(controllerName, md, mv);
		}
		return mv;
	}

	@Override
	public void visitEnd() {
		cv.visitEnd();
	}

	public static byte[] visitController(byte[] code) {
		try {
			ControllerClassVisitor controllerClassVisitor = new ControllerClassVisitor();
			new ClassReader(new ByteArrayInputStream(code)).accept(controllerClassVisitor, 0);
			return controllerClassVisitor.output();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	// static ControllerClassVisitor controllerClassVisitor = new
	// ControllerClassVisitor();

	public byte[] output() {
		byte[] byteArray = ((ClassWriter) super.cv).toByteArray();
		verifyit(byteArray);
		return byteArray;
	}

	/**
	 * @author Bing Ran (bing.ran@gmail.com)
	 * @param byteArray
	 */
	private void verifyit(byte[] byteArray) {
//		not really working...
//		ClassReader cr;
//		try {
//			cr = new ClassReader(new ByteArrayInputStream(byteArray));
//			CheckClassAdapter.verify(cr, false, new PrintWriter(System.out));
//		} catch (IOException e) {
//			e.printStackTrace();
//		}

	}

}
