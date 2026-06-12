package com.hecate.agent;

import net.bytebuddy.jar.asm.*;

public class SynchronizedBlockTransformer extends MethodVisitor {

    public SynchronizedBlockTransformer(int api, MethodVisitor methodVisitor) {
        super(api, methodVisitor);
    }

    @Override
    public void visitMaxs(int maxStack, int maxLocals) {
        super.visitMaxs(maxStack + 2, maxLocals);
    }

    @Override
    public void visitInsn(int opcode) {

        if (opcode == Opcodes.MONITORENTER) {
            super.visitInsn(Opcodes.DUP);
            super.visitInsn(Opcodes.DUP);
            super.visitMethodInsn(Opcodes.INVOKESTATIC, "com/hecate/util/MonitorHelper", "beforeMonitorEnter", "(Ljava/lang/Object;)V", false);
            super.visitInsn(Opcodes.MONITORENTER);
            super.visitMethodInsn(Opcodes.INVOKESTATIC, "com/hecate/util/MonitorHelper", "afterMonitorEnter", "(Ljava/lang/Object;)V", false);
        }

        else if (opcode == Opcodes.MONITOREXIT) {
            super.visitInsn(Opcodes.DUP);
            super.visitMethodInsn(Opcodes.INVOKESTATIC, "com/hecate/util/MonitorHelper", "beforeMonitorExit", "(Ljava/lang/Object;)V", false);
            super.visitInsn(opcode);
        }

        else {
            super.visitInsn(opcode);
        }

    }

}
