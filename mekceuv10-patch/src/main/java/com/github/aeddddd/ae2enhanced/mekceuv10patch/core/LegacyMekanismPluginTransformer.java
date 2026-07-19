package com.github.aeddddd.ae2enhanced.mekceuv10patch.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class LegacyMekanismPluginTransformer implements IClassTransformer, Opcodes {

    private static final String TARGET = "com.github.aeddddd.ae2enhanced.mixin.MekanismMixinPlugin";
    private static final String ON_LOAD_DESCRIPTOR = "(Ljava/lang/String;)V";
    private static final String GUARD = "com/github/aeddddd/ae2enhanced/mekceuv10patch/compat/LegacyMekanismPluginGuard";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || (!TARGET.equals(name) && !TARGET.equals(transformedName))) {
            return basicClass;
        }

        ClassNode targetClass = new ClassNode();
        new ClassReader(basicClass).accept(targetClass, 0);
        String loadedField = findLoadedField(targetClass);
        if (loadedField == null || !replaceOnLoad(targetClass, loadedField)) {
            return basicClass;
        }

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        targetClass.accept(writer);
        return writer.toByteArray();
    }

    private static String findLoadedField(ClassNode targetClass) {
        for (FieldNode field : targetClass.fields) {
            if ("Z".equals(field.desc) && ("mekanismLoaded".equals(field.name)
                    || "legacyMekanismLoaded".equals(field.name))) {
                return field.name;
            }
        }
        return null;
    }

    private static boolean replaceOnLoad(ClassNode targetClass, String loadedField) {
        for (MethodNode method : targetClass.methods) {
            if (!"onLoad".equals(method.name) || !ON_LOAD_DESCRIPTOR.equals(method.desc)) {
                continue;
            }

            method.instructions.clear();
            method.tryCatchBlocks.clear();
            method.localVariables = null;
            method.instructions.add(new VarInsnNode(ALOAD, 0));
            method.instructions.add(new MethodInsnNode(INVOKESTATIC, GUARD, "isLegacyMekanism", "()Z", false));
            method.instructions.add(new FieldInsnNode(PUTFIELD, targetClass.name, loadedField, "Z"));
            method.instructions.add(new InsnNode(RETURN));
            method.maxStack = 2;
            method.maxLocals = 2;
            return true;
        }
        return false;
    }
}
