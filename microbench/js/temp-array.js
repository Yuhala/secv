
if (typeof console === 'undefined') console = {log:print};

const inputs = new Array(16);
for (let i = 0; i < inputs.length; ++i) inputs[i] = i + 0.1;

const TESTS = [];

(function() {
  TESTS.push(
      function find(a) {
        return a.find(x => x < 0);
      },
      function findIndex(a) {
        return a.findIndex(x => x < 0);
      }
  );
})();

const N = 1e7;

function test(fn) {
  var result;
  for (var i = 0; i < N; ++i) result = fn(inputs);
  return result;
}
test(x => x);

for (var j = 0; j < TESTS.length; ++j) {
  test(TESTS[j]);
}

for (var j = 0; j < TESTS.length; ++j) {
  var startTime = Date.now();
  test(TESTS[j]);
  console.log(TESTS[j].name + ':', (Date.now() - startTime), 'ms.');
}