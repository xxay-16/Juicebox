package juicebox.core.assembly;

/**
 * UI-free snapshot of the scaffold state needed by the assembly coordinate
 * transform. Decouples the transform algorithm from the desktop Scaffold class
 * (which pulls in AWT colors and feature metadata).
 */
public final class ScaffoldData {
    public final long originalStart;
    public final long originalEnd;
    public final long currentStart;
    public final long currentEnd;
    public final long length;
    public final boolean invertedVsInitial;

    public ScaffoldData(long originalStart, long originalEnd, long currentStart, long currentEnd,
                        long length, boolean invertedVsInitial) {
        this.originalStart = originalStart;
        this.originalEnd = originalEnd;
        this.currentStart = currentStart;
        this.currentEnd = currentEnd;
        this.length = length;
        this.invertedVsInitial = invertedVsInitial;
    }
}
