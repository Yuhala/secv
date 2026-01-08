const size = 100;
const arr = (new Array(size)).join(' ').split(' ')
	.map((a, i) => `${i}`);

const picked = arr.filter(() => Math.random() > 0.5);

const set = new Set(picked);


const rand = () => String(Math.floor(Math.random() * size));

const N = 10000;


function testSet() {
  var val = rand();
  return set.has(val);
}

function test(f) {
  let result;
  for (var i = 0; i < N; ++i) {
    result = f();
  } 
  
}

console.log("test set start");

test(testSet);


console.log("test set end");
