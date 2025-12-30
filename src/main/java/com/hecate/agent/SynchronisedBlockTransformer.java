package com.hecate.agent;

import net.bytebuddy.jar.asm.*;

import java.util.Stack;

public class SynchronisedBlockTransformer extends MethodVisitor {

    private final Stack<Integer> lockVariables = new Stack<>();
    private boolean justStoredLockVariable = false;
    private int lastStoredVariable = -1;

    public SynchronisedBlockTransformer(int api, MethodVisitor methodVisitor) {
        super(api, methodVisitor);
    }

    @Override
    public void visitVarInsn(int opcode, int var) {
        if (opcode == Opcodes.ASTORE) {
            justStoredLockVariable = true;
            lastStoredVariable = var;
        }
        super.visitVarInsn(opcode, var);
    }

    @Override
    public void visitInsn(int opcode) {

        if (opcode == Opcodes.MONITORENTER) {
            super.visitInsn(Opcodes.DUP);
            super.visitMethodInsn(Opcodes.INVOKESTATIC, "com/hecate/util/MonitorHelper", "beforeMonitorEnter", "(Ljava/lang/Object;)V", false);
            super.visitInsn(Opcodes.MONITORENTER);

            if (justStoredLockVariable) {
                lockVariables.push(lastStoredVariable);
                justStoredLockVariable = false;
                super.visitVarInsn(Opcodes.ALOAD, lastStoredVariable);
                super.visitMethodInsn(Opcodes.INVOKESTATIC, "com/hecate/util/MonitorHelper", "afterMonitorEnter", "(Ljava/lang/Object;)V", false);
            }
        }

        else if (opcode == Opcodes.MONITOREXIT) {
            super.visitInsn(Opcodes.DUP);
            super.visitMethodInsn(Opcodes. INVOKESTATIC, "com/hecate/agent/MonitorHelper", "beforeMonitorExit", "(Ljava/lang/Object;)V", false);
            super.visitInsn(opcode);

            if (!lockVariables.isEmpty()) {
                lockVariables.pop();
            }
        }

        else {
            super.visitInsn(opcode);
        }

    }

}
