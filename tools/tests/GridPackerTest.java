package com.senzaproject.splauncher;
public class GridPackerTest {
    static void eq(int[] a, int[] b) { if (!java.util.Arrays.equals(a,b)) throw new AssertionError(java.util.Arrays.toString(a)+" != "+java.util.Arrays.toString(b)); }
    public static void main(String[] x) {
        // 4x6: semua ikon 1x1 -> slot berurutan
        eq(GridPacker.pack(4,6,new int[]{1,1,1,1,1},new int[]{1,1,1,1,1}), new int[]{0,1,2,3,4});
        // widget 4x2 di awal, lalu ikon di baris ke-3
        eq(GridPacker.pack(4,6,new int[]{4,1,1},new int[]{2,1,1}), new int[]{0,8,9});
        // 3 ikon lalu widget 2x2: widget tidak muat di baris 0 (hanya kolom 3 bebas) -> baris 1 kol 0 ; ikon berikutnya mengisi sel 3
        eq(GridPacker.pack(4,6,new int[]{1,1,1,2,1},new int[]{1,1,1,2,1}), new int[]{0,1,2,4,3});
        // widget lebih besar dari halaman di-clamp
        eq(GridPacker.pack(4,2,new int[]{9},new int[]{9}), new int[]{0});
        // tidak muat -> -1
        eq(GridPacker.pack(2,2,new int[]{2,1},new int[]{2,1}), new int[]{0,-1});
        if (!GridPacker.fits(4,6,new int[]{2,2,1},new int[]{2,2,1})) throw new AssertionError();
        if (GridPacker.fits(2,2,new int[]{2,1},new int[]{2,1})) throw new AssertionError();
        System.out.println("GridPacker OK");
    }
}
