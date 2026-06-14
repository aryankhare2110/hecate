package com.hecate.agent;

import net.bytebuddy.jar.asm.*;

/**
 * Rewrites lock sites inside application methods so every acquire/release is observed.
 *
 * Two kinds of locking are instrumented:
 * <ul>
 *   <li><b>Intrinsic monitors</b> — the {@code MONITORENTER}/{@code MONITOREXIT} opcodes
 *       emitted for {@code synchronized} blocks.</li>
 *   <li><b>{@code java.util.concurrent.locks.Lock}</b> — explicit {@code lock()},
 *       {@code lockInterruptibly()}, {@code unlock()} and no-arg {@code tryLock()} calls.</li>
 * </ul>
 *
 * In both cases the lock object is duplicated on the operand stack so {@link com.hecate.util.MonitorHelper}
 * can be called around the real operation without disturbing the program's own use of it.
 */
public class LockSiteTransformer extends MethodVisitor {

    private static final String HELPER = "com/hecate/util/MonitorHelper";
    private static final String JUC_LOCKS_PREFIX = "java/util/concurrent/locks/";

    /** Single-element flag shared with the class transformer; set true once any site is rewritten. */
    private final boolean[] modified;

    public LockSiteTransformer(int api, MethodVisitor methodVisitor, boolean[] modified) {
        super(api, methodVisitor);
        this.modified = modified;
    }

    @Override
    public void visitMaxs(int maxStack, int maxLocals) {
        // Worst-case expansion (the monitor double-DUP and the lock() double-DUP) adds two
        // operand-stack slots at a single site; expansions never nest, so +2 is sufficient.
        super.visitMaxs(maxStack + 2, maxLocals);
    }

    @Override
    public void visitInsn(int opcode) {
        if (opcode == Opcodes.MONITORENTER) {
            modified[0] = true;
            super.visitInsn(Opcodes.DUP);
            super.visitInsn(Opcodes.DUP);
            super.visitMethodInsn(Opcodes.INVOKESTATIC, HELPER, "beforeMonitorEnter", "(Ljava/lang/Object;)V", false);
            super.visitInsn(Opcodes.MONITORENTER);
            super.visitMethodInsn(Opcodes.INVOKESTATIC, HELPER, "afterMonitorEnter", "(Ljava/lang/Object;)V", false);
        } else if (opcode == Opcodes.MONITOREXIT) {
            modified[0] = true;
            super.visitInsn(Opcodes.DUP);
            super.visitMethodInsn(Opcodes.INVOKESTATIC, HELPER, "beforeMonitorExit", "(Ljava/lang/Object;)V", false);
            super.visitInsn(opcode);
        } else {
            super.visitInsn(opcode);
        }
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
        if (owner != null && owner.startsWith(JUC_LOCKS_PREFIX)) {
            // lock() / lockInterruptibly(): record WAIT, perform the real call, record ACQUIRE.
            if (("lock".equals(name) || "lockInterruptibly".equals(name)) && "()V".equals(descriptor)) {
                modified[0] = true;
                super.visitInsn(Opcodes.DUP);
                super.visitInsn(Opcodes.DUP);
                super.visitMethodInsn(Opcodes.INVOKESTATIC, HELPER, "beforeMonitorEnter", "(Ljava/lang/Object;)V", false);
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                super.visitMethodInsn(Opcodes.INVOKESTATIC, HELPER, "afterMonitorEnter", "(Ljava/lang/Object;)V", false);
                return;
            }
            // unlock(): record RELEASE, then perform the real call.
            if ("unlock".equals(name) && "()V".equals(descriptor)) {
                modified[0] = true;
                super.visitInsn(Opcodes.DUP);
                super.visitMethodInsn(Opcodes.INVOKESTATIC, HELPER, "beforeMonitorExit", "(Ljava/lang/Object;)V", false);
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                return;
            }
            // no-arg tryLock(): record ACQUIRE only if it returned true (afterTryLock passes
            // the boolean result straight through). Timed tryLock(long,TimeUnit) is left alone
            // because its extra arguments sit above the receiver on the stack.
            if ("tryLock".equals(name) && "()Z".equals(descriptor)) {
                modified[0] = true;
                super.visitInsn(Opcodes.DUP);
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                super.visitMethodInsn(Opcodes.INVOKESTATIC, HELPER, "afterTryLock", "(Ljava/lang/Object;Z)Z", false);
                return;
            }
        }
        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
    }
}
