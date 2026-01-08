const size = 1000;
const arr = (new Array(size)).join(' ').split(' ')
	.map((a, i) => `${i}`);

const picked = arr.filter(() => Math.random() > 0.5);

const map = new Map([...picked.map(v => [v, v])]);

const rand = () => String(Math.floor(Math.random() * size));

const N = 10000;

function testMap() {
  var val = rand();
  return map.has(val);
}


function test(f) {
  let result;
  for (var i = 0; i < N; ++i) {    
    result = f();
  }   
}

var start = new Date();
test(testMap);
var stop = new Date();
var diff = stop - start;
console.log(">>>>>>>>>>>> map find end: time = ",diff," ms");




