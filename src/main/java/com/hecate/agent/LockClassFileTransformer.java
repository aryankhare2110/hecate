package com.hecate.agent;

import net.bytebuddy.jar.asm.ClassReader;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.ClassWriter;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

/**
 * Instruments {@code synchronized} blocks and {@code java.util.concurrent.locks.Lock}
 * call sites by rewriting bytecode with raw ASM.
 *
 * Why not ByteBuddy's {@code AsmVisitorWrapper.ForDeclaredMethods}? It silently skips
 * synthetic methods (lambda bodies) and constructors, so any lock taken inside a lambda
 * went uninstrumented. A plain {@link ClassFileTransformer} driving an ASM
 * {@link ClassReader}/{@link ClassWriter} visits <em>every</em> method, closing that gap.
 *
 * Only linear instructions are inserted and each rewrite is operand-stack-neutral at basic
 * block boundaries, so existing stack-map frames stay valid — the class is copied with
 * {@code ClassWriter(reader, 0)} (no frame recomputation) and {@link LockSiteTransformer}
 * bumps {@code maxStack} itself.
 */
public class LockClassFileTransformer implements ClassFileTransformer {

    private static final String[] IGNORED_PREFIXES = {
            "java/", "javax/", "sun/", "com/sun/", "jdk/", "net/bytebuddy/",
            "com/hecate/agent/", "com/hecate/events/", "com/hecate/util/"
    };

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (className == null || isIgnored(className)) {
            return null; // null = leave the class unchanged
        }
        try {
            ClassReader reader = new ClassReader(classfileBuffer);
            ClassWriter writer = new ClassWriter(reader, 0);
            boolean[] modified = {false};

            ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                    return mv == null ? null : new LockSiteTransformer(Opcodes.ASM9, mv, modified);
                }
            };
            reader.accept(visitor, 0);

            // Untouched classes (the vast majority) keep their original bytes.
            return modified[0] ? writer.toByteArray() : null;
        } catch (Throwable t) {
            System.err.println("[Hecate] Failed to instrument " + className + ": " + t);
            return null;
        }
    }

    private static boolean isIgnored(String className) {
        for (String prefix : IGNORED_PREFIXES) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
