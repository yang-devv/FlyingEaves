package com.flyingeaves.gen;

/**
 * What the generator draws into: arch.Frame and the wrappers that stand for one (furniture.Keep, furniture._Lift,
 * parts._Recorder) - put / get in local (u, y, v), a child frame, and local -> world.
 */
public interface Fr {
    void put(int u, int y, int v, String s);

    String get(int u, int y, int v);

    Fr sub(int du, int dv, String frontLocal);

    default Fr sub(int du, int dv) {
        return sub(du, dv, "south");
    }

    /** World x of local (u, v). */
    int wx(int u, int v);

    /** World z of local (u, v). */
    int wz(int u, int v);

    default long wkey(int u, int v) {
        return Canvas.ckey(wx(u, v), wz(u, v));
    }
}
