function sayHello() {
    console.log('++++++++++++ Hello from javascript file +++++++++++');
}

function add(a, b) {
    console.log("adding two numbers");
    return a + b;
}

function funcA() {
    var sec_int_a = Polyglot.eval("secL", "sDouble(125.45)");
    var resC = 123 + 456 * 789 / 111 - sec_int_a;
    var resN = funcN(sec_int_a, 2);
    var retd = funcD(resC);
    var a = 4;

    for (let i = 0; i < 5; i++) {
        a = a + i;
        for (let j = 0; j < 2; j++) {
            resN = resC + a;
        }
    }

    sayHello();

    console.log('resN in funcA is : ' + resN);
    return resC;
}

function funcD(paramD) {

    var res = funcN(paramD, 2);
    console.log('funcD res from N: ' + res);
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
    console.log('poly2_secInt: ' + poly2_secInt);
}

function poly_3() {

    var poly3_secInt = Polyglot.eval("secL", "sInt(333)");
    console.log('poly3_secInt: ' + poly3_secInt);
}


function sum_1(n) {
    return n;
}


function sum_2(n, m) {
    return n + m;
}


function sum_6(a, b, c, d, e, f) {
    var poly3_secInt = Polyglot.eval("secL", "sInt(333)");
    console.log("=======>>> in sum_6 ------>>>");
    s2 = sum_2(a, b) + sum_2(c, d) + sum_2(e, f);
    return s2;
}

sayHello();
var retd = funcD(5.0);
var reta = funcA();
var poly = poly_2();
console.log(">>>>>>>> at the end of javascript file >>>>>>");


