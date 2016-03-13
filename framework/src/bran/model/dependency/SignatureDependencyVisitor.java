/**
 * 
 */
package bran.model.dependency;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.signature.SignatureVisitor;

class SignatureDependencyVisitor extends SignatureVisitor {

	/**
	 * 
	 */
	private final DependencyVisitor dependencyVisitor;
	String signatureClassName;

	public SignatureDependencyVisitor(DependencyVisitor dependencyVisitor) {
		super(Opcodes.ASM5);
		this.dependencyVisitor = dependencyVisitor;
	}

	@Override
	public void visitClassType(final String name) {
		signatureClassName = name;
		this.dependencyVisitor.addInternalName(name);
	}

	@Override
	public void visitInnerClassType(final String name) {
		signatureClassName = signatureClassName + "$" + name;
		this.dependencyVisitor.addInternalName(signatureClassName);
	}
}