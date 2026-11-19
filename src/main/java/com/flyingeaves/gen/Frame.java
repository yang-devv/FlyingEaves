package com.flyingeaves.gen;

/** arch.Frame: local building frame, origin (world ox, oz), front = world direction of local +v. */
public final class Frame implements Fr {
    public final Canvas cv;
    public final int ox, oz;
    public final String front;
    final int a, b, c, d;

    public Frame(Canvas cv, int ox, int oz, String front) {
        this.cv = cv;
        this.ox = ox;
        this.oz = oz;
        this.front = front;
        int[] r = Blocks.ROT.get(front);
        a = r[0];
        b = r[1];
        c = r[2];
        d = r[3];
    }

    @Override
    public int wx(int u, int v) {
        return ox + a * u + b * v;
    }

    @Override
    public int wz(int u, int v) {
        return oz + c * u + d * v;
    }

    @Override
    public void put(int u, int y, int v, String s) {
        cv.set(wx(u, v), y, wz(u, v), Blocks.rotateState(s, front));
    }

    @Override
    public String get(int u, int y, int v) {
        return cv.get(wx(u, v), y, wz(u, v));
    }

    public void fill(int u0, int y0, int v0, int u1, int y1, int v1, String s) {
        for (int u = Math.min(u0, u1); u <= Math.max(u0, u1); u++)
            for (int v = Math.min(v0, v1); v <= Math.max(v0, v1); v++)
                for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) put(u, y, v, s);
    }

    public void col(int u, int v, int y0, int y1, String s) {
        cv.run(wx(u, v), wz(u, v), y0, y1, Blocks.rotateState(s, front));
    }

    @Override
    public Frame sub(int du, int dv, String frontLocal) {
        return new Frame(cv, wx(du, dv), wz(du, dv), Blocks.worldDir(front, frontLocal));
    }

    @Override
    public Frame sub(int du, int dv) {
        return sub(du, dv, "south");
    }
}
