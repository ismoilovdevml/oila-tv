/**
 * Tiny command-line bridge to the TV platform's factory API, run with app_process as root.
 *   Fapi get <key>...      print boot-loader environment values (e.g. db_table, _BootlogoFile)
 *   Fapi logo <jpg>        set the boot-loader logo the same way the TV's own "set logo" menu does;
 *                          the jpg must be readable by the platform service (use /vendor/tvconfig/)
 * The classes come from the TV player APK on the device, so nothing vendor-specific is bundled here.
 */
public class Fapi {
    public static void main(String[] a) throws Exception {
        Class<?> c = Class.forName("com.mediatek.twoworlds.factory.MtkTvFApiSystem");
        Object o = c.getMethod("getInstance").invoke(null);
        if (a.length >= 2 && a[0].equals("get")) {
            for (int i = 1; i < a.length; i++)
                System.out.println(a[i] + "=" + c.getMethod("getEmmcEnvVar", String.class).invoke(o, a[i]));
        } else if (a.length == 2 && a[0].equals("logo")) {
            System.out.println("changeBootlogo ret=" + c.getMethod("changeBootlogo", String.class).invoke(o, a[1]));
        } else {
            System.err.println("usage: Fapi get <key>... | Fapi logo <jpg>");
            System.exit(2);
        }
    }
}
