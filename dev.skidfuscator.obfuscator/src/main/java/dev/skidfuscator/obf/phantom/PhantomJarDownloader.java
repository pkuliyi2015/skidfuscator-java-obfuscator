package dev.skidfuscator.obf.phantom;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import ch.qos.logback.classic.Level;
import dev.skidfuscator.obf.directory.SkiddedDirectory;
import dev.skidfuscator.obf.utils.Files;
import dev.skidfuscator.obf.utils.ProgressUtil;
import dev.skidfuscator.obf.utils.TypeUtil;
import lombok.SneakyThrows;
import me.tongfei.progressbar.ProgressBar;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.clyze.jphantom.ClassMembers;
import org.clyze.jphantom.JPhantom;
import org.clyze.jphantom.Options;
import org.clyze.jphantom.Phantoms;
import org.clyze.jphantom.access.ClassAccessStateMachine;
import org.clyze.jphantom.access.FieldAccessStateMachine;
import org.clyze.jphantom.access.MethodAccessStateMachine;
import org.clyze.jphantom.adapters.ClassPhantomExtractor;
import org.clyze.jphantom.hier.ClassHierarchy;
import org.clyze.jphantom.hier.IncrementalClassHierarchy;
import org.mapleir.asm.ClassHelper;
import org.mapleir.asm.ClassNode;
import org.objectweb.asm.*;
import org.topdank.byteengineer.commons.asm.ASMFactory;
import org.topdank.byteengineer.commons.data.JarContents;
import org.topdank.byteengineer.commons.data.JarInfo;
import org.topdank.byteengineer.commons.data.JarResource;
import org.topdank.byteengineer.commons.data.LocateableJarContents;

import com.google.common.io.ByteStreams;
import org.topdank.byteio.in.AbstractJarDownloader;

public class PhantomJarDownloader<C extends ClassNode> extends AbstractJarDownloader<C> {

	protected final JarInfo jarInfo;
	protected JarContents<C> phantomContents;
	private final Logger logger = LogManager.getLogger(this.getClass());

	public PhantomJarDownloader(JarInfo jarInfo) {
		super();
		this.jarInfo = jarInfo;
		this.phantomContents = new JarContents<>();
	}

	public PhantomJarDownloader(ASMFactory<C> factory, JarInfo jarInfo) {
		super(factory);
		this.jarInfo = jarInfo;
		this.phantomContents = new JarContents<>();
	}

	@SneakyThrows
	@Override
	public void download() throws IOException {
		URL url = null;
		JarURLConnection connection = (JarURLConnection) (url = new URL(jarInfo.formattedURL())).openConnection();
		JarFile jarFile = connection.getJarFile();
		Enumeration<JarEntry> entries = jarFile.entries();
		contents = new LocateableJarContents<>(url);

		/*
		 * Map holding all the regular data
		 */
		Map<String, byte[]> data = new HashMap<>();

		/*
		 * Add all the regular data to the map, add the resources to the jar contents, we won't
		 * be needing them for now. Once that's done, cleate a new type map.
		 */
		while (entries.hasMoreElements()) {
			JarEntry entry = entries.nextElement();
			byte[] bytes = read(jarFile.getInputStream(entry));
			if (entry.getName().endsWith(".class")) {
				data.put(entry.getName(), bytes);
			} else {
				JarResource resource = new JarResource(entry.getName(), bytes);
				contents.getResourceContents().add(resource);
			}
		}

		Map<Type, org.objectweb.asm.tree.ClassNode> typeMap = new HashMap<>();

		/*
		 * Create all the classes necessary based on the data we cached.
		 */
		logger.info("[$] Generating classes...");
		try (ProgressBar progressBar = ProgressUtil.progress(data.size())){
			data.forEach((name, db) -> {
				C cn;
				try {
					cn = factory.create(db, name);
					if(!data.containsKey(cn.getName())) {
						contents.getClassContents().add(cn);
					} else {
						throw new IllegalStateException("duplicate: " + cn.getName());
					}
				} catch (IOException e) {
					e.printStackTrace();
				}

				progressBar.step();
			});
		}

		/*
		 * Just like in Recaf, copy the file to a temporary file. Overwrite if necessary. This
		 * I suppose is absolutely useless aside from trying not to fuck over the OG jar....
		 * just in case...
		 */
		final File input = new File(SkiddedDirectory.getCache(), "temp-copy.jar");
		Files.writeArchive(true, input, data);
		logger.info("[$] Wrote classes to temp file, starting phantom analysis... [" + data.size() + "]");

		/*
		 * Set up JPhantom (Recaf matt edition) and read all the classes from our cache.
		 * Use the copied input to generate a class hierarchy. This can be quite slow
		 * at times. I'll need to optimize this process.
		 */
		Options.V().setSoftFail(true);
		Options.V().setJavaVersion(8);

		try {
			Field field = Options.class.getDeclaredField("logger");
			field.setAccessible(true);
			ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) field.get(null);
			logger.setLevel(Level.OFF);
		} catch (Exception e) {
			e.printStackTrace();
		}

		Options.V().setLogLevel(Level.OFF);
		ClassHierarchy hierarchy = clsHierarchyFromArchive(new JarFile(input));
		ClassMembers members = ClassMembers.fromJar(new JarFile(input), hierarchy);

