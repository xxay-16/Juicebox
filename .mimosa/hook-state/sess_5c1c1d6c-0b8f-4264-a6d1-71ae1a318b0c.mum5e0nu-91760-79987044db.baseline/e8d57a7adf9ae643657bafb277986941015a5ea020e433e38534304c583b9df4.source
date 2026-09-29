package juicebox;

/**
 * Snapshot of the mutable runtime configuration that lives in HiCGlobals.
 * Lets callers (notably assembly-mode enter/exit) save and restore the whole
 * mutable group atomically instead of touching each static field by hand, which
 * is the first step toward replacing the loose statics with an injected config.
 *
 * This is an additive accessor: the static fields on HiCGlobals remain the
 * backing store so the many existing direct references keep working unchanged.
 */
public final class HiCConfig {

    public double hicMapScale;
    public boolean useCache;
    public boolean allowDynamicBlockIndex;
    public boolean printVerboseComments;

    private HiCConfig(double hicMapScale, boolean useCache,
                      boolean allowDynamicBlockIndex, boolean printVerboseComments) {
        this.hicMapScale = hicMapScale;
        this.useCache = useCache;
        this.allowDynamicBlockIndex = allowDynamicBlockIndex;
        this.printVerboseComments = printVerboseComments;
    }

    /** Capture the current mutable configuration from HiCGlobals. */
    public static HiCConfig snapshot() {
        return new HiCConfig(
                HiCGlobals.hicMapScale,
                HiCGlobals.useCache,
                HiCGlobals.allowDynamicBlockIndex,
                HiCGlobals.printVerboseComments);
    }

    /** Write this configuration back to HiCGlobals. */
    public void apply() {
        HiCGlobals.hicMapScale = this.hicMapScale;
        HiCGlobals.useCache = this.useCache;
        HiCGlobals.allowDynamicBlockIndex = this.allowDynamicBlockIndex;
        HiCGlobals.printVerboseComments = this.printVerboseComments;
    }

    /** Convenience: snapshot now, run action, restore the snapshot. */
    public static void withPreserved(Runnable action) {
        HiCConfig saved = snapshot();
        try {
            action.run();
        } finally {
            saved.apply();
        }
    }
}
