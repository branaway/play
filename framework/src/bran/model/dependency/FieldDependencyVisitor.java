/**
 * 
 */
package bran.model.dependency;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.TypePath;

class FieldDependencyVisitor extends FieldVisitor {

	/**
	 * 
	 */
	private final DependencyVisitor dependencyVisitor;

	public FieldDependencyVisitor(DependencyVisitor dependencyVisitor) {
		super(Opcodes.ASM5);
		this.dependencyVisitor = dependencyVisitor;
	}

	@Override
	public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
		this.dependencyVisitor.addDesc(desc);
		return new AnnotationDependencyVisitor(this.dependencyVisitor);
	}

	@Override
	public AnnotationVisitor visitTypeAnnotation(final int typeRef, final TypePath typePath, final String desc,
			final boolean visible) {
		this.dependencyVisitor.addDesc(desc);
		return new AnnotationDependencyVisitor(this.dependencyVisitor);
	}
}