
/**
 * Generated native image application with polytaint partitioner
 */
package polytaint.partitioner;

import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;

import org.graalvm.polyglot.*;
import org.graalvm.polyglot.proxy.*;

public class PolyNativeImage {
    public static Context ctx = Context.create();

    @CEntryPoint(name = "graal_add")
    static int add(IsolateThread thread, int a, int b) {

        return a + b;
    }

    static int multi(int a, int b) {
        return a * b;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("++++++++++++++ PolyNativeImage Main +++++++++++++++");
        System.out.println("Result multi(2,3): " + multi(2, 3));
    }
}
