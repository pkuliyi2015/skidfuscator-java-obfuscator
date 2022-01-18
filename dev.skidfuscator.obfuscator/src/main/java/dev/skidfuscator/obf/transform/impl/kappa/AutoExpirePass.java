package dev.skidfuscator.obf.transform.impl.kappa;

import dev.skidfuscator.obf.init.SkidSession;
import dev.skidfuscator.obf.transform.impl.ProjectPass;
import dev.skidfuscator.obf.utils.RandomUtil;
import lombok.SneakyThrows;
import org.mapleir.asm.MethodNode;
import org.mapleir.ir.algorithms.BoissinotDestructor;
import org.mapleir.ir.algorithms.LocalsReallocator;
import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.expr.invoke.StaticInvocationExpr;
import org.mapleir.ir.code.stmt.PopStmt;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class AutoExpirePass implements ProjectPass {

    public void expire() {

    }

    @SneakyThrows
    private MethodNode getMethodNode(final org.mapleir.asm.ClassNode extra) {
        final ClassReader classReader = new ClassReader(AutoExpirePass.class.getResourceAsStream("/" + this.getClass().getName().replace(".", "/") + ".class"));
        final org.objectweb.asm.tree.ClassNode classNode = new ClassNode();
        classReader.accept(classNode, 0);

        final Method method = AutoExpirePass.class.getDeclaredMethod("expire");

        final org.objectweb.asm.tree.MethodNode methodNode = classNode.methods.stream()
                .filter(e -> e.name.equals(method.getName()))
                .filter(e -> e.desc.equals("()V"))
                .findFirst()
                .orElse(null);



        final org.objectweb.asm.tree.MethodNode copied =  new org.objectweb.asm.tree.MethodNode(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_DEPRECATED | Opcodes.ACC_STATIC,
                "skid2",
                "()V",
                "",
                null
        );

        copied.instructions.add(methodNode.instructions);
        final MethodNode methodNode1 = new MethodNode(
                copied,
                extra
        );

        extra.node.methods.add(copied);
        extra.getMethods().add(methodNode1);

        return methodNode1;
    }


    @Override
    public void pass(SkidSession session) {
        final List<MethodNode> methodNodeList = new ArrayList<>(session.getCxt().getApplicationContext().getEntryPoints());

        final List<MethodNode> randomSelect = methodNodeList.stream()
                .filter(e -> !e.owner.isEnum())
                .filter(e -> e.owner.node.outerClass == null && !e.owner.getName().contains("$"))
                .collect(Collectors.toList());
        final MethodNode random = randomSelect.get(RandomUtil.nextInt(randomSelect.size()));

        final MethodNode methodNode = getMethodNode(random.owner);
        final ControlFlowGraph vcfg = session.getCxt().getIRCache().getFor(methodNode);
        BoissinotDestructor.leaveSSA(vcfg);
        LocalsReallocator.realloc(vcfg);

        for (MethodNode node : methodNodeList) {
            if (node.isAbstract() || node.isNative()) {
                continue;
            }

            final ControlFlowGraph cfg = session.getCxt().getIRCache().get(node);

            if (cfg == null) {
                continue;
            }

            final BasicBlock entry = cfg.getEntries().iterator().next();
            final StaticInvocationExpr staticInvocationExpr = new StaticInvocationExpr(
                    new Expr[0],
                    methodNode.owner.getName(),
                    methodNode.getName(),
                    methodNode.getDesc()
            );

            final PopStmt stmt = new PopStmt(staticInvocationExpr);

            entry.add(0, stmt);
        }
    }

    @Override
    public String getName() {
        return "AutoExpire Pass";
    }
}
