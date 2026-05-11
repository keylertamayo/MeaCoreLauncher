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
import java.util.Iterator;

public class LanguageManagerTransformer implements ClassFileTransformer {
    
    private static final String LANGUAGE_MANAGER = "net/minecraft/client/resources/LanguageManager";
    private static final String FILTER_METHOD = "filterAllowedLanguages";
    
    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classFileBuffer) {
        
        if (!LANGUAGE_MANAGER.equals(className)) {
            return null;
        }
        
        try {
            ClassReader reader = new ClassReader(classFileBuffer);
            ClassNode classNode = new ClassNode(Opcodes.ASM9);
            
            reader.accept(classNode, 0);
            
            boolean modified = addFilterCall(classNode);
            
            if (modified) {
                ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
                classNode.accept(writer);
                System.out.println("[MeaCore-Agent] ✅ LanguageManager inyectado - idiomas filtrados");
                return writer.toByteArray();
            }
            
        } catch (Exception e) {
            System.err.println("[MeaCore-Agent] ⚠️ Error transformando LanguageManager: " + e.getMessage());
        }
        
        return null;
    }
    
    private boolean addFilterCall(ClassNode classNode) {
        boolean modified = false;
        
        for (MethodNode method : classNode.methods) {
            String methodName = method.name;
            String methodDesc = method.desc;
            
            if ("onResourceManagerReload".equals(methodName) || 
                "reload".equals(methodName) ||
                methodName.contains("reload")) {
                
                Iterator<AbstractInsnNode> it = method.instructions.iterator();
                while (it.hasNext()) {
                    AbstractInsnNode insn = it.next();
                    
                    if (insn.getType() == AbstractInsnNode.METHOD_INSN) {
                        MethodInsnNode methodInsn = (MethodInsnNode) insn;
                        
                        if ("loadLanguages".equals(methodInsn.name) || 
                            "load".equals(methodInsn.name)) {
                            
                            method.instructions.insert(methodInsn, createFilterCode());
                            modified = true;
                            break;
                        }
                    }
                }
            }
        }
        
        if (!modified) {
            for (MethodNode method : classNode.methods) {
                if ("<init>".equals(method.name)) {
                    method.instructions.insert(createFilterCode());
                    modified = true;
                    break;
                }
            }
        }
        
        return modified;
    }
    
    private AbstractInsnNode[] createFilterCode() {
        org.objectweb.asm.tree.InsnList list = new org.objectweb.asm.tree.InsnList();
        
        list.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
        list.add(new org.objectweb.asm.tree.MethodInsnNode(
            Opcodes.INVOKESTATIC,
            "com/experimento/launcher/agent/LanguageFilter",
            "filterRegistry",
            "(Ljava/util/Map;)Ljava/util/Map;",
            false
        ));
        
        return list.toArray();
    }
}