package com.hecate.agent;

import com.hecate.events.EventCollector;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;

import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

public class HecateAgent {

    public static void premain(String agentArgs, Instrumentation inst) {

        System.out.println("[Hecate] Agent started");
        System.out.println("[Hecate] Runtime concurrency analysis active");

        EventCollector.getInstance().startCollecting();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            EventCollector.getInstance().stopCollecting();
            System.out.println("[Hecate] Captured " + EventCollector.getInstance().getEventCount() + " events");
        }));

        new AgentBuilder.Default()

                .ignore(ElementMatchers.nameStartsWith("java.")
                        .or(ElementMatchers.nameStartsWith("javax."))
                        .or(ElementMatchers.nameStartsWith("sun."))
                        .or(ElementMatchers.nameStartsWith("com.sun."))
                        .or(ElementMatchers.nameStartsWith("jdk."))
                        .or(ElementMatchers.nameStartsWith("net.bytebuddy."))
                        .or(ElementMatchers.nameStartsWith("com.hecate.")))

                .type(ElementMatchers.any())

                .transform(new AgentBuilder.Transformer() {
                    @Override
                    public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder, TypeDescription typeDescription, ClassLoader classLoader, JavaModule module, ProtectionDomain domain) {
                        builder = builder.visit(Advice.to(SynchronizedMethodInterceptor.class).on(ElementMatchers.isSynchronized()));
                        builder = builder
                                .visit(new AsmVisitorWrapper.ForDeclaredMethods()
                                        .method(ElementMatchers.any(), new AsmVisitorWrapper.ForDeclaredMethods.MethodVisitorWrapper() {
                                                    @Override
                                                    public MethodVisitor wrap(TypeDescription instrumentedType, net.bytebuddy.description. method.MethodDescription instrumentedMethod, MethodVisitor methodVisitor, net.bytebuddy.implementation. Implementation.Context implementationContext, net.bytebuddy.pool.TypePool typePool, int writerFlags, int readerFlags) {
                                                        return new SynchronizedBlockTransformer(Opcodes.ASM9, methodVisitor);
                                                    }
                                                })
                                );
                        return builder;
                    }
                })

                .with(new AgentBuilder.Listener() {
                    @Override
                    public void onDiscovery(String typeName, ClassLoader classLoader, JavaModule module, boolean loaded) {
                    }

                    @Override
                    public void onTransformation(TypeDescription typeDescription, ClassLoader classLoader, JavaModule module, boolean loaded, DynamicType dynamicType) {
                        System.out.println("[Hecate] Instrumented:  " + typeDescription. getName());
                    }

                    @Override
                    public void onIgnored(TypeDescription typeDescription, ClassLoader classLoader, JavaModule module, boolean loaded) {
                    }

                    @Override
                    public void onError(String typeName, ClassLoader classLoader, JavaModule module, boolean loaded, Throwable throwable) {
                        System.err.println("[Hecate] Error instrumenting " + typeName);
                        throwable.printStackTrace();
                    }

                    @Override
                    public void onComplete(String typeName, ClassLoader classLoader, JavaModule module, boolean loaded) {
                    }
                })

                .installOn(inst);

        System.out.println("[Hecate] Agent installed successfully");

    }
}