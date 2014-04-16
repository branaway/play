package bran;
import java.io.ByteArrayInputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

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
		if (!isInterface) {
			FieldVisitor fv = cv.visitField(ACC_PUBLIC + ACC_STATIC, "timer", "J", null, null);
			if (fv != null) {
				fv.visitEnd();
			}
		}
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
	
//	static ControllerClassVisitor controllerClassVisitor = new ControllerClassVisitor();
	
	public byte[] output() {
		return ((ClassWriter)super.cv).toByteArray();
	}
}
