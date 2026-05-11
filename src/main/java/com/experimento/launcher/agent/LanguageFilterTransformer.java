package com.experimento.launcher.agent;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

public class LanguageFilterTransformer implements ClassFileTransformer {
    
    private static final String LANGUAGE_MANAGER = "net/minecraft/client/resources/LanguageManager";
    private static final String LANGUAGE_HOLDER = "net/minecraft/client/resources/LanguageHolder";
    
    private boolean injected = false;
    
    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classFileBuffer) {
        
        if (injected || (!LANGUAGE_MANAGER.equals(className) && !className.contains("LanguageManager"))) {
            return null;
        }
        
        try {
            ClassReader reader = new ClassReader(classFileBuffer);
            ClassNode classNode = new ClassNode(Opcodes.ASM9);
            reader.accept(classNode, 0);
            
            boolean modified = injectFilterCode(classNode);
            
            if (modified) {
                injected = true;
                ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
                classNode.accept(writer);
                System.out.println("[MeaCore-Agent] ✅ Filtro de idiomas inyectado en " + className);
                return writer.toByteArray();
            }
            
        } catch (Exception e) {
            System.err.println("[MeaCore-Agent] ⚠️ Error: " + e.getMessage());
        }
        
        return null;
    }
    
    private boolean injectFilterCode(ClassNode classNode) {
        for (MethodNode method : classNode.methods) {
            if ("<init>".equals(method.name)) {
                injectAtConstructor(method);
                return true;
            }
            
            if (method.name.contains("reload") || method.name.contains("load")) {
                injectAtMethod(method);
                return true;
            }
        }
        return false;
    }
    
    private void injectAtConstructor(MethodNode method) {
        org.objectweb.asm.tree.InsnList list = method.instructions;
        
        for (int i = 0; i < list.size(); i++) {
            AbstractInsnNode insn = list.get(i);
            if (insn.getOpcode() == Opcodes.RETURN) {
                org.objectweb.asm.tree.InsnList filterCall = new org.objectweb.asm.tree.InsnList();
                
                filterCall.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
                filterCall.add(new org.objectweb.asm.tree.MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    "com/experimento/launcher/agent/LanguageFilter",
                    "applyReflectionFilter",
                    "()V",
                    false
                ));
                
                list.insert(insn, filterCall);
                break;
            }
        }
    }
    
    private void injectAtMethod(MethodNode method) {
        org.objectweb.asm.tree.InsnList list = method.instructions;
        
        for (AbstractInsnNode insn : list) {
            if (insn.getType() == AbstractInsnNode.METHOD_INSN) {
                MethodInsnNode methodInsn = (MethodInsnNode) insn;
                if (methodInsn.name.contains("load") && methodInsn.owner.contains("Language")) {
                    
                    org.objectweb.asm.tree.InsnList filterCall = new org.objectweb.asm.tree.InsnList();
                    filterCall.add(new org.objectweb.asm.tree.MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "com/experimento/launcher/agent/LanguageFilter",
                        "applyReflectionFilter",
                        "()V",
                        false
                    ));
                    
                    list.insert(insn, filterCall);
                    break;
                }
            }
        }
    }
}