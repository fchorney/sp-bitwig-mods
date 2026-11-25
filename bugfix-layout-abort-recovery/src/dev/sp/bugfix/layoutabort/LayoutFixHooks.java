package dev.sp.bugfix.layoutabort;

import bitwig.obf.Utt; // a Bitwig logger
import bitwig.obf.dpN; // Bitwig's application base class; CDi() is the layout loop
import bitwig.obf.dve; // a widget; the ones in the application's eLY list are dirty layout roots
import bml.api.At;
import bml.api.Bml;
import bml.api.Hook;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Bitwig's layout loop (dpN.CDi) gives up after 1000 passes, clears its list of dirty
 * layout roots and leaves them invalid. A root is only queued again when it is invalidated
 * while valid, so those roots are never laid out or painted again: the window freezes (seen
 * on Mix views with ~200+ strips). This mod hooks the abort's log call, copies the list before
 * Bitwig clears it, and queues the roots again on the next event, which gives the layout
 * another 1000 passes. Retries are capped per episode so a layout that never settles only
 * costs a few rounds and then behaves as stock Bitwig.
 * All of this runs on Bitwig's event thread (CDi asserts it).
 */
public final class LayoutFixHooks {
    private LayoutFixHooks() {}

    static final Bml.Mod MOD = Bml.mod("bugfix-layout-abort-recovery");
    static final int MAX_ROUNDS = 20;          // retries per episode
    static final long EPISODE_GAP_NS = 5_000_000_000L; // an abort this long after the last one starts a new episode

    private static int rounds;
    private static long episodeStart, lastAbort;
    private static boolean abortedThisCall;
    private static List<dve> pending = List.of(); // roots we put back, checked when a layout ends

    @Hook(id = "layout-abort-recovery-enter", target = dpN.class, method = "CDi()V", at = At.ENTRY)
    public static void layoutStarts() {
        abortedThisCall = false;
    }

    /** Replaces the logger call that writes "Maximum layout iteration count reached ..." inside dpN.CDi (its only Utt.IwV call). */
    @Hook(id = "layout-abort-recovery-abort", target = dpN.class, method = "CDi()V", at = At.REPLACE_CALL,
          callOwner = Utt.class, call = "IwV(Ljava/lang/Object;)V", occurrence = 1)
    public static void layoutAborted(Utt log, Object message) {
        log.IwV(message); // Bitwig's own error line, unchanged
        try {
            abortedThisCall = true;
            long now = System.nanoTime();
            if (rounds == 0 || now - lastAbort > EPISODE_GAP_NS) { rounds = 0; episodeStart = now; }
            lastAbort = now;
            rounds++;
            dpN app = dpN.TVZ();
            List<dve> roots = new ArrayList<>();
            for (Object o : app.eLY) roots.add((dve) o); // Bitwig clears eLY right after this call
            MOD.log("layout aborted after 1000 passes, round " + rounds + ", " + roots.size() + " roots: " + classes(roots)
                    + ", " + ms(now - episodeStart) + " ms into the episode");
            if (rounds > MAX_ROUNDS) {
                MOD.log("giving up after " + MAX_ROUNDS + " rounds: the layout does not settle; leaving it as Bitwig does");
                pending = List.of();
                return;
            }
            pending = roots;
            app.asyncExec(() -> requeue(roots));
        } catch (Throwable t) {
            MOD.log("could not handle the layout abort", t);
        }
    }

    private static void requeue(List<dve> roots) {
        try {
            dpN app = dpN.TVZ();
            int n = 0;
            for (dve r : roots) {
                if (!r.tA() && r.ZTl() && r.kI_()) { app.IwV(r); n++; } // IwV(dve): Bitwig's own "add dirty root"
            }
            MOD.log("queued " + n + " of " + roots.size() + " roots again");
        } catch (Throwable t) {
            MOD.log("could not queue the layout roots again", t);
        }
    }

    @Hook(id = "layout-abort-recovery-exit", target = dpN.class, method = "CDi()V", at = At.BEFORE_RETURN, allReturns = true)
    public static void layoutEnds() {
        try {
            if (abortedThisCall || pending.isEmpty()) return;
            for (dve r : pending) if (!r.tA()) return; // still being laid out
            MOD.log("layout recovered after " + rounds + " aborted round(s), " + ms(System.nanoTime() - episodeStart) + " ms");
            pending = List.of();
            rounds = 0;
        } catch (Throwable t) {
            MOD.log("layout end check failed", t);
            pending = List.of();
        }
    }

    private static String classes(List<dve> roots) {
        Map<String, Integer> m = new TreeMap<>();
        for (dve r : roots) m.merge(r.getClass().getName(), 1, Integer::sum);
        return m.toString();
    }

    private static long ms(long ns) {
        return ns / 1_000_000L;
    }
}
