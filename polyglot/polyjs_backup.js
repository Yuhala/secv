

function sayHello() {
    console.log('++++++++++++ Hello from javascript file +++++++++++');
}


function funcA(paramA) {
    var sec_int_a = Polyglot.eval("secureL", "function main() {return 10;}");
    var resC = 123 + 456 * 789 / 111 - sec_int_a;

    var indirect_sec_int = 2 + funcN(sec_int_a, 2);

}


function funcB() {

    var sec_int = Polyglot.eval("secL", "sInt(60)");

    console.log('funcB secL int is: ' + sec_int);
}

function funcD(paramD) {

    console.log('funcD: ' + paramD);

}

function funcN(param, n) {
    console.log('funcN: ' + param);
    return param * n;
}

function funcM(param, m) {
    console.log('funcM: ' + param);
    return param / m;
}



function poly_1() {
    var poly1_sec_double = Polyglot.eval("secL", "sDouble(15.5)");
    console.log('poly1_secDouble: ' + poly1_sec_double);

}

function poly_2() {
    var poly2_secInt = Polyglot.eval("secureL", "function main() {return 222;}");
    console.log('poly1_secInt: ' + poly2_secInt);
}

function poly_3() {
    var poly3_secInt = Polyglot.eval("secureL", "function main() {return 333;}");
    console.log('poly3_secInt: ' + poly3_secInt);
}

function funcC(a, b, c) {

    console.log('sum of params: ' + a + b + c);
}


function func_1(val) {
    var res = val * 10;
    console.log('func_1 result: ' + res);
}


function func_2(val) {
    var res = val * 10;
    console.log('func_2 result: ' + res);
}


function func_3(val) {
    var res = val * 10;
    console.log('func_3 result: ' + res);
}

sayHello();
funcB();

