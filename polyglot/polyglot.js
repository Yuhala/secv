

function sayHello() {
    console.log('++++++++++++ Hello from javascript file +++++++++++');
}

function add(a, b) {
    return a + b;
}


function funcA(paramA) {
    var sec_int_a = Polyglot.eval("secL", "sInt(10)");
    var resC = 123 + 456 * 789 / 111 - sec_int_a;
    var indirect_sec_int = 2 + funcN(5, 2);
    dummy = 123;
    console.log('dummy variable in funcA is : ' + dummy);
    return indirect_sec_int;
}

function funcD(paramD) {

    var res = funcN(paramD, 2);
    console.log('funcD res from A: ' + res);
    return res;
}

function funcN(param, n) {
    console.log('funcN: ' + param);
    return param * n;
}

function funcM(param, m) {
    console.log('funcM: ' + param);
    return param / m;
}

function poly_2() {

    var poly2_secInt = Polyglot.eval("secL", "sInt(222)");
    console.log('poly1_secInt: ' + poly2_secInt);
}

function poly_3() {

    var poly3_secInt = Polyglot.eval("secL", "sInt(333)");
    console.log('poly3_secInt: ' + poly3_secInt);
}

var reta = funcA(6);
sayHello();
var retd = funcD(reta) + funcA(2);
poly_2();


