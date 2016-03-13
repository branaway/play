package bran.model.dependency;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypePath;
import org.objectweb.asm.signature.SignatureReader;

/**
 * DependencyVisitor
 * 
 * was tracking package reference. now tracks classes usage
 * 
 * @author Eugene Kuleshov
 * @author Bing Ran
 */
public class DependencyVisitor extends ClassVisitor {
	// Set<String> classesRefed = new HashSet<String>();

	// <classname, Map<ref class name, ref numbers>>
	Map<String, Set<String>> classRefs = new HashMap<String, Set<String>>();

	Set<String> refsForCurrentClass; // track ref num

	private String[] included;

	private String currentClass;

	public Map<String, Set<String>> getClassRefs() {
		return classRefs;
	}

	// public Set<String> getClassesRefed() {
	// return classesRefed;
	// }

	public DependencyVisitor(String... included) {
		super(Opcodes.ASM5);
		this.included = included;
	}

	// ClassVisitor

	/**
	 * @param classReader
	 * @param included2
	 */
	public DependencyVisitor(ClassReader classReader, String[] included2) {
		this(included2);
		classReader.accept(this, 0);
	}

	@Override
	public void visit(final int version, final int access, final String name, final String signature,
			final String superName, final String[] interfaces) {
		// classes.add(name);
		refsForCurrentClass = classRefs.getOrDefault(name, new HashSet<>());
		classRefs.put(name, refsForCurrentClass); // in case new
		this.currentClass = name;
		
		if (signature == null) {
			if (superName != null) {
				addInternalName(superName);
			}
			addInternalNames(interfaces);
		} else {
			addSignature(signature);
		}
	}

	@Override
	public AnnotationVisitor visitAnnotation(final String desc, final boolean visible) {
		addDesc(desc);
		return new AnnotationDependencyVisitor(this);
	}

	@Override
	public AnnotationVisitor visitTypeAnnotation(final int typeRef, final TypePath typePath, final String desc,
			final boolean visible) {
		addDesc(desc);
		return new AnnotationDependencyVisitor(this);
	}

	@Override
	public FieldVisitor visitField(final int access, final String name, final String desc, final String signature,
			final Object value) {
		if (signature == null) {
			addDesc(desc);
		} else {
			addTypeSignature(signature);
		}
		if (value instanceof Type) {
			addType((Type) value);
		}
		return new FieldDependencyVisitor(this);
	}

	@Override
	public MethodVisitor visitMethod(final int access, final String name, final String desc, final String signature,
			final String[] exceptions) {
		if (signature == null) {
			addMethodDesc(desc);
		} else {
			addSignature(signature);
		}
		addInternalNames(exceptions);
		return new MethodDependencyVisitor(this);
	}

	// ---------------------------------------------

	private void addRef(final String name) {
		if (name == null || name.equals(currentClass))
			return;
		
		if (Stream.of(included).anyMatch(inc -> "*".equals(inc) || name.startsWith(inc))) {
			refsForCurrentClass.add(name.replace('/', '.'));
		}
	}

	void addInternalName(final String name) {
		addType(Type.getObjectType(name));
	}

	private void addInternalNames(final String[] names) {
		for (int i = 0; names != null && i < names.length; i++) {
			addInternalName(names[i]);
		}
	}

	void addDesc(final String desc) {
		addType(Type.getType(desc));
	}

	void addMethodDesc(final String desc) {
		addType(Type.getReturnType(desc));
		Type[] types = Type.getArgumentTypes(desc);
		for (int i = 0; i < types.length; i++) {
			addType(types[i]);
		}
	}

	void addType(final Type t) {
		switch (t.getSort()) {
		case Type.ARRAY:
			addType(t.getElementType());
			break;
		case Type.OBJECT:
			addRef(t.getInternalName());
			break;
		case Type.METHOD:
			addMethodDesc(t.getDescriptor());
			break;
		}
	}

	private void addSignature(final String signature) {
		if (signature != null) {
			new SignatureReader(signature).accept(new SignatureDependencyVisitor(this));
		}
	}

	void addTypeSignature(final String signature) {
		if (signature != null) {
			new SignatureReader(signature).acceptType(new SignatureDependencyVisitor(this));
		}
	}

	void addConstant(final Object cst) {
		if (cst instanceof Type) {
			addType((Type) cst);
		} else if (cst instanceof Handle) {
			Handle h = (Handle) cst;
			addInternalName(h.getOwner());
			addMethodDesc(h.getDesc());
		}
	}

	/**
	 * @author Bing Ran (bing.ran@gmail.com)
	 * @return
	 */
	public Set<String> getClassesRefed() {
		return classRefs.values().stream().reduce(new HashSet<>(), (a, b) -> {
			a.addAll(b);
			return a;
		});
	}

	/**
	 * @author Bing Ran (bing.ran@gmail.com)
	 * @param string
	 * @param string2
	 * @return
	 */
	public static Set<String> getDependencies(byte[] bytes, String... included) {
		return new DependencyVisitor(new ClassReader(bytes), included).getClassesRefed();
	}
	
}