		logger.info("[$] Beginning Phantom Extraction...");
		try (ProgressBar progressBar = ProgressUtil.progress(data.size())){
			data.values().forEach(c -> {
				ClassReader cr = new ClassReader(c);
				if (cr.getClassName().contains("$")) {
					progressBar.step();
					return;
				}

				try {
					cr.accept(new ClassPhantomExtractor(hierarchy, members), 0);
				} catch (Throwable t) {
					logger.debug("Phantom extraction failed: {}", t);
				}

				progressBar.step();
			});
		}

		// Remove duplicate constraints for faster analysis
		Set<String> existingConstraints = new HashSet<>();
		ClassAccessStateMachine.v().getConstraints().removeIf(c -> {
			boolean isDuplicate = existingConstraints.contains(c.toString());
			existingConstraints.add(c.toString());
			return isDuplicate;
		});

		logger.info("[$] Beginning Phantom Execution...");
		JPhantom phantom;
		try (ProgressBar progressBar = ProgressUtil.progress(1)){
			// Execute and populate the current resource with generated classes
			phantom = new JPhantom(typeMap, hierarchy, members);
			phantom.run();
			progressBar.step();
		}

		/*
		 * Generate the classes and export them to our phantom content jar
		 * class cache. This will be used as a library during obfuscation.
		 */
		logger.info("[$] Outputting phantom classes...");
		final Map<String, C> namedMap = contents.getClassContents().namedMap();

		try (ProgressBar progressBar = ProgressUtil.progress(phantom.getGenerated().size())){
			phantom.getGenerated().forEach((k, v) -> {
				final ClassNode classNode = ClassHelper.create(decorate(v));

				if (namedMap.containsKey(classNode.getName())) {
                    return;
                }

				phantomContents.getClassContents().add((C) classNode);
				progressBar.step();
			});
		}

		/*
		 * Cleanup Cleanup Cleanup Cleanup Cleanup Cleanup Cleanup Cleanup Cleanup
		 * Cleanup Cleanup Cleanup Cleanup Cleanup Cleanup Cleanup Cleanup Cleanup
		 * Cleanup Cleanup Cleanup Cleanup Cleanup Cleanup Cleanup Cleanup Cleanup
		 */
		logger.info("[$] Phantom analysis complete, cleaning temp file [x" + phantomContents.getClassContents().size() + "]");
		// Cleanup
		typeMap.clear();
		Phantoms.refresh();
		ClassAccessStateMachine.refresh();
		FieldAccessStateMachine.refresh();
		MethodAccessStateMachine.refresh();
		java.nio.file.Files.deleteIfExists(input.toPath());


	}

	public JarContents<C> getPhantomContents() {
		return phantomContents;
	}

	/**
	 * Adds a note to the given class that it has been auto-generated.
	 *
	 * @param generated
	 * 		Input generated JPhantom class.
	 *
	 * @return modified class that clearly indicates it is generated.
	 */
	private byte[] decorate(byte[] generated) {
		ClassWriter cw = new ClassWriter(0);
		ClassVisitor cv = new ClassVisitor(Opcodes.ASM8, cw) {
			@Override
			public void visitEnd() {
				visitAnnotation("LAutoGenerated;", true)
						.visit("msg", "Recaf/JPhantom automatically generated this class");
				super.visitEnd();
			}
		};
		new ClassReader(generated).accept(cv, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
		return cw.toByteArray();
	}

	/**
	 * This is copy pasted from JPhantom, modified to be more lenient towards obfuscated inputs.
	 *
	 * @param file
	 * 		Some jar file.
	 *
	 * @return Class hierarchy.
	 *
	 * @throws IOException
	 * 		When the archive cannot be read.
	 */
	private ClassHierarchy clsHierarchyFromArchive(JarFile file) throws IOException {
		try {
			ClassHierarchy hierarchy = new IncrementalClassHierarchy();
			for (Enumeration<JarEntry> e = file.entries(); e.hasMoreElements(); ) {
				JarEntry entry = e.nextElement();
				if (entry.isDirectory()) {
                    continue;
                }
				if (!entry.getName().endsWith(".class")) {
                    continue;
                }
				try (InputStream stream = file.getInputStream(entry)) {
					ClassReader reader = new ClassReader(stream);
					String[] ifaceNames = reader.getInterfaces();
					Type clazz = Type.getObjectType(reader.getClassName());
					Type superclass = reader.getSuperName() == null ?
							TypeUtil.OBJECT_TYPE : Type.getObjectType(reader.getSuperName());
					Type[] ifaces = new Type[ifaceNames.length];
					for (int i = 0; i < ifaces.length; i++) {
                        ifaces[i] = Type.getObjectType(ifaceNames[i]);
                    }
					// Add type to hierarchy
					boolean isInterface = (reader.getAccess() & Opcodes.ACC_INTERFACE) != 0;
					try {
						if (isInterface) {
							hierarchy.addInterface(clazz, ifaces);
						} else {
							hierarchy.addClass(clazz, superclass, ifaces);
						}
					} catch (Exception ex) {
						logger.error(String.format("JPhantom: Hierarchy failure for: %s", clazz), ex);
					}
				} catch (IOException ex) {
					logger.error(String.format("JPhantom: IO Error reading from archive: %s", file.getName()), ex);
				}
			}
			return hierarchy;
		} finally {
			file.close();
		}
	}


	private byte[] read(InputStream inputStream) throws IOException {
		return ByteStreams.toByteArray(inputStream);
	}
}
