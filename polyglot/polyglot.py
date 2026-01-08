
import polyglot 



def helloPy():
   print('++++++++++++ Hello graal.python +++++++++++');



def funcA(num):
    sec_int_a = polyglot.eval(language="secL", string="sInt(10)");
    resC = 123 + 456 * 789 / 111 - sec_int_a;
    indirect_sec_int = 2 + funcN(5, 2);
    dummy = 123;
    print("dummy variable in funcA is:",dummy);
    return indirect_sec_int;


def funcN(param, n):
    print("funcN param1 is: ",param)
    return param * n;


helloPy();
funcA(5);