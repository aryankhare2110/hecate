package com.hecate.agent;

import net.bytebuddy.jar.asm.ClassReader;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.ClassWriter;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

public class LockClassFileTransformer implements ClassFileTransformer {

    private static final String[] IGNORED_PREFIXES = {
            "java/", "javax/", "sun/", "com/sun/", "jdk/", "net/bytebuddy/",
            "com/hecate/agent/", "com/hecate/events/", "com/hecate/util/"
    };

    private static final int MAJOR_VERSION_OFFSET = 6;
    private static final int JAVA_17_MAJOR = 61;

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (className == null || isIgnored(className)) {
            return null; 
        }
        try {
            ClassReader reader;
            boolean versionPatched = false;
            try {
                reader = new ClassReader(classfileBuffer);
            } catch (IllegalArgumentException tooNew) {
                byte[] downgraded = classfileBuffer.clone();
                downgraded[MAJOR_VERSION_OFFSET] = 0;
                downgraded[MAJOR_VERSION_OFFSET + 1] = (byte) JAVA_17_MAJOR;
                reader = new ClassReader(downgraded);
                versionPatched = true;
            }

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
            if (!modified[0]) {
                return null;
            }
            byte[] result = writer.toByteArray();
            if (versionPatched) {
                result[MAJOR_VERSION_OFFSET] = classfileBuffer[MAJOR_VERSION_OFFSET];
                result[MAJOR_VERSION_OFFSET + 1] = classfileBuffer[MAJOR_VERSION_OFFSET + 1];
            }
            return result;
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

/*
 * Notes
 * - A raw java.lang.instrument ClassFileTransformer that rewrites synchronized blocks and
 *   j.u.c Lock call sites by driving an ASM ClassReader -> ClassWriter over every method.
 * - Used instead of ByteBuddy's AsmVisitorWrapper.ForDeclaredMethods, which silently skips
 *   synthetic (lambda) methods and constructors, leaving locks in lambdas uninstrumented.
 * - ClassWriter(reader, 0) means no frame recomputation. The inserted code is operand-stack
 *   neutral at basic-block boundaries, so existing stack-map frames stay valid;
 *   LockSiteTransformer bumps maxStack itself.
 * - Version handling: if ASM rejects a too-new class file (a JDK newer than the bundled ASM),
 *   the major version (bytes 6-7) is temporarily lowered to Java 17 for reading and restored on
 *   the output. This keeps the agent working on future JDKs, e.g. Java 25 (version 69).
 * - Returns null (leave the class unchanged) for ignored packages, unmodified classes, or any
 *   error, so instrumentation never breaks the host program.
 */

