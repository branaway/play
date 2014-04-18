package bran;

import java.io.ByteArrayInputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * 
 */

/**
 * @author bran
 *
 */
public class ModelClassVisitor extends ClassVisitor implements Opcodes {
	/**
	 * 
	 */
	private static final String JPQL = "play/db/jpa/JPQL";
	/**
	 * 
	 */
	private static final String GET_JPA_CONFIG = "getJPAConfig";
	/**
	 * 
	 */
	private static final String JPA_BASE = "play/db/jpa/JPABase";
	String entityName;
	private boolean isInterface;

	public ModelClassVisitor(ClassWriter cv) {
		super(ASM5, cv);
	}

	/**
	 * 
	 */
	public ModelClassVisitor() {
		this(new ClassWriter(ClassWriter.COMPUTE_MAXS));
	}

	@Override
	public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
		cv.visit(version, access, name, signature, superName, interfaces);
		entityName = name;
		isInterface = (access & ACC_INTERFACE) != 0;
	}

	@Override
	public void visitEnd() {
		if (!isInterface) {
			count();
			count2();
			findAll();
			findById();
			find();
			findQuery();
			allQuery();
			delete();
			deleteAll();
			findOneBy();
			create();
		}
		cv.visitEnd();
	}

	private void count() {
		String name = "count";
		MethodVisitor mv = cv.visitMethod(ACC_PUBLIC + ACC_STATIC, name, "()J", null, null);
		mv.visitCode();
		getJPQL(mv);
		mv.visitLdcInsn(en());
		// mv.visitLdcInsn(entityName);
		mv.visitMethodInsn(INVOKEVIRTUAL, JPQL, name, "(Ljava/lang/String;)J", false);
		mv.visitInsn(LRETURN);
		mv.visitMaxs(2, 0);
		mv.visitEnd();
	}

	private String en() {
		return entityName.substring(entityName.lastIndexOf('/') + 1);
	}

	private Type t() {
		return Type.getType("L" + entityName + ";");
	}

	private void count2() {
		String name = "count";
		MethodVisitor mv = cv.visitMethod(ACC_PUBLIC + ACC_STATIC, name, "(Ljava/lang/String;[Ljava/lang/Object;)J",
				null, null);
		mv.visitCode();
		getJPQL(mv);
		mv.visitLdcInsn(en());
		mv.visitVarInsn(ALOAD, 0);
		mv.visitVarInsn(ALOAD, 1);
		mv.visitMethodInsn(INVOKEVIRTUAL, JPQL, name,
				"(Ljava/lang/String;Ljava/lang/String;[Ljava/lang/Object;)J", false);
		mv.visitInsn(LRETURN);
		mv.visitMaxs(4, 2);
		mv.visitEnd();
	}

	private void findAll() {
		String name = "findAll";
		MethodVisitor mv = cv.visitMethod(ACC_PUBLIC + ACC_STATIC, name, "()Ljava/util/List;", null, null);
		mv.visitCode();
		getJPQL(mv);
		mv.visitLdcInsn(en());
		mv.visitMethodInsn(INVOKEVIRTUAL, JPQL, name, "(Ljava/lang/String;)Ljava/util/List;", false);
		mv.visitInsn(ARETURN);
		mv.visitMaxs(2, 0);
		mv.visitEnd();
	}

	private void findQuery() {
		String name = "find";
		MethodVisitor mv = cv.visitMethod(ACC_PUBLIC + ACC_STATIC, name, "()Lplay/db/jpa/GenericModel$JPAQuery;",
				null, null);
		mv.visitCode();
		getJPQL(mv);
		mv.visitLdcInsn(en());
		mv.visitMethodInsn(INVOKEVIRTUAL, JPQL, name,
				"(Ljava/lang/String;)Lplay/db/jpa/GenericModel$JPAQuery;", false);
		mv.visitInsn(ARETURN);
		mv.visitMaxs(2, 0);
		mv.visitEnd();
	}

	private void findById() {
		String name = "findById";
		MethodVisitor mv = cv.visitMethod(ACC_PUBLIC + ACC_STATIC, name,
				"(Ljava/lang/Object;)Lplay/db/jpa/GenericModel;", null, null);
		mv.visitCode();
		getJPQL(mv);
		mv.visitLdcInsn(t());
		mv.visitVarInsn(ALOAD, 0);
		mv.visitMethodInsn(INVOKEVIRTUAL, JPQL, name,
				"(Ljava/lang/Class;Ljava/lang/Object;)Lplay/db/jpa/GenericModel;", false);
		mv.visitInsn(ARETURN);
		mv.visitMaxs(3, 1);
		mv.visitEnd();
	}

	private void allQuery() {
		String name = "all";
		MethodVisitor mv = cv.visitMethod(ACC_PUBLIC + ACC_STATIC, name, "()Lplay/db/jpa/GenericModel$JPAQuery;",
				null, null);
		mv.visitCode();
		getJPQL(mv);
		mv.visitLdcInsn(en());
		mv.visitMethodInsn(INVOKEVIRTUAL, JPQL, name,
				"(Ljava/lang/String;)Lplay/db/jpa/GenericModel$JPAQuery;", false);
		mv.visitInsn(ARETURN);
		mv.visitMaxs(2, 0);
		mv.visitEnd();
	}

	private void find() {
		String name = "find";
		MethodVisitor mv = cv.visitMethod(ACC_PUBLIC + ACC_STATIC, name,
				"(Ljava/lang/String;[Ljava/lang/Object;)Lplay/db/jpa/GenericModel$JPAQuery;", null, null);
		mv.visitCode();
		getJPQL(mv);
		mv.visitLdcInsn(en());
		mv.visitVarInsn(ALOAD, 0);
		mv.visitVarInsn(ALOAD, 1);
		mv.visitMethodInsn(INVOKEVIRTUAL, JPQL, name,
				"(Ljava/lang/String;Ljava/lang/String;[Ljava/lang/Object;)Lplay/db/jpa/GenericModel$JPAQuery;", false);
		mv.visitInsn(ARETURN);
		mv.visitMaxs(4, 2);
		mv.visitEnd();
	}

	private void delete() {
		String name = "delete";
		MethodVisitor mv = cv.visitMethod(ACC_PUBLIC + ACC_STATIC, name,
				"(Ljava/lang/String;[Ljava/lang/Object;)I", null, null);
		mv.visitCode();
		getJPQL(mv);
		mv.visitLdcInsn(en());
		mv.visitVarInsn(ALOAD, 0);
		mv.visitVarInsn(ALOAD, 1);
		mv.visitMethodInsn(INVOKEVIRTUAL, JPQL, name,
				"(Ljava/lang/String;Ljava/lang/String;[Ljava/lang/Object;)I", false);
		mv.visitInsn(IRETURN);
		mv.visitMaxs(4, 2);
		mv.visitEnd();
	}

	private void deleteAll() {
		String name = "deleteAll";
		MethodVisitor mv = cv.visitMethod(ACC_PUBLIC + ACC_STATIC, name, "()I", null, null);
		mv.visitCode();
		getJPQL(mv);
		mv.visitLdcInsn(en());
		mv.visitMethodInsn(INVOKEVIRTUAL, JPQL, name, "(Ljava/lang/String;)I", false);
		mv.visitInsn(IRETURN);
		mv.visitMaxs(2, 0);
		mv.visitEnd();
	}

	private void findOneBy() {
		String name = "findOneBy";
		MethodVisitor mv = cv.visitMethod(ACC_PUBLIC + ACC_STATIC, name,
				"(Ljava/lang/String;[Ljava/lang/Object;)Lplay/db/jpa/GenericModel;", null, null);
		mv.visitCode();
		getJPQL(mv);
		mv.visitLdcInsn(en());
		mv.visitVarInsn(ALOAD, 0);
		mv.visitVarInsn(ALOAD, 1);
		mv.visitMethodInsn(INVOKEVIRTUAL, JPQL, name,
				"(Ljava/lang/String;Ljava/lang/String;[Ljava/lang/Object;)Lplay/db/jpa/GenericModel;", false);
		mv.visitInsn(ARETURN);
		mv.visitMaxs(4, 2);
		mv.visitEnd();
	}

	private void getJPQL(MethodVisitor mv) {
		mv.visitLdcInsn(t());
		mv.visitMethodInsn(INVOKESTATIC, JPA_BASE, GET_JPA_CONFIG, "(Ljava/lang/Class;)Lplay/db/jpa/JPAConfig;",
				false);
		mv.visitFieldInsn(GETFIELD, "play/db/jpa/JPAConfig", "jpql", "Lplay/db/jpa/JPQL;");
	}

	private void create() {
		MethodVisitor mv = cv.visitMethod(ACC_PUBLIC + ACC_STATIC, "create",
				"(Ljava/lang/String;Lplay/mvc/Scope$Params;)Lplay/db/jpa/JPABase;", null,
				new String[] { "java/lang/Exception" });
		mv.visitCode();
		getJPQL(mv);
		mv.visitLdcInsn(t());
		mv.visitVarInsn(ALOAD, 0);
		mv.visitVarInsn(ALOAD, 1);
		mv.visitMethodInsn(INVOKEVIRTUAL, JPQL, "create",
				"(Ljava/lang/Class;Ljava/lang/String;Lplay/mvc/Scope$Params;)Lplay/db/jpa/GenericModel;", false);
		mv.visitInsn(ARETURN);
		mv.visitMaxs(4, 2);
		mv.visitEnd();
	}

	public static byte[] visitModel(byte[] code) {
		try {
			ModelClassVisitor modelClassVisitor = new ModelClassVisitor();
			new ClassReader(new ByteArrayInputStream(code)).accept(modelClassVisitor, 0);
			return modelClassVisitor.output();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	// static ControllerClassVisitor controllerClassVisitor = new
	// ControllerClassVisitor();

	public byte[] output() {
		return ((ClassWriter) super.cv).toByteArray();
	}

}
