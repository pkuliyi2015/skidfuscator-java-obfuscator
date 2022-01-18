package dev.skidfuscator.obf.phantom;

import com.google.common.collect.Lists;
import org.mapleir.app.service.ApplicationClassSource;
import org.mapleir.app.service.ClassTree;
import org.mapleir.asm.ClassHelper;
import org.objectweb.asm.ClassWriter;
import org.mapleir.asm.ClassNode;
import org.mapleir.asm.MethodNode;
import org.topdank.byteengineer.commons.data.JarContents;
import org.topdank.byteengineer.commons.data.JarResource;
import org.topdank.byteio.out.JarDumper;
import org.topdank.byteio.util.Debug;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/**
 * Dumps ClassNodes and JarResources back into a file on the local system.
 * Todo: make this extend CompleteJarDumper?
 *
 * @author Bibl
 */
public class PhantomResolvingJarDumper implements JarDumper {

	private final JarContents<?> contents;
	private final ApplicationClassSource source;
	/**
	 * Creates a new JarDumper.
	 *
	 * @param contents Contents of jar.
	 */
	public PhantomResolvingJarDumper(JarContents<ClassNode> contents, ApplicationClassSource source) {
		this.contents = contents;
		this.source = source;
	}

	/**
	 * Dumps the jars contents.
	 *
	 * @param file File to dump it to.
	 */
	@Override
	public void dump(File file) throws IOException {
		if (file.exists()) {
            file.delete();
        }
		file.createNewFile();
		JarOutputStream jos = new JarOutputStream(new FileOutputStream(file));
		int classesDumped = 0;
		int resourcesDumped = 0;
		for (ClassNode cn : contents.getClassContents()) {
			classesDumped += dumpClass(jos, cn.getName(), cn);
		}
		for (JarResource res : contents.getResourceContents()) {
			resourcesDumped += dumpResource(jos, res.getName(), res.getData());
		}
		if(!Debug.debugging) {
            System.out.println("Dumped " + classesDumped + " classes and " + resourcesDumped + " resources to " + file.getAbsolutePath());
        }
		
		jos.close();
	}

	/**
	 * Writes the {@link ClassNode} to the Jar.
	 *
	 * @param out The {@link JarOutputStream}.
	 * @param cn The ClassNode.
	 * @param name The entry name.
	 * @throws IOException If there is a write error.
	 * @return The amount of things dumped, 1 or if you're not dumping it 0.
	 */
	@Override
	public int dumpClass(JarOutputStream out, String name, ClassNode cn) throws IOException {
		JarEntry entry = new JarEntry(cn.getName() + ".class");
		out.putNextEntry(entry);
		ClassTree tree = source.getClassTree();
		

		
		for(MethodNode m : cn.getMethods()) {
			if(m.node.instructions.size() > 10000) {
				System.out.println("large method: " + m + " @" + m.node.instructions.size());
			}
		}

		try {
			try {
				ClassWriter writer = this.buildClassWriter(tree, ClassWriter.COMPUTE_FRAMES);
				cn.node.accept(writer); // must use custom writer which overrides getCommonSuperclass
				out.write(writer.toByteArray());
			} catch (Exception e) {
				ClassWriter writer = this.buildClassWriter(tree, ClassWriter.COMPUTE_MAXS);
				cn.node.accept(writer); // must use custom writer which overrides getCommonSuperclass
				out.write(writer.toByteArray());
				System.err.println("Failed to write " + cn.getName() + "! Writing with COMPUTE_MAXS, " +
						"which may cause runtime abnormalities");
			}
		} catch (Exception e) {
			System.err.println("Failed to write " + cn.getName() + "! Skipping class...");
		}

		return 1;
	}

	public ClassWriter buildClassWriter(ClassTree tree, int flags) {
		return new ClassWriter(flags) {


			// this method in ClassWriter uses the systemclassloader as
			// a stream location to load the super class, however, most of
			// the time the class is loaded/read and parsed by us so it
			// isn't defined in the system classloader. in certain cases
			// we may not even want it to be loaded/resolved and we can
			// bypass this by implementing the hierarchy scanning algorithm
			// with ClassNodes rather than Classes.
			@Override
			protected String getCommonSuperClass(String type1, String type2) {
				ClassNode ccn = source.findClassNode(type1);
				ClassNode dcn = source.findClassNode(type2);

				if(ccn == null) {
//		    		return "java/lang/Object";
					ClassNode c;
					try {
						c = ClassHelper.create(type1);
					} catch (IOException e) {
						e.printStackTrace();
						return "java/lang/Object";
					}
					if(c == null) {
						return "java/lang/Object";
					}
					throw new UnsupportedOperationException(c.toString());
					// classTree.build(c);
					// return getCommonSuperClass(type1, type2);
				}

				if(dcn == null) {

//		    		return "java/lang/Object";
					ClassNode c;
					try {
						c = ClassHelper.create(type2);
					} catch (IOException e) {
						e.printStackTrace();
						return "java/lang/Object";
					}
					if(c == null) {
						return "java/lang/Object";
					}
					throw new UnsupportedOperationException(c.toString());
					// classTree.build(c);
					// return getCommonSuperClass(type1, type2);
				}

				Collection<ClassNode> c = tree.getAllParents(ccn);
				Collection<ClassNode> d = tree.getAllParents(dcn);


				final boolean poggers = type1.equalsIgnoreCase("me/tecnio/antihaxerman/packetevents/utils/netty/bytebuf/ByteBufUtil_8") ||
						type2.equalsIgnoreCase("me/tecnio/antihaxerman/packetevents/utils/netty/bytebuf/ByteBufUtil_8");
				if (poggers) {
					System.out.println("YEEET");
					System.out.println(type1);
					System.out.println(type2);
					System.out.println("--------");
				}

				final Stack<ClassNode> stack = new Stack<>();
				stack.addAll(Lists.reverse(tree.getAllParents(ccn)));

				final Set<ClassNode> nodes = new HashSet<>(tree.getAllParents(dcn));
				while (!stack.isEmpty()) {
					if (nodes.contains(stack.peek())) {
						if (poggers) {
							System.out.println("POOOOOG " + stack.peek().getName());
						}

						return stack.peek().getName();
					}

					stack.pop();
				}


				{
					throw new IllegalStateException("Could not find common class type between " + Arrays.toString(new Object[]{ccn, dcn}));
				}

				/*if(Modifier.isInterface(ccn.node.access) || Modifier.isInterface(dcn.node.access)) {
					// enums as well?
					return "java/lang/Object";
				} else {
					do {
						ClassNode nccn = source.findClassNode(ccn.node.superName);
						if(nccn == null)
							break;
						ccn = nccn;
						c = tree.getAllParents(ccn);
					} while(!c.contains(dcn));
					return ccn.getName();
				}*/
			}
		};
	}

	/**
	 * Writes a resource to the Jar.
	 *
	 * @param out The {@link JarOutputStream}.
	 * @param name The name of the file.
	 * @param file File as a byte[].
	 * @throws IOException If there is a write error.
	 * @return The amount of things dumped, 1 or if you're not dumping it 0.
	 */
	@Override
	public int dumpResource(JarOutputStream out, String name, byte[] file) throws IOException {
		JarEntry entry = new JarEntry(name);
		out.putNextEntry(entry);
		out.write(file);
		return 1;
	}
}
