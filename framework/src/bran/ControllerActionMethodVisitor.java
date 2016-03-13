package bran;

import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javassist.CtField;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * 
 */

public class ControllerActionMethodVisitor extends MethodVisitor implements Opcodes {
	/**
	 * 
	 */
	private final String owner;
	private MethodDescr methodDesc;

	public ControllerActionMethodVisitor(String owner, MethodDescr md, MethodVisitor mv) {
		super(ControllerClassVisitor.ASM5, mv);
		this.owner = owner;
		this.methodDesc = md;
	}

	@Override
	public void visitCode() {
		redirect();
	}

	/**
	 * replace special field access to xxx.current() call
	 */
	@Override
	public void visitFieldInsn(final int opcode, final String owner, final String name, final String desc) {
		if (opcode == GETSTATIC) {
			if (isSpecialFieldAccess(owner, name)) {
				String type = desc.substring(1, desc.length() - 1); 
				// remove L and ;
				String gDesc = "()" + desc;
				mv.visitMethodInsn(INVOKESTATIC, type, "current", gDesc, false);
				return;
			}
		}
		// else
		super.visitFieldInsn(opcode, owner, name, desc);
	}

//	private void alert() {
//		mv.visitCode();
//		mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
//		mv.visitLdcInsn("method called: " + owner + "." + methodDesc.name);
//		mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
//	}

	boolean isSpecialFieldAccess(String owner, String fname) {
		if (owner.equals(this.owner) || owner.equals("play.mvc.WebSocketController")) {
			return fname.equals("params") || fname.equals("request") || fname.equals("response")
					|| fname.equals("session") || fname.equals("params") || fname.equals("renderArgs")
					|| fname.equals("routeArgs") || fname.equals("validation") || fname.equals("inbound")
					|| fname.equals("outbound") || fname.equals("flash");
		}
		return false;
	}

	private void redirect() {
		mv.visitCode();
		mv.visitMethodInsn(INVOKESTATIC, "play/classloading/enhancers/ControllersEnhancer$ControllerInstrumentation",
				"isActionCallAllowed", "()Z", false);
		Label l0 = new Label();
		mv.visitJumpInsn(IFNE, l0);
		useParamToCallAnother(this.methodDesc.paramTypes);
		Label l1 = new Label();
		mv.visitJumpInsn(GOTO, l1);
		mv.visitLabel(l0);
		mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
		mv.visitMethodInsn(INVOKESTATIC, "play/classloading/enhancers/ControllersEnhancer$ControllerInstrumentation",
				"stopActionCall", "()V", false);
		mv.visitLabel(l1);
		mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
	}

	private void useParamToCallAnother(List<Type> pts) {
		mv.visitLdcInsn(owner.replace('/', '.') + "." + this.methodDesc.name);
		// parameter array
		int arrayLength = pts.size();
		mv.visitIntInsn(BIPUSH, arrayLength);
		// mv.visitInsn(ICONST_0);
		mv.visitTypeInsn(ANEWARRAY, "java/lang/Object");

		int currentSlot = 0;
		for (int i = 0; i < pts.size(); i++) {
			mv.visitInsn(DUP);
			mv.visitIntInsn(BIPUSH, i);

			Type t = pts.get(i);
			// autoboxing
			if (t == int.class) {
				mv.visitVarInsn(ILOAD, currentSlot);
				mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
			} else if (t == boolean.class) {
				mv.visitVarInsn(ILOAD, currentSlot);
				mv.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
			} else if (t == short.class) {
				mv.visitVarInsn(ILOAD, currentSlot);
				mv.visitMethodInsn(INVOKESTATIC, "java/lang/Short", "valueOf", "(S)Ljava/lang/Short;", false);
			} else if (t == char.class) {
				mv.visitVarInsn(ILOAD, currentSlot);
				mv.visitMethodInsn(INVOKESTATIC, "java/lang/Character", "valueOf", "(C)Ljava/lang/Character;", false);
			} else if (t == long.class) {
				mv.visitVarInsn(LLOAD, currentSlot);
				mv.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
			} else if (t == float.class) {
				mv.visitVarInsn(FLOAD, currentSlot);
				mv.visitMethodInsn(INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false);
			} else if (t == double.class) {
				mv.visitVarInsn(DLOAD, currentSlot);
				mv.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
			} else
				mv.visitVarInsn(ALOAD, currentSlot);

			mv.visitInsn(AASTORE);

			currentSlot++;
			if (t == long.class || t == double.class) {
				currentSlot++;
			}

		}
		mv.visitMethodInsn(INVOKESTATIC, "play/mvc/Controller", "redirect", "(Ljava/lang/String;[Ljava/lang/Object;)V",
				false);
		// mv.visitInsn(RETURN);
	}

	@Override
	public void visitInsn(int opcode) {
		// if ((opcode >= ControllerClassVisitor.IRETURN && opcode <=
		// ControllerClassVisitor.RETURN) || opcode ==
		// ControllerClassVisitor.ATHROW) {
		// mv.visitFieldInsn(ControllerClassVisitor.GETSTATIC, owner, "timer",
		// "J");
		// mv.visitMethodInsn(ControllerClassVisitor.INVOKESTATIC,
		// "java/lang/System", "currentTimeMillis", "()J");
		// mv.visitInsn(ControllerClassVisitor.LADD);
		// mv.visitFieldInsn(ControllerClassVisitor.PUTSTATIC, owner, "timer",
		// "J");
		// }
		mv.visitInsn(opcode);
	}

	@Override
	public void visitMaxs(int maxStack, int maxLocals) {
		mv.visitMaxs(0, 0); // should be ignored
	}

	/* (non-Javadoc)
	 * @see org.objectweb.asm.MethodVisitor#visitTryCatchBlock(org.objectweb.asm.Label, org.objectweb.asm.Label, org.objectweb.asm.Label, java.lang.String)
	 */
	@Override
	public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
		handlers.add(handler);
		super.visitTryCatchBlock(start, end, handler, type);
	}
	
	Set<Label> handlers = new HashSet<>();

	/* (non-Javadoc)
	 * @see org.objectweb.asm.MethodVisitor#visitLabel(org.objectweb.asm.Label)
	 */
	@Override
	public void visitLabel(Label label) {
		super.visitLabel(label);
		if (handlers.contains(label)) {
			// let inject something
//			System.out.println("caught you");
		}
	}

	/* (non-Javadoc)
	 * @see org.objectweb.asm.MethodVisitor#visitFrame(int, int, java.lang.Object[], int, java.lang.Object[])
	 */
	@Override
	public void visitFrame(int type, int nLocal, Object[] local, int nStack, Object[] stack) {
		super.visitFrame(type, nLocal, local, nStack, stack);
	}

	/* (non-Javadoc)
	 * @see org.objectweb.asm.MethodVisitor#visitInvokeDynamicInsn(java.lang.String, java.lang.String, org.objectweb.asm.Handle, java.lang.Object[])
	 */
	@Override
	public void visitInvokeDynamicInsn(String name, String desc, Handle bsm, Object... bsmArgs) {
		super.visitInvokeDynamicInsn(name, desc, bsm, bsmArgs);
	}
	
	
	
}