package com.gathin.tvhome;

/** Physical left/right boundaries for a grid whose first column follows its reading direction. */
final class GridNavigation {
    private GridNavigation() { }

    static boolean atHorizontalEdge(int position, int count, int columns, boolean rtl, boolean left) {
        boolean towardStart = left != rtl;
        if (towardStart) return position % columns == 0;
        return position % columns == columns - 1 || position == count - 1;
    }

    /**
     * The next tile for Left or Right, or -1 at a row end. One step along the row, never a wrap and never
     * a jump: a zoomed tile's bounds make the focus finder skip the neighbour, or find none at all.
     */
    static int horizontalStep(int position, int count, int columns, boolean rtl, boolean left) {
        if (columns <= 0 || count <= 0) return -1;
        if (atHorizontalEdge(position, count, columns, rtl, left)) return -1;
        boolean towardStart = left != rtl;
        int next = position + (towardStart ? -1 : 1);
        return next < 0 || next >= count ? -1 : next;
    }
}
