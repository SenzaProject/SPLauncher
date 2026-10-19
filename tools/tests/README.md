# Uji logika murni (tanpa perangkat)

Menguji pengepakan grid (`GridPacker`) dan model susunan (`LayoutStore`: normalize, addWidget, resize).
Jalankan dari root proyek setelah `./build.sh` (butuh `build/classes`):

    TC=${SP_TOOLCHAIN:-$HOME/sp-toolchain}
    mkdir -p /tmp/spt && javac -nowarn -cp $TC/android.jar:build/classes -d /tmp/spt tools/tests/*Test.java
    java -cp /tmp/spt:$TC/android.jar:build/classes com.senzaproject.splauncher.GridPackerTest
    java -cp /tmp/spt:$TC/android.jar:build/classes com.senzaproject.splauncher.LayoutStoreTest

(`LayoutStoreTest` memakai `sun.misc.Unsafe` untuk membuat `LayoutStore` tanpa Context Android.)
