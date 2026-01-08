const size = 1000;
const array = new Array(size);

array.fill(Math.random());

const rand = () => String(Math.floor(Math.random() * size));


for (var i = 0; i < array.length; i++) {
    array[i] = rand();
}

const N = 10000;

function testArray() {
  var val = rand();
  return array.includes(val);
}

function test(f) {
  let result;
  for (var i = 0; i < N; ++i) {
    result = f();
  }   
}


var start = new Date();
test(testArray);
var stop = new Date();
var diff = stop - start;
console.log(">>>>>>>>>>>> array find end: time = ",diff," ms");


