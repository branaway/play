/**
 * 
 */
package bran.model.dependency;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypePath;

class MethodDependencyVisitor extends MethodVisitor {

	/**
	 * 
	 */
	private final DependencyVisitor dependencyVisitor;

	public MethodDependencyVisitor(DependencyVisitor dependencyVisitor) {
		super(Opcodes.ASM5);
		this.dependencyVisitor = dependencyVisitor;
	}

	@Override
	public AnnotationVisitor visitAnnotationDefault() {
		return new AnnotationDependencyVisitor(this.dependencyVisitor);
	}

	@Override
	public AnnotationVisitor visitAnnotation(final String desc, final boolean visible) {
		this.dependencyVisitor.addDesc(desc);
		return new AnnotationDependencyVisitor(this.dependencyVisitor);
	}

	@Override
	public AnnotationVisitor visitTypeAnnotation(final int typeRef, final TypePath typePath, final String desc,
			final boolean visible) {
		this.dependencyVisitor.addDesc(desc);
		return new AnnotationDependencyVisitor(this.dependencyVisitor);
	}

	@Override
	public AnnotationVisitor visitParameterAnnotation(final int parameter, final String desc, final boolean visible) {
		this.dependencyVisitor.addDesc(desc);
		return new AnnotationDependencyVisitor(this.dependencyVisitor);
	}

	@Override
	public void visitTypeInsn(final int opcode, final String type) {
		this.dependencyVisitor.addType(Type.getObjectType(type));
	}

	@Override
	public void visitFieldInsn(final int opcode, final String owner, final String name, final String desc) {
		this.dependencyVisitor.addInternalName(owner);
		this.dependencyVisitor.addDesc(desc);
	}

	@Override
	public void visitMethodInsn(final int opcode, final String owner, final String name, final String desc,
			final boolean itf) {
		this.dependencyVisitor.addInternalName(owner);
		this.dependencyVisitor.addMethodDesc(desc);
	}

	@Override
	public void visitInvokeDynamicInsn(String name, String desc, Handle bsm, Object... bsmArgs) {
		this.dependencyVisitor.addMethodDesc(desc);
		this.dependencyVisitor.addConstant(bsm);
		for (int i = 0; i < bsmArgs.length; i++) {
			this.dependencyVisitor.addConstant(bsmArgs[i]);
		}
	}

	@Override
	public void visitLdcInsn(final Object cst) {
		this.dependencyVisitor.addConstant(cst);
	}

	@Override
	public void visitMultiANewArrayInsn(final String desc, final int dims) {
		this.dependencyVisitor.addDesc(desc);
	}

	@Override
	public AnnotationVisitor visitInsnAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
		this.dependencyVisitor.addDesc(desc);
		return new AnnotationDependencyVisitor(this.dependencyVisitor);
	}

	@Override
	public void visitLocalVariable(final String name, final String desc, final String signature, final Label start,
			final Label end, final int index) {
		this.dependencyVisitor.addTypeSignature(signature);
	}

	@Override
	public AnnotationVisitor visitLocalVariableAnnotation(int typeRef, TypePath typePath, Label[] start,
			Label[] end, int[] index, String desc, boolean visible) {
		this.dependencyVisitor.addDesc(desc);
		return new AnnotationDependencyVisitor(this.dependencyVisitor);
	}

	@Override
	public void visitTryCatchBlock(final Label start, final Label end, final Label handler, final String type) {
		if (type != null) {
			this.dependencyVisitor.addInternalName(type);
		}
	}

	@Override
	public AnnotationVisitor visitTryCatchAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
		this.dependencyVisitor.addDesc(desc);
		return new AnnotationDependencyVisitor(this.dependencyVisitor);
	}
}