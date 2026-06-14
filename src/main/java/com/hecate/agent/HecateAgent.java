package com.hecate.agent;

import com.hecate.events.EventCollector;
import com.hecate.util.EventExporter;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;

import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

public class HecateAgent {

    public static void premain(String agentArgs, Instrumentation inst) {

        System.setProperty("net.bytebuddy.experimental", "true");
        System.out.println("[Hecate] Agent started");
        System.out.println("[Hecate] Runtime concurrency analysis active");

        EventCollector.getInstance().startCollecting();

        // Raw-ASM transformer for synchronized blocks and j.u.c Lock calls — reaches lambda
        // bodies that ByteBuddy's method wrapper skips.
        inst.addTransformer(new LockClassFileTransformer(), true);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            EventCollector.getInstance().stopCollecting();
            System.out.println("[Hecate] Captured " + EventCollector.getInstance().getEventCount() + " events");
            EventExporter.exportToFile(EventCollector.getInstance().getEvents(), "hecate-events.json");
            System.out.println("[Hecate] Events exported to >> hecate-events.json <<");
        }));

        new AgentBuilder.Default()

                .ignore(ElementMatchers.nameStartsWith("java.")
                        .or(ElementMatchers.nameStartsWith("javax."))
                        .or(ElementMatchers.nameStartsWith("sun."))
                        .or(ElementMatchers.nameStartsWith("com.sun."))
                        .or(ElementMatchers.nameStartsWith("jdk."))
                        .or(ElementMatchers.nameStartsWith("net.bytebuddy."))
                        .or(ElementMatchers.nameStartsWith("com.hecate.agent."))
                        .or(ElementMatchers.nameStartsWith("com.hecate.events."))
                        .or(ElementMatchers.nameStartsWith("com.hecate.util.")))

                .type(ElementMatchers.any())

                // ByteBuddy handles synchronized *methods* (ACC_SYNCHRONIZED has no opcodes to
                // rewrite, so advice wraps method entry/exit). Synchronized *blocks* and explicit
                // Lock calls are handled by LockClassFileTransformer below, which — unlike
                // ByteBuddy's method wrapper — also reaches lambda bodies and constructors.
                .transform(new AgentBuilder.Transformer() {
                    @Override
                    public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder, TypeDescription typeDescription, ClassLoader classLoader, JavaModule module, ProtectionDomain domain) {
                        return builder.visit(Advice.to(SynchronizedMethodInterceptor.class).on(ElementMatchers.isSynchronized()));
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