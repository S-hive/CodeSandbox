public class ExecTest {
    public static void main(String[] a) throws Exception {
        Process p = Runtime.getRuntime().exec(""" + java -Djava.security.manager=allow -Dfile.encoding=UTF-8 -Dsandbox.dir=D:\Liyalin\javafail\code-sandbox\tmpCode\e2e-test -cp "D:\Liyalin\javafail\code-sandbox\tmpCode\e2e-test;D:\Liyalin\javafail\code-sandbox\target\classes" com.security.SandboxRunner 1 2.Replace('"','\"') + """);
        java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()));
        String line; while ((line = r.readLine()) != null) System.out.println("OUT:" + line);
        java.io.BufferedReader e = new java.io.BufferedReader(new java.io.InputStreamReader(p.getErrorStream()));
        while ((line = e.readLine()) != null) System.out.println("ERR:" + line);
        System.out.println("EXIT:" + p.waitFor());
    }
}
