package com.gmail.woodyc40.loader;

import com.sun.tools.attach.AgentInitializationException;
import com.sun.tools.attach.AgentLoadException;
import com.sun.tools.attach.AttachNotSupportedException;
import javassist.*;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import tk.ivybits.agent.AgentLoader;
import tk.ivybits.agent.Tools;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.reflect.Field;
import java.net.URLClassLoader;

// REQUIRE: JAVASSIST
// REQUIRE: https://github.com/Xyene/ASM-Late-Bind-Agent
public final class BukkitLoader {
    private static final String GET_LOADER_CALL = BukkitLoader.class.getName() + "." + "getLoader()";

    // Singletons
    private static ClassLoader loader;
    private static Instrumentation instrumentation;

    public static void agentmain(String string, Instrumentation instrument) {
        instrumentation = instrument;
    }

    private static Instrumentation getInstr() {
        if (instrumentation == null) {
            try {
                Tools.loadAgentLibrary();
                AgentLoader.attachAgentToJVM(Tools.getCurrentPID(), BukkitLoader.class);
            } catch (IOException | AttachNotSupportedException | AgentLoadException | AgentInitializationException e) {
                e.printStackTrace();
            }
        }

        return instrumentation;
    }

    private BukkitLoader() {} // Private constructor

    public static void loadAll() {
        Class<?> pcl = null;
        try {
            pcl = Class.forName("org.bukkit.plugin.java.PluginClassLoader");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        if (pcl == null) return;

        // While this class *should* be loaded by the same plugin class loader
        // you may have to obtain the actual class loader of the plugin directly
        ClassLoader current = BukkitLoader.class.getClassLoader();
        if (!(pcl.isInstance(current))) {
            // In which case, you would need to obtain it from the main class
            throw new RuntimeException("The loader obtained was not of type PluginClassLoader");
        }

        if (loader == null) {
            loader = current;
        }

        try {
            final ClassPool pool = ClassPool.getDefault();
            pool.appendClassPath(new LoaderClassPath(current));

            final CtClass compiledClass = pool.get(pcl.getName());
            final CtMethod me = compiledClass.getDeclaredMethod("loadPlugin", new CtClass[]{
                    pool.get(File.class.getName())});

            me.instrument(new ExprEditor() {
                public void edit(MethodCall m) throws CannotCompileException {
                    if (m.getMethodName().equals("put")) {
                        try {
                            me.addLocalVariable("clsLoader", pool.get(URLClassLoader.class.getName()));
                        } catch (NotFoundException e) {
                            e.printStackTrace();
                        }
                        me.insertBefore("clsLoader = " + GET_LOADER_CALL + ";");

                        // Avoid possible side effects
                        m.replace("clsLoader.addURL(file.toURI().toURL());\n" + "loaders.put(description.getName(), loader);");
                        // I cannot dictate your loading code :/
                        // You have to add it yourself
                    }
                }
            });

            getInstr().redefineClasses(new ClassDefinition(pcl, compiledClass.toBytecode()));
        } catch (CannotCompileException | NotFoundException | IOException | ClassNotFoundException | UnmodifiableClassException e) {
            e.printStackTrace();
        }
    }

    public static URLClassLoader getLoader() {
        URLClassLoader loader = (URLClassLoader) BukkitLoader.loader;
        if (loader == null) {
            loadAll();

            // Refresh
            return getLoader();
        }

        return loader;
    }

    // Or whatever dohicky you'd like to have as the reflection :)
    private static Field getField(Class<?> cls, String name) {
        try {
            Field field = cls.getDeclaredField(name);
            field.setAccessible(true);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }
}