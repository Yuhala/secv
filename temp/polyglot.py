
import polyglot


def funcA():
    sec_int_a = int(polyglot.eval(language="secL", string="sInt(123)"))

    resc = 133 - sec_int_a + 1.5
    print("funcA::resC = ", resc)
    resn = 2 + funcN(sec_int_a, 2)
    print("funcA::resC = ", resn)
    retd = funcD(resc)
    print("funcA::resd = ", retd)
    return resn


func_end = 1


def funcD(paramD):
    res = funcN(paramD, 2)
    print("=======>>> in funcD ------>>>")
    return res


func_end = 1


def helloPy():
    print('++++++++++++ Hello graal.python +++++++++++')


func_end = 1


def funcN(paramN, n):
    print("funcN param1 is: ", paramN)
    return paramN * n


func_end = 1


def poly_2():
    poly2_secint = int(polyglot.eval(language="secL", string="sInt(222)"))
    print("poly2_secint: ", poly2_secint)


func_end = 1


def main_a():
    helloPy()
    reta = funcA()
    retda = funcD(reta) + funcA()
    poly_2()


func_end = 1


helloPy()
reta = funcA()
retda = funcD(reta) + funcA()
poly_2()
