package bran;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class AsmTestsDump implements Opcodes {

	public static byte[] dump() throws Exception {

		ClassWriter cw = new ClassWriter(0);
		FieldVisitor fv;
		MethodVisitor mv;
		AnnotationVisitor av0;

		cw.visit(52, ACC_PUBLIC + ACC_SUPER, "bran/AsmTests", null, "java/lang/Object", null);

		cw.visitInnerClass("play/classloading/enhancers/ControllersEnhancer$ControllerInstrumentation",
				"play/classloading/enhancers/ControllersEnhancer", "ControllerInstrumentation", ACC_PUBLIC + ACC_STATIC);

		{
			mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
			mv.visitCode();
			mv.visitVarInsn(ALOAD, 0);
			mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
			mv.visitInsn(RETURN);
			mv.visitMaxs(1, 1);
			mv.visitEnd();
		}
		{
			mv = cw.visitMethod(ACC_PUBLIC, "testAsmifier", "()V", null, new String[] { "java/io/IOException" });
			{
				av0 = mv.visitAnnotation("Lorg/junit/Test;", true);
				av0.visitEnd();
			}
			mv.visitCode();
			mv.visitTypeInsn(NEW, "org/objectweb/asm/ClassReader");
			mv.visitInsn(DUP);
			mv.visitLdcInsn(Type.getType("Lbran/AsmTests;"));
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getName", "()Ljava/lang/String;", false);
			mv.visitMethodInsn(INVOKESPECIAL, "org/objectweb/asm/ClassReader", "<init>", "(Ljava/lang/String;)V", false);
			mv.visitVarInsn(ASTORE, 1);
			mv.visitVarInsn(ALOAD, 1);
			mv.visitTypeInsn(NEW, "org/objectweb/asm/util/TraceClassVisitor");
			mv.visitInsn(DUP);
			mv.visitInsn(ACONST_NULL);
			mv.visitTypeInsn(NEW, "org/objectweb/asm/util/ASMifier");
			mv.visitInsn(DUP);
			mv.visitMethodInsn(INVOKESPECIAL, "org/objectweb/asm/util/ASMifier", "<init>", "()V", false);
			mv.visitTypeInsn(NEW, "java/io/PrintWriter");
			mv.visitInsn(DUP);
			mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
			mv.visitMethodInsn(INVOKESPECIAL, "java/io/PrintWriter", "<init>", "(Ljava/io/OutputStream;)V", false);
			mv.visitMethodInsn(INVOKESPECIAL, "org/objectweb/asm/util/TraceClassVisitor", "<init>",
					"(Lorg/objectweb/asm/ClassVisitor;Lorg/objectweb/asm/util/Printer;Ljava/io/PrintWriter;)V", false);
			mv.visitInsn(ICONST_2);
			mv.visitMethodInsn(INVOKEVIRTUAL, "org/objectweb/asm/ClassReader", "accept",
					"(Lorg/objectweb/asm/ClassVisitor;I)V", false);
			mv.visitInsn(RETURN);
			mv.visitMaxs(8, 2);
			mv.visitEnd();
		}
		{
			mv = cw.visitMethod(ACC_PUBLIC + ACC_STATIC, "beforeMethod",
					"(Ljava/lang/reflect/Method;[Ljava/lang/Object;)V", null, null);
			mv.visitCode();
			mv.visitMethodInsn(INVOKESTATIC,
					"play/classloading/enhancers/ControllersEnhancer$ControllerInstrumentation", "isActionCallAllowed",
					"()Z", false);
			Label l0 = new Label();
			mv.visitJumpInsn(IFNE, l0);
			mv.visitTypeInsn(NEW, "java/lang/StringBuilder");
			mv.visitInsn(DUP);
			mv.visitVarInsn(ALOAD, 0);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/reflect/Method", "getDeclaringClass", "()Ljava/lang/Class;",
					false);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getName", "()Ljava/lang/String;", false);
			mv.visitMethodInsn(INVOKESTATIC, "java/lang/String", "valueOf", "(Ljava/lang/Object;)Ljava/lang/String;",
					false);
			mv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "(Ljava/lang/String;)V", false);
			mv.visitLdcInsn(".");
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
					"(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
			mv.visitVarInsn(ALOAD, 0);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/reflect/Method", "getName", "()Ljava/lang/String;", false);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
					"(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
			mv.visitVarInsn(ALOAD, 1);
			mv.visitMethodInsn(INVOKESTATIC, "play/mvc/Controller", "redirect",
					"(Ljava/lang/String;[Ljava/lang/Object;)V", false);
			Label l1 = new Label();
			mv.visitJumpInsn(GOTO, l1);
			mv.visitLabel(l0);
			mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
			mv.visitMethodInsn(INVOKESTATIC,
					"play/classloading/enhancers/ControllersEnhancer$ControllerInstrumentation", "stopActionCall",
					"()V", false);
			mv.visitLabel(l1);
			mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
			mv.visitInsn(RETURN);
			mv.visitMaxs(3, 2);
			mv.visitEnd();
		}
		cw.visitEnd();

		return cw.toByteArray();
	}
}
