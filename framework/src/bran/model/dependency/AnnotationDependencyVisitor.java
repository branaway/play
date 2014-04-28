/**
 * 
 */
package bran.model.dependency;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

class AnnotationDependencyVisitor extends AnnotationVisitor {

	/**
	 * 
	 */
	private final DependencyVisitor dependencyVisitor;

	public AnnotationDependencyVisitor(DependencyVisitor dependencyVisitor) {
		super(Opcodes.ASM5);
		this.dependencyVisitor = dependencyVisitor;
	}

	@Override
	public void visit(final String name, final Object value) {
		if (value instanceof Type) {
			this.dependencyVisitor.addType((Type) value);
		}
	}

	@Override
	public void visitEnum(final String name, final String desc, final String value) {
		this.dependencyVisitor.addDesc(desc);
	}

	@Override
	public AnnotationVisitor visitAnnotation(final String name, final String desc) {
		this.dependencyVisitor.addDesc(desc);
		return this;
	}

	@Override
	public AnnotationVisitor visitArray(final String name) {
		return this;
	}
}