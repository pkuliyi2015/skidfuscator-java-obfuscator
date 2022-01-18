package dev.skidfuscator.obf.transform.impl.flow.gen3;

import dev.skidfuscator.obf.init.SkidSession;
import dev.skidfuscator.obf.maple.FakeConditionalJumpStmt;
import dev.skidfuscator.obf.skidasm.SkidGraph;
import dev.skidfuscator.obf.skidasm.SkidMethod;
import dev.skidfuscator.obf.transform.Transformer;
import dev.skidfuscator.obf.transform.impl.flow.FlowPass;
import dev.skidfuscator.obf.transform.type.SkidGraphTransformer;
import dev.skidfuscator.obf.utils.Blocks;
import org.mapleir.flowgraph.edges.ConditionalJumpEdge;
import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.expr.ConstantExpr;
import org.mapleir.ir.code.expr.VarExpr;
import org.mapleir.ir.code.stmt.ConditionalJumpStmt;
import org.objectweb.asm.Type;

import java.util.HashSet;

/**
 * Simple fake jump flow pass.
 *
 * >> Scoring (/10, higher is better):
 * [*] Decompiler: 3
 * [*] Weight: 8
 * [*] Difficult: 5 (with seed), 1 (without seed)
 * [*] Efficiency: 6
 * [=] Total: 5.5
 *
 * >> Complexity (n = number of blocks)
 * C = O(n)
 *
 * >> Space complexity (n = number of blocks)
 * C = O(1)
 */

@Transformer("Fake Exception [Simple]")
public class FakeExceptionFlowPass implements SkidGraphTransformer {

    @Override
    public void run(SkidSession skidSession, SkidGraph graph) {
        final ControlFlowGraph cfg = skidSession.getCxt().getIRCache().get(graph.getNode());

        if (cfg == null) {
            return;
        }

        for (BasicBlock entry : new HashSet<>(cfg.vertices())) {
            if (entry.size() == 0) {
                continue;
            }

            // Todo add hashing to amplify difficulty and remove key exposure
            // Todo make this a better system
            final Expr var_load = new VarExpr(graph.getLocal(), Type.INT_TYPE);
            final ConstantExpr var_const = new ConstantExpr(graph.getBlock(entry).getSeed());

            // Todo add more boilerplates + add exception rotation
            final BasicBlock fuckup = Blocks.exception(cfg, "ay");

            // Todo change blocks to be skiddedblocks to add method to directly add these
            final FakeConditionalJumpStmt jump_stmt = new FakeConditionalJumpStmt(var_load, var_const, fuckup, ConditionalJumpStmt.ComparisonType.NE);
            final ConditionalJumpEdge<BasicBlock> jump_edge = new ConditionalJumpEdge<>(entry, fuckup, jump_stmt.getOpcode());
            entry.add(jump_stmt);
            cfg.addEdge(jump_edge);

            skidSession.count();

            // Regular debug
                /*
                final Local local1 = entry.cfg.getLocals().get(entry.cfg.getLocals().getMaxLocals() + 2);
                entry.add(new CopyVarStmt(new VarExpr(local1, Type.getType(String.class)),
                        new ConstantExpr(entry.getDisplayName() +" : var expect: " + var_const.getConstant())));
                */
        }
    }
}
