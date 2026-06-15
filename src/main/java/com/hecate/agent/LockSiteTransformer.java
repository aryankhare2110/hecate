package com.hecate.agent;

import net.bytebuddy.jar.asm.*;

public class LockSiteTransformer extends MethodVisitor {

    private static final String HELPER = "com/hecate/util/MonitorHelper";
    private static final String JUC_LOCKS_PREFIX = "java/util/concurrent/locks/";

    private final boolean[] modified;

    public LockSiteTransformer(int api, MethodVisitor methodVisitor, boolean[] modified) {
        super(api, methodVisitor);
        this.modified = modified;
    }

    @Override
    public void visitMaxs(int maxStack, int maxLocals) {
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
            if (("lock".equals(name) || "lockInterruptibly".equals(name)) && "()V".equals(descriptor)) {
                modified[0] = true;
                super.visitInsn(Opcodes.DUP);
                super.visitInsn(Opcodes.DUP);
                super.visitMethodInsn(Opcodes.INVOKESTATIC, HELPER, "beforeMonitorEnter", "(Ljava/lang/Object;)V", false);
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                super.visitMethodInsn(Opcodes.INVOKESTATIC, HELPER, "afterMonitorEnter", "(Ljava/lang/Object;)V", false);
                return;
            }
            if ("unlock".equals(name) && "()V".equals(descriptor)) {
                modified[0] = true;
                super.visitInsn(Opcodes.DUP);
                super.visitMethodInsn(Opcodes.INVOKESTATIC, HELPER, "beforeMonitorExit", "(Ljava/lang/Object;)V", false);
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                return;
            }
           
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

/*
 * Notes
 * - An ASM MethodVisitor that wraps lock sites so MonitorHelper observes every acquire/release.
 * - Monitors: MONITORENTER expands to DUP, DUP, beforeMonitorEnter, MONITORENTER,
 *   afterMonitorEnter; MONITOREXIT expands to DUP, beforeMonitorExit, MONITOREXIT. The
 *   duplicated reference lets the helper be called without disturbing the program's own use of
 *   the lock object.
 * - j.u.c Lock calls (owner under java/util/concurrent/locks/): lock()/lockInterruptibly() get
 *   the same WAIT-then-ACQUIRE wrapping; unlock() records RELEASE; no-arg tryLock() routes its
 *   boolean result through afterTryLock, so an ACQUIRE is recorded only on success. Timed
 *   tryLock(long, TimeUnit) is left alone, since its extra args sit above the receiver.
 * - visitMaxs adds 2 stack slots: the worst-case single-site expansion, which never nests.
 * - modified[0] is set whenever a site is rewritten, letting LockClassFileTransformer skip
 *   classes it did not change.
 */

