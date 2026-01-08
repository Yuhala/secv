const size = 100;
const arr = (new Array(size)).join(' ').split(' ')
	.map((a, i) => `${i}`);

const picked = arr.filter(() => Math.random() > 0.5);

const set = new Set(picked);
const map = new Map([...picked.map(v => [v, v])]);

const rand = () => String(Math.floor(Math.random() * size));

const N = 100;

function testMap() {
  var val = rand();
  return map.has(val);
}
function testSet() {
  var val = rand();
  return set.has(val);
}

function test(f) {
  let result;
  for (var i = 0; i < N; ++i) {
    result = f();
  } 
  return result;
}

console.log("test start");

test(testSet);
test(testMap);

console.log("test end");
