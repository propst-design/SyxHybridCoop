package script;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.nio.file.Files;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import init.paths.PATH;
import init.paths.PATHS;
import snake2d.Errors;
import snake2d.LOG;
import snake2d.util.sets.ArrayList;
import snake2d.util.sets.KeyMap;
import snake2d.util.sets.LIST;
import snake2d.util.sets.LinkedList;

public final class ScriptLoad{
	
	private static KeyMap<LinkedList<ScriptLoad>> cache = new KeyMap<>();
	
	public final SCRIPT script;
	public final String className;
	public final String file;
	public final String key;
	
	ScriptLoad(SCRIPT script, String cn, String file){
		this.script = script;
		this.className = cn;
		this.file = file;
		key = file + "->" + cn;
	}

	static LIST<ScriptLoad> get(String script){
		return new Init().compileScripts(script);
	}
	
	static LIST<ScriptLoad> getAll(){
		return new ArrayList<>(new Init().compileScripts(PATHS.SCRIPT().jar.getFiles()));
	}
	
	private static class Init {
		
		private final PATH pathRoot = PATHS.SCRIPT().jar;
		private final LinkedList<ScriptLoad> all = new LinkedList<>();
		private final LinkedList<URL> urlList = new LinkedList<URL>();
		private final KeyMap<String> classToJar = new KeyMap<>();
		
		Init(){
			forceInit(SCRIPT.class);
		}
		
		private LIST<ScriptLoad> compileScripts(String... files) {
			
			files = grabCachedScripts(files);
			
			if (files.length == 0)
				return all;
			
			for (String file : files) {
				
				JarInputStream jarFile = copyAndMakeJarUrl(file);
				if (jarFile == null)
					continue;
				
				try {
					JarEntry je = jarFile.getNextJarEntry();
					
					while (je != null) {
						JarEntry jarEntry = je;
						je = jarFile.getNextJarEntry();
						if (jarEntry.getName().contains("META-INF"))
							continue;
						processJarEntry(jarEntry, file);					
					}

					jarFile.close();
					
				} catch (IOException e) {
					LOG.err("script: " + pathRoot.get() + File.separator + file + " unable to cache!");
					e.printStackTrace();
				}
			}
			
			loadScripts();
			
			cache.clear();
			
			for (ScriptLoad l : all) {
				if (!cache.containsKey(l.file))
					cache.put(l.file, new LinkedList<>());
				cache.get(l.file).add(l);
			}
			
			return all;
		}
		
		private String[] grabCachedScripts(String... files) {
			int uninited = 0;
			
			for (String f : files)  {
				if (cache.containsKey(f)) {
					all.add(cache.get(f));
				}else
					uninited++;
			}
			
			String[] nFiles = new String[uninited];
			uninited = 0;
			for (String f : files)  {
				if (!cache.containsKey(f)) {
					nFiles[uninited++] = f;
				}
			}
			
			return nFiles;
			
		}
		
		private void loadScripts() {
			LOG.ln("SCRIPTS");
			
			ClassLoader loader = ClassLoader.getSystemClassLoader();
			
			for (String className : classToJar.keys()) {
				Class<?> s;
				try {
					s = loader.loadClass(className);
				} catch (ClassNotFoundException e1) {
					// [COOP-MOD START]
					LOG.err("Skipping class not found on classpath: " + className + " in " + classToJar.get(className));
					continue;
					// [COOP-MOD END]
				} catch (Throwable e2) {
					// [COOP-MOD START]
					LOG.err("Skipping class with resolution failure: " + className + " (" + e2.getMessage() + ")");
					continue;
					// [COOP-MOD END]
				}

				if (SCRIPT.class.isAssignableFrom(s) && !Modifier.isAbstract(s.getModifiers())) {
					try {
						SCRIPT sc =   (SCRIPT)s.getDeclaredConstructor().newInstance();
						all.add(new ScriptLoad(sc, className, classToJar.get(className)));
						LOG.ln(" -script available: : " + sc.name());
					}catch(IllegalAccessException e) {
						throw new Errors.DataError(className + " could not be created. Probably cause would be a non-public constructor, or constructor parameters", classToJar.get(className));
					} catch (Exception e) {
						e.printStackTrace();
						throw new RuntimeException("some weirdness with loading scripts. See std err");
					}
					
					
				}
			}
		}
		
		private JarInputStream copyAndMakeJarUrl(String jarFile) {
			if (!pathRoot.exists(jarFile)) {
				LOG.err("script: " + pathRoot.get() + File.separator + jarFile + " does not exist and will be ignored.");
				return null;
			}
			LOG.ln("loading script jar " + jarFile);
			
			try {
				java.nio.file.Path p = pathRoot.get(jarFile);
				urlList.add(p.toUri().toURL());
				return new JarInputStream(Files.newInputStream(p));
			} catch (IOException e) {
				LOG.err("script: " + pathRoot.get() + File.separator + jarFile + " unable to cache! Ignoring.");
				e.printStackTrace();
				
			}
			return null;
			
		}
		
		private void processJarEntry(JarEntry jarEntry, String jarFile) {
			if (jarEntry.getName().endsWith(".class")) {
				String className = jarEntry.getName();
				className = jarEntry.getName().substring(0, jarEntry.getName().length() - 6);
				className = className.replaceAll("/", "\\.");
				
				if (classToJar.containsKey(className)) {
					//utility libraries, these are fine ???
					return;
					
					//throw new Errors.DataError(className + " this class already exists in another script jar and game can't be loaded. Other jar: " + classToJar.get(className), pathRoot.get(jarFile));
				}
				
				try {
					if (Class.forName(className) != null) {
						throw new Errors.DataError(className + " already exist in the game and will clash with the game. This class needs to be renamed", pathRoot.get(jarFile));
					}
				}catch(Exception e) {
					//this is good!
				}catch(NoClassDefFoundError e) {
					throw new Errors.DataError(className + " could not be loaded.", pathRoot.get(jarFile));
				}
				
				classToJar.put(className, jarFile);
			}
		}
		
		private static <T> Class<T> forceInit(Class<T> klass) {
			try {
				Class.forName(klass.getName(), true, klass.getClassLoader());
			} catch (ClassNotFoundException e) {
				throw new AssertionError(e); // Can't happen
			}
			return klass;
		}
	}

}
